package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.BlockTree.BlockTreeEntry;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustumCulling;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.concurrent.PriorityExecutorService;
import org.janelia.saalfeldlab.util.grids.Grids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import bdv.util.Affine3DHelpers;
import eu.mihosoft.jcsg.ext.openjfx.shape3d.PolygonMeshView;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;
import net.imglib2.util.ValuePair;
import net.imglib2.util.ValueTriple;

public class MeshGeneratorJobManager<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final class SceneUpdateJobParameters
	{
		final int preferredScaleIndex;
		final int highestScaleIndex;
		final int simplificationIterations;
		final double smoothingLambda;
		final int smoothingIterations;
		final ViewFrustum viewFrustum;
		final AffineTransform3D eyeToWorldTransform;

		SceneUpdateJobParameters(
			final int preferredScaleIndex,
			final int highestScaleIndex,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final ViewFrustum viewFrustum,
			final AffineTransform3D eyeToWorldTransform)
		{
			this.preferredScaleIndex = preferredScaleIndex;
			this.highestScaleIndex = highestScaleIndex;
			this.simplificationIterations = simplificationIterations;
			this.smoothingLambda = smoothingLambda;
			this.smoothingIterations = smoothingIterations;
			this.viewFrustum = viewFrustum;
			this.eyeToWorldTransform = eyeToWorldTransform;
		}
	}

	private static class Task
	{
		final Runnable task;
		final int priority;

		Future<?> future;

		Task(final Runnable task, final int priority)
		{
			this.task = task;
			this.priority = priority;
		}
	}

	private final DataSource<?, ?> source;

	private final T identifier;

	private final Map<ShapeKey<T>, Task> tasks = new HashMap<>();

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks;

	private final InterruptibleFunction<T, Interval[]>[] getBlockLists;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes;

	private final ExecutorService manager;

	private final PriorityExecutorService<Integer> workers;

	private final IntegerProperty numPendingTasks;

	private final IntegerProperty numCompletedTasks;

	private final int rendererBlockSize;

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	private final RenderListFilter renderListFilter = new RenderListFilter();

	private final ObjectProperty<SceneUpdateJobParameters> sceneJobUpdateParametersProperty = new SimpleObjectProperty<>();

	private BlockTree blockTree = null;

	public MeshGeneratorJobManager(
			final DataSource<?, ?> source,
			final T identifier,
			final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks,
			final InterruptibleFunction<T, Interval[]>[] getBlockLists,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes,
			final ExecutorService manager,
			final PriorityExecutorService<Integer> workers,
			final IntegerProperty numPendingTasks,
			final IntegerProperty numCompletedTasks,
			final int rendererBlockSize)
	{
		this.source = source;
		this.identifier = identifier;
		this.meshesAndBlocks = meshesAndBlocks;
		this.getBlockLists = getBlockLists;
		this.getMeshes = getMeshes;
		this.manager = manager;
		this.workers = workers;
		this.numPendingTasks = numPendingTasks;
		this.numCompletedTasks = numCompletedTasks;
		this.rendererBlockSize = rendererBlockSize;
	}

	public void submit(
			final int preferredScaleIndex,
			final int highestScaleIndex,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final ViewFrustum viewFrustum,
			final AffineTransform3D eyeToWorldTransform)
	{
		final SceneUpdateJobParameters params = new SceneUpdateJobParameters(
				preferredScaleIndex,
				highestScaleIndex,
				simplificationIterations,
				smoothingLambda,
				smoothingIterations,
				viewFrustum,
				eyeToWorldTransform
			);

		synchronized (sceneJobUpdateParametersProperty)
		{
			final boolean needToSubmit = sceneJobUpdateParametersProperty.get() == null;
			sceneJobUpdateParametersProperty.set(params);
			if (needToSubmit)
				manager.submit(this::updateScene);
		}
	}

	public synchronized void interrupt()
	{
		isInterrupted.set(true);

		LOG.debug("Interrupting for {} keys={}", this.identifier, tasks.keySet());
		for (final InterruptibleFunction<T, Interval[]> getBlockList : this.getBlockLists)
			getBlockList.interruptFor(this.identifier);

		tasks.values().forEach(this::interruptTask);

		for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
			tasks.keySet().forEach(getMesh::interruptFor);

		tasks.clear();
	}

	private synchronized void updateScene()
	{
		final SceneUpdateJobParameters params;
		synchronized (sceneJobUpdateParametersProperty)
		{
			params = sceneJobUpdateParametersProperty.get();
			sceneJobUpdateParametersProperty.set(null);
		}

		// TODO: save previously created tree and re-use it if the set of blocks hasn't changed (i.e. affected blocks in the canvas haven't changed)
		blockTree = createRendererBlockTree();

		final Set<BlockTreeEntry> blocksToRender = getBlocksToRender(
				blockTree,
				params.preferredScaleIndex,
				params.highestScaleIndex,
				params.viewFrustum,
				params.eyeToWorldTransform
			);

		if (isInterrupted.get())
		{
			LOG.debug("Got interrupted before building meshes -- returning");
			return;
		}

		final int numBlocksToRenderBeforeFiltering = blocksToRender.size();

		renderListFilter.update(
				blockTree,
				blocksToRender,
				params.simplificationIterations,
				params.smoothingLambda,
				params.smoothingIterations
			);

		if (blocksToRender.isEmpty())
		{
			LOG.debug("No blocks need to be rendered");
			return;
		}

//		int numHighResMeshesToRemove = 0;
//		for (final Set<ShapeKey<T>> highResMeshesToRemove : renderListFilter.postponeRemovalHighRes.values())
//			numHighResMeshesToRemove += highResMeshesToRemove.size();
//		LOG.debug("blocksToRender before filtering={}, blocksToRender after filtering={}, low-res meshes to replace={}, high-res meshes to replace={}", numBlocksToRenderBeforeFiltering, blocksToRender.size(), renderListFilter.postponeRemovalLowRes.size(), numHighResMeshesToRemove);

		if (isInterrupted.get())
		{
			LOG.debug("Got interrupted before building meshes -- returning");
			return;
		}

		LOG.debug("Generating mesh with {} blocks for id {}.", blocksToRender.size(), identifier);

		numCompletedTasks.set(numBlocksToRenderBeforeFiltering - blocksToRender.size() - tasks.size());

		if (!isInterrupted.get())
		{
			for (final BlockTreeEntry blockEntry : blocksToRender)
			{
//				final ShapeKey<T> key = createShapeKey(
//						blockEntry,
//						params.simplificationIterations,
//						params.smoothingLambda,
//						params.smoothingIterations
//					);
				final ShapeKey<T> key = renderListFilter.allKeysAndEntriesToRender.inverse().get(blockEntry);

				final Runnable taskRunnable = () ->
				{
					final String initialName = Thread.currentThread().getName();
					Thread.currentThread().setName(initialName + " -- generating mesh: " + key);

					final Future<?> currentFuture;
					synchronized (this)
					{
						currentFuture = tasks.get(key).future;
					}

					final BooleanSupplier isTaskCanceled = () -> isInterrupted.get() || currentFuture.isCancelled();
					try
					{
						if (!isTaskCanceled.getAsBoolean())
						{
							final Pair<float[], float[]> verticesAndNormals = getMeshes[key.scaleIndex()].apply(key);

//							try {
//								Thread.sleep(5000);
//							} catch (final InterruptedException e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}

							if (!isTaskCanceled.getAsBoolean() && verticesAndNormals != null)
								onMeshGenerated(key, verticesAndNormals, isTaskCanceled);
						}
					}
					catch (final Exception e)
					{
						LOG.debug("Was not able to retrieve mesh for {}: {}", key, e);
					}
					finally
					{
						synchronized (this)
						{
							if (!isTaskCanceled.getAsBoolean())
								tasks.remove(key);
							numPendingTasks.set(tasks.size());
						}
						Thread.currentThread().setName(initialName);
					}
				};

				// render starting from coarsest scale level which allows to gradually improve resolution
				final int taskPriority = blockEntry.scaleLevel;

				final Task task = new Task(taskRunnable, taskPriority);
				tasks.put(key, task);

				// submit task immediately if top-level block
				final BlockTreeEntry parentEntry = blockTree.getParent(blockEntry);
				if (!renderListFilter.allKeysAndEntriesToRender.inverse().containsKey(parentEntry))
					submitTask(task);
			}

			numPendingTasks.set(tasks.size());
		}
	}

	private synchronized void submitTask(final Task task)
	{
		task.future = workers.submit(task.task, task.priority);
	}

	private synchronized void interruptTask(final Task task)
	{
		if (task.future != null)
			task.future.cancel(true);
	}

	private synchronized void onMeshGenerated(
			final ShapeKey<T> key,
			final Pair<float[], float[]> verticesAndNormals,
			final BooleanSupplier isTaskCanceled)
	{
		System.out.println("Block at scale level " + key.scaleIndex() + " is ready");

		final boolean nonEmptyMesh = Math.max(verticesAndNormals.getA().length, verticesAndNormals.getB().length) > 0;
		final MeshView mv = nonEmptyMesh ? makeMeshView(verticesAndNormals) : null;
		final Node blockShape = nonEmptyMesh ? createBlockShape(key) : null;
		LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);

		if (!isTaskCanceled.getAsBoolean())
		{
			synchronized (meshesAndBlocks)
			{
				final BlockTreeEntry entry = renderListFilter.allKeysAndEntriesToRender.get(key);
				final BlockTreeEntry parentEntry = blockTree.getParent(entry);
				final ShapeKey<T> parentKey = renderListFilter.allKeysAndEntriesToRender.inverse().get(parentEntry);

				if (renderListFilter.lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
				{
					final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes = renderListFilter.lowResParentBlockToHighResContainedMeshes.get(parentKey);
					if (!highResContainedMeshes.containsKey(key))
					{
						System.err.println("descendant block not found!");
						throw new RuntimeException();
					}

					if (nonEmptyMesh)
					{
						mv.setVisible(false);
						blockShape.setVisible(false);
						highResContainedMeshes.put(key, new ValueTriple<>(mv, blockShape, new AtomicBoolean(false)));
						meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
					}
					else
					{
						highResContainedMeshes.put(key, new ValueTriple<>(null, null, null));
					}

					/*if (!highResContainedMeshes.containsValue(null))
					{
						// all pending blocks are ready
//						meshesAndBlocks.putAll(renderListFilter.lowResParentBlockToHighResContainedMeshes.remove(parentKey));
						for (final Pair<MeshView, Node> containedValue : highResContainedMeshes.values())
						{
							if (containedValue.getA() != null)
								containedValue.getA().setVisible(true);

							if (containedValue.getB() != null)
								containedValue.getB().setVisible(true);
						}
						renderListFilter.lowResParentBlockToHighResContainedMeshes.remove(parentKey);

						// submit tasks for descendants
						highResContainedMeshes.keySet().forEach(containedKey -> submitTask(tasks.get(containedKey)));

						meshesAndBlocks.remove(parentKey);
					}*/
				}
				else if (nonEmptyMesh)
				{
					meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
				}

				/*final BlockTreeEntry entry = blockTree.find(key.interval(), key.scaleIndex());

				if (renderListFilter.postponeRemovalHighRes.containsKey(entry))
				{
					// new low-res block replaces a set of existing high-res blocks
					final Set<ShapeKey<T>> meshesToRemove = renderListFilter.postponeRemovalHighRes.remove(entry);
					if (nonEmptyMesh)
						meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
					meshesAndBlocks.keySet().removeAll(meshesToRemove);
				}
				else if (renderListFilter.postponeRemovalLowResParents.containsKey(entry))
				{
					// new high-res block is part of a group that will replace an existing low-res block
					final ShapeKey<T> entryParentKey = renderListFilter.postponeRemovalLowResParents.get(entry);
					final Set<BlockTreeEntry> blocksToRenderBeforeRemovingMesh = renderListFilter.postponeRemovalLowRes.get(entryParentKey);

					blocksToRenderBeforeRemovingMesh.remove(entry);
					renderListFilter.postponeRemovalLowResParents.remove(entry);

					if (!renderListFilter.lowResParentBlockToHighResContainedMeshes.containsKey(entryParentKey))
						renderListFilter.lowResParentBlockToHighResContainedMeshes.put(entryParentKey, new HashMap<>());

					if (nonEmptyMesh)
						renderListFilter.lowResParentBlockToHighResContainedMeshes.get(entryParentKey).put(key, new ValuePair<>(mv, blockShape));

					if (blocksToRenderBeforeRemovingMesh.isEmpty())
					{
						System.out.println(String.format("Got block at scale level %d. Parent: %s, all blocks are ready", key.scaleIndex(), blockTree.find(entryParentKey.interval(), entryParentKey.scaleIndex())));
						renderListFilter.postponeRemovalLowRes.remove(entryParentKey);
						meshesAndBlocks.putAll(renderListFilter.lowResParentBlockToHighResContainedMeshes.remove(entryParentKey));
						meshesAndBlocks.remove(entryParentKey);
					}
					else
					{
						System.out.println(String.format("Got block at scale level %d. Parent: %s, number of blocks left: %d", key.scaleIndex(), blockTree.find(entryParentKey.interval(), entryParentKey.scaleIndex()), blocksToRenderBeforeRemovingMesh.size()));
					}
				}
				else if (nonEmptyMesh)
				{
					meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
				}*/
			}

			if (!nonEmptyMesh)
			{
				// update tasks
				onMeshAdded(key);
			}
		}
	}

	public synchronized void onMeshAdded(final ShapeKey<T> key)
	{
		numCompletedTasks.set(numCompletedTasks.get() + 1);

		final BlockTreeEntry entry = renderListFilter.allKeysAndEntriesToRender.get(key);
		final BlockTreeEntry parentEntry = blockTree.getParent(entry);
		final ShapeKey<T> parentKey = renderListFilter.allKeysAndEntriesToRender.inverse().get(parentEntry);

		if (renderListFilter.lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
		{
			final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes = renderListFilter.lowResParentBlockToHighResContainedMeshes.get(parentKey);

			// mark block as ready
			if (highResContainedMeshes.get(key).getC() != null)
				highResContainedMeshes.get(key).getC().set(true);

//			if (!highResContainedMeshes.containsValue(null))
//			{

			// check if all pending blocks are ready
			int numPendingBlocks = 0;
			for (final Triple<MeshView, Node, AtomicBoolean> value : highResContainedMeshes.values())
				if (value == null || (value.getC() != null && !value.getC().get()))
					++numPendingBlocks;

			if (numPendingBlocks == 0)
			{
				// update the visibility of the meshes and remove the parent mesh

				try
				{
					for (final Triple<MeshView, Node, AtomicBoolean> containedValue : highResContainedMeshes.values())
					{
						if (containedValue.getA() != null)
							containedValue.getA().setVisible(true);

						if (containedValue.getB() != null)
							containedValue.getB().setVisible(true);
					}

					renderListFilter.lowResParentBlockToHighResContainedMeshes.remove(parentKey);

					synchronized (meshesAndBlocks)
					{
						meshesAndBlocks.remove(parentKey);
					}

					// submit tasks for descendants
					final AtomicInteger numDescendants = new AtomicInteger();
					highResContainedMeshes.keySet().forEach(containedKey -> {
						final Set<ShapeKey<T>> descendants = Optional.ofNullable(renderListFilter.lowResParentBlockToHighResContainedMeshes.get(containedKey)).map(val -> val.keySet()).orElse(null);
						if (descendants != null)
						{
							descendants.forEach(descendant -> submitTask(tasks.get(descendant)));
							numDescendants.addAndGet(descendants.size());
						}
					});

					System.out.println(String.format("Added mesh %s, all blocks are ready, submitting %d tasks for descendants", entry, numDescendants.get()));
				}
				catch (final Exception e)
				{
					e.printStackTrace();
					throw e;
				}
			}
			else
			{
				System.out.println(String.format("Added mesh %s, number of pending blocks: %d", entry, numPendingBlocks));
			}

//			}
		}
		else
		{
			// submit tasks for descendants
			final Set<ShapeKey<T>> descendants = Optional.ofNullable(renderListFilter.lowResParentBlockToHighResContainedMeshes.get(key)).map(val -> val.keySet()).orElse(null);
			if (descendants != null)
			{
				descendants.forEach(descendant -> submitTask(tasks.get(descendant)));
				System.out.println(String.format("Added top-level mesh %s, submitting %d tasks for descendants", entry, descendants.size()));
			}
			else
			{
				System.out.println(String.format("Added top-level mesh %s, no descendants are present", entry));
			}
		}
	}

	private BlockTree createRendererBlockTree()
	{
		@SuppressWarnings("unchecked")
		final Set<HashWrapper<Interval>>[] sourceBlocks = new Set[getBlockLists.length];
		for (int i = 0; i < sourceBlocks.length; ++i)
		{
			sourceBlocks[i] = new HashSet<>(
					Arrays
						.stream(getBlockLists[i].apply(identifier))
						.map(HashWrapper::interval)
						.collect(Collectors.toSet())
				);
		}

		final long[][] sourceDimensions = new long[source.getNumMipmapLevels()][];
		Arrays.setAll(sourceDimensions, i -> source.getGrid(i).getImgDimensions());

		final double[][] sourceScales = new double[source.getNumMipmapLevels()][];
		Arrays.setAll(sourceScales, i -> DataSource.getScale(source, 0, i));

		final int[][] rendererFullBlockSizes = getRendererFullBlockSizes(rendererBlockSize, sourceScales);
		LOG.debug("Source scales: {}, renderer block sizes: {}", sourceScales, rendererFullBlockSizes);

		// Create new block grids with renderer block size based on source blocks
		@SuppressWarnings("unchecked")
		final Set<HashWrapper<Interval>>[] rendererBlocks = new Set[sourceBlocks.length];
		for (int i = 0; i < rendererBlocks.length; ++i)
		{
			rendererBlocks[i] = new HashSet<>();
			final CellGrid rendererBlockGrid = new CellGrid(sourceDimensions[i], rendererFullBlockSizes[i]);
			for (final HashWrapper<Interval> sourceBlock : sourceBlocks[i])
			{
				final long[] intersectingRendererBlocksIndices = Grids.getIntersectingBlocks(sourceBlock.getData(), rendererBlockGrid);
				for (final long intersectingRendererBlocksIndex : intersectingRendererBlocksIndices)
				{
					final Interval intersectingRendererBlock = Grids.getCellInterval(rendererBlockGrid, intersectingRendererBlocksIndex);
					rendererBlocks[i].add(HashWrapper.interval(intersectingRendererBlock));
				}
			}
		}

		return new BlockTree(rendererBlocks, sourceDimensions, rendererFullBlockSizes, sourceScales);
	}

	private Set<BlockTreeEntry> getBlocksToRender(
			final BlockTree blockTree,
			final int preferredScaleIndex,
			final int highestScaleIndex,
			final ViewFrustum viewFrustum,
			final AffineTransform3D eyeToWorldTransform)
	{
		final ViewFrustumCulling[] viewFrustumCullingInSourceSpace = new ViewFrustumCulling[source.getNumMipmapLevels()];
		final double[] minMipmapPixelSize = new double[source.getNumMipmapLevels()];
		final double[] maxRelativeScaleFactors = new double[source.getNumMipmapLevels()];
		for (int i = 0; i < viewFrustumCullingInSourceSpace.length; ++i)
		{
			final AffineTransform3D sourceToWorldTransform = new AffineTransform3D();
			source.getSourceTransform(0, i, sourceToWorldTransform);

			final AffineTransform3D cameraToSourceTransform = new AffineTransform3D();
			cameraToSourceTransform.preConcatenate(eyeToWorldTransform).preConcatenate(sourceToWorldTransform.inverse());

			viewFrustumCullingInSourceSpace[i] = new ViewFrustumCulling(viewFrustum, cameraToSourceTransform);

			final double[] extractedScale = new double[3];
			Arrays.setAll(extractedScale, d -> Affine3DHelpers.extractScale(cameraToSourceTransform.inverse(), d));

			minMipmapPixelSize[i] = Arrays.stream(extractedScale).min().getAsDouble();
			maxRelativeScaleFactors[i] = Arrays.stream(DataSource.getRelativeScales(source, 0, 0, i)).max().getAsDouble();
		}

		final Set<BlockTreeEntry> blocksToRender = new HashSet<>();

		final Queue<BlockTreeEntry> blocksQueue = new ArrayDeque<>();
		blocksQueue.addAll(blockTree.getTreeLevel(blockTree.getNumLevels() - 1).valueCollection());

		while (!blocksQueue.isEmpty())
		{
			final BlockTreeEntry blockEntry = blocksQueue.poll();
			if (viewFrustumCullingInSourceSpace[blockEntry.scaleLevel].intersects(blockEntry.interval()))
			{
				final double distanceFromCamera = viewFrustumCullingInSourceSpace[blockEntry.scaleLevel].distanceFromCamera(blockEntry.interval());
				final double screenSizeToViewPlaneRatio = viewFrustum.screenSizeToViewPlaneRatio(distanceFromCamera);
				final double screenPixelSize = screenSizeToViewPlaneRatio * minMipmapPixelSize[blockEntry.scaleLevel];
				LOG.debug("scaleIndex={}, screenSizeToViewPlaneRatio={}, screenPixelSize={}", blockEntry.scaleLevel, screenSizeToViewPlaneRatio, screenPixelSize);

				blocksToRender.add(blockEntry);

				// check if needed to subdivide the block
				if (blockEntry.scaleLevel > highestScaleIndex && screenPixelSize > maxRelativeScaleFactors[preferredScaleIndex])
				{
					if (isInterrupted.get())
					{
						LOG.debug("Interrupted while building a list of blocks for rendering for label {}", identifier);
						break;
					}

					if (blockEntry.children != null)
					{
						final TLongObjectHashMap<BlockTreeEntry> nextLevelTree = blockTree.getTreeLevel(blockEntry.scaleLevel - 1);
						for (final TLongIterator it = blockEntry.children.iterator(); it.hasNext();)
							blocksQueue.add(nextLevelTree.get(it.next()));
					}
				}
			}
		}

//		final Map<Integer, Integer> scaleIndexToNumBlocks = new TreeMap<>();
//		for (final BlockTreeEntry blockEntry : blocksToRender)
//			scaleIndexToNumBlocks.put(blockEntry.scaleLevel, scaleIndexToNumBlocks.getOrDefault(blockEntry.scaleLevel, 0) + 1);
//		LOG.debug("Label ID {}: ", identifier, scaleIndexToNumBlocks);

		return blocksToRender;
	}


	Group meshesGroup, blocksGroup;

	private final class RenderListFilter
	{
		/**
		 * A mapping from a pending low-res block to a set of displayed high-res blocks.
		 * The displayed high-res blocks (mapped values) need to be removed from the scene once the pending low-res block (key) is ready.
		 */
//		final Map<BlockTreeEntry, Set<ShapeKey<T>>> postponeRemovalHighRes = new HashMap<>();

		/**
		 * A mapping from a displayed low-res block to a set of pending high-res blocks.
		 * The displayed low-res block (key) needs to be removed from the scene once the entire set of pending high-res blocks (mapped values) is ready.
		 */
//		final Map<ShapeKey<T>, Set<BlockTreeEntry>> postponeRemovalLowRes = new HashMap<>();

		/**
		 * Helper mapping from a pending high-res block to its displayed low-res parent block
		 * (essentially the inverse mapping of {@link #postponeRemovalLowRes}).
		 */
//		final Map<BlockTreeEntry, ShapeKey<T>> postponeRemovalLowResParents = new HashMap<>();

		/**
		 * Temporary storage for new rendered high-res blocks that will be added onto the scene in bulk once their low-res parent block can be fully replaced.
		 * Represented as a mapping from a low-res parent block to a set of contained high-res blocks and generated meshes for them.
		 */
		final Map<ShapeKey<T>, Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>>> lowResParentBlockToHighResContainedMeshes = new HashMap<>();

		final BiMap<ShapeKey<T>, BlockTreeEntry> allKeysAndEntriesToRender = HashBiMap.create();

		/**
		 * Intersects the current state with the new requested set of blocks for rendering.
		 * Updates meshes in the scene, running tasks, lists of blocks for postponed removal.
		 * Modifies {@code blocksToRender} with the intersected set of blocks that still need to be rendered.
		 *
		 * @param blockTree
		 * @param blocksToRender
		 * @param simplificationIterations
		 * @param smoothingLambda
		 * @param smoothingIterations
		 */
		private void update(
				final BlockTree blockTree,
				final Set<BlockTreeEntry> blocksToRender,
				final int simplificationIterations,
				final double smoothingLambda,
				final int smoothingIterations)
		{
			synchronized (meshesAndBlocks)
			{

				// TODO: re-work state intersection algorithm. For now remove everything from the scene and generate all requsted blocks

				for (final Entry<ShapeKey<T>, Task> taskToInterrupt : tasks.entrySet())
				{
					getMeshes[taskToInterrupt.getKey().scaleIndex()].interruptFor(taskToInterrupt.getKey());
					interruptTask(taskToInterrupt.getValue());
				}
				tasks.clear();

				meshesAndBlocks.clear();
				lowResParentBlockToHighResContainedMeshes.clear();


				// FIXME: this shouldn't be necessary because meshesAndBlocks.clear() should call observers
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					meshesGroup.getChildren().clear();
					blocksGroup.getChildren().clear();
				});


//				postponeRemovalHighRes.clear();
//				postponeRemovalLowRes.clear();
//				postponeRemovalLowResParents.clear();




				// set up relations between ancestors and descendants
				allKeysAndEntriesToRender.clear();
				for (final BlockTreeEntry blockEntry : blocksToRender)
				{
					final ShapeKey<T> keyToRender = createShapeKey(
							blockEntry,
							simplificationIterations,
							smoothingLambda,
							smoothingIterations
						);
					allKeysAndEntriesToRender.put(keyToRender, blockEntry);
				}
				for (final Entry<ShapeKey<T>, BlockTreeEntry> keyAndEntry : allKeysAndEntriesToRender.entrySet())
				{
					final BlockTreeEntry parentEntry = blockTree.getParent(keyAndEntry.getValue());
					final ShapeKey<T> parentKey = allKeysAndEntriesToRender.inverse().get(parentEntry);
					if (parentEntry != null)
					{
						if (!lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
							lowResParentBlockToHighResContainedMeshes.put(parentKey, new HashMap<>());
						lowResParentBlockToHighResContainedMeshes.get(parentKey).put(keyAndEntry.getKey(), null);
					}
				}






				/*
				// blocksToRender will be used to store filtered set, copy the full set to a separate collection
				final BiMap<ShapeKey<T>, BlockTreeEntry> allKeysAndEntriesToRender = HashBiMap.create();
				for (final BlockTreeEntry blockEntry : blocksToRender)
				{
					final ShapeKey<T> keyToRender = createShapeKey(
							blockEntry,
							simplificationIterations,
							smoothingLambda,
							smoothingIterations
						);
					allKeysAndEntriesToRender.put(keyToRender, blockEntry);
				}

				// remove all pending ancestors for which a descendant already exists in the scene
				final Set<BlockTreeEntry> pendingLowResParentBlocksToIgnore = new HashSet<>();
				for (final Entry<ShapeKey<T>, BlockTreeEntry> pendingKeyAndEntry : allKeysAndEntriesToRender.entrySet())
				{
					if (meshesAndBlocks.containsKey(pendingKeyAndEntry.getKey()))
					{
						// if this block is already present in the scene, ignore all pending parent blocks of this block
						// (there is no point in rendering a lower-resolution version of the requested block that is already displayed)
						BlockTreeEntry parentEntry = pendingKeyAndEntry.getValue();
						do
						{
							parentEntry = blockTree.getParent(parentEntry);
							if (parentEntry != null)
								pendingLowResParentBlocksToIgnore.add(parentEntry);

						}
						while (parentEntry != null);
					}
				}
				blocksToRender.removeAll(pendingLowResParentBlocksToIgnore);
				allKeysAndEntriesToRender.inverse().keySet().removeAll(pendingLowResParentBlocksToIgnore);







				// interrupt and remove running tasks for blocks that are not needed anymore
				final List<ShapeKey<T>> taskKeysToInterrupt = tasks.keySet().stream()
					.filter(key -> !allKeysAndEntriesToRender.containsKey(key))
					.collect(Collectors.toList());
				for (final ShapeKey<T> taskKeyToInterrupt : taskKeysToInterrupt)
				{
					getMeshes[taskKeyToInterrupt.scaleIndex()].interruptFor(taskKeyToInterrupt);
					Optional.ofNullable(tasks.remove(taskKeyToInterrupt)).ifPresent(task -> task.cancel(true));
				}

				// filter out pending blocks that are already being processed
				blocksToRender.removeIf(blockEntry -> tasks.containsKey(allKeysAndEntriesToRender.inverse().get(blockEntry)));

				// filter out pending high-res blocks that have already been rendered but their uploading to the scene has been postponed
				for (final Map<ShapeKey<T>, Pair<MeshView, Node>> highResPostponedMeshes : lowResParentBlockToHighResContainedMeshes.values())
					for (final ShapeKey<T> highResKey : highResPostponedMeshes.keySet())
						if (allKeysAndEntriesToRender.containsKey(highResKey))
							blocksToRender.remove(allKeysAndEntriesToRender.get(highResKey));

				final Map<BlockTreeEntry, ShapeKey<T>> displayedMeshEntriesToKeys = new HashMap<>();
				for (final ShapeKey<T> meshKey : meshesAndBlocks.keySet())
				{
					final BlockTreeEntry meshEntry = blockTree.find(meshKey.interval(), meshKey.scaleIndex());
					displayedMeshEntriesToKeys.put(meshEntry, meshKey);
				}

				// prepare for new state
				postponeRemovalHighRes.clear();
				postponeRemovalLowRes.clear();
				postponeRemovalLowResParents.clear();

				// determine which displayed meshes need to be removed
				final Set<ShapeKey<T>> displayedMeshKeysToKeep = new HashSet<>();

				for (final Entry<BlockTreeEntry, ShapeKey<T>> displayedMeshEntryAndKey : displayedMeshEntriesToKeys.entrySet())
				{
					final ShapeKey<T> displayedMeshKey = displayedMeshEntryAndKey.getValue();
					final BlockTreeEntry displayedMeshEntry = displayedMeshEntryAndKey.getKey();

					// check if the requested block is already in the scene
					if (allKeysAndEntriesToRender.containsKey(displayedMeshKey))
					{
						blocksToRender.remove(displayedMeshEntry);
						displayedMeshKeysToKeep.add(displayedMeshKey);
						continue;
					}

					// check if needed to render block at lower resolution than currently displayed
					BlockTreeEntry parentEntry = displayedMeshEntry;
					while (parentEntry != null)
					{
						if (allKeysAndEntriesToRender.inverse().containsKey(parentEntry))
						{
							// need to render block at lower resolution, postpone removal of meshes at higher resolution
							if (!postponeRemovalHighRes.containsKey(parentEntry))
								postponeRemovalHighRes.put(parentEntry, new HashSet<>());
							postponeRemovalHighRes.get(parentEntry).add(displayedMeshKey);
							displayedMeshKeysToKeep.add(displayedMeshKey);
							break;
						}
						parentEntry = blockTree.getParent(parentEntry);
					}
				}

				for (final BlockTreeEntry blockEntry : allKeysAndEntriesToRender.inverse().keySet())
				{
					if (displayedMeshEntriesToKeys.containsKey(blockEntry))
						continue;

					// check if needed to render block at higher resolution than currently displayed
					BlockTreeEntry parentEntry = blockEntry;
					while (parentEntry != null)
					{
						if (displayedMeshEntriesToKeys.containsKey(parentEntry))
						{
							// need to render block at higher resolution, postpone removal of meshes at lower resolution
							final ShapeKey<T> displayedParentMeshKey = displayedMeshEntriesToKeys.get(parentEntry);
							if (!postponeRemovalLowRes.containsKey(displayedParentMeshKey))
								postponeRemovalLowRes.put(displayedParentMeshKey, new HashSet<>());
							postponeRemovalLowRes.get(displayedParentMeshKey).add(blockEntry);
							postponeRemovalLowResParents.put(blockEntry, displayedParentMeshKey);
							displayedMeshKeysToKeep.add(displayedParentMeshKey);
							break;
						}
						parentEntry = blockTree.getParent(parentEntry);
					}
				}

				// remove blocks from the scene that are not needed anymore
				meshesAndBlocks.keySet().retainAll(displayedMeshKeysToKeep);

				// update the contents of already rendered high-res blocks that have not been uploaded to the scene yet with respect to the new state
				lowResParentBlockToHighResContainedMeshes.keySet().retainAll(postponeRemovalLowRes.keySet());
				for (final Map<ShapeKey<T>, Pair<MeshView, Node>> highResPostponedMeshes : lowResParentBlockToHighResContainedMeshes.values())
					highResPostponedMeshes.keySet().retainAll(allKeysAndEntriesToRender.keySet());
				lowResParentBlockToHighResContainedMeshes.entrySet().removeIf(entry -> entry.getValue().isEmpty());

				// update the mapping that specifies when to replace a displayed low-res block with a set of rendered high-res blocks
				for (final Entry<ShapeKey<T>, Map<ShapeKey<T>, Pair<MeshView, Node>>> lowResParentBlockToHighResBlocks : lowResParentBlockToHighResContainedMeshes.entrySet())
				{
					final ShapeKey<T> displayedLowResBlock = lowResParentBlockToHighResBlocks.getKey();
					final Set<ShapeKey<T>> renderedHighResBlockKeys = lowResParentBlockToHighResBlocks.getValue().keySet();
					final Set<BlockTreeEntry> containedHighResBlockEntries = postponeRemovalLowRes.get(displayedLowResBlock);
					for (final ShapeKey<T> renderedHighResBlockKey : renderedHighResBlockKeys)
					{
						final BlockTreeEntry renderedHighResBlockEntry = allKeysAndEntriesToRender.get(renderedHighResBlockKey);
						containedHighResBlockEntries.remove(renderedHighResBlockEntry);
						postponeRemovalLowResParents.remove(renderedHighResBlockEntry);
					}
				}

				// update the scene with respect to the new list of rendered high-res blocks that have not been uploaded to the scene yet
				for (final Iterator<Entry<ShapeKey<T>, Set<BlockTreeEntry>>> it = postponeRemovalLowRes.entrySet().iterator(); it.hasNext();)
				{
					final Entry<ShapeKey<T>, Set<BlockTreeEntry>> entry = it.next();
					final ShapeKey<T> displayedLowResBlock = entry.getKey();
					final Set<BlockTreeEntry> blocksToRenderBeforeRemovingMesh = entry.getValue();
					if (blocksToRenderBeforeRemovingMesh.isEmpty())
					{
						it.remove();
						meshesAndBlocks.putAll(renderListFilter.lowResParentBlockToHighResContainedMeshes.remove(displayedLowResBlock));
						meshesAndBlocks.remove(displayedLowResBlock);
					}
				}*/
			}
		}
	}

	private ShapeKey<T> createShapeKey(
			final BlockTreeEntry blockEntry,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations)
	{
		final Interval blockInterval = blockEntry.interval();
		return new ShapeKey<>(
				identifier,
				blockEntry.scaleLevel,
				simplificationIterations,
				smoothingLambda,
				smoothingIterations,
				Intervals.minAsLongArray(blockInterval),
				Intervals.maxAsLongArray(blockInterval)
			);
	}

	static int[][] getRendererFullBlockSizes(final int rendererBlockSize, final double[][] sourceScales)
	{
		final int[][] rendererFullBlockSizes = new int[sourceScales.length][];
		for (int i = 0; i < rendererFullBlockSizes.length; ++i)
		{
			rendererFullBlockSizes[i] = new int[sourceScales[i].length];
			final double minScale = Arrays.stream(sourceScales[i]).min().getAsDouble();
			for (int d = 0; d < rendererFullBlockSizes[i].length; ++d)
			{
				final double scaleRatio = sourceScales[i][d] / minScale;
				final double bestBlockSize = rendererBlockSize / scaleRatio;
				final int adjustedBlockSize;
				if (i > 0) {
					final int closestMultipleFactor = Math.max(1, (int) Math.round(bestBlockSize / rendererFullBlockSizes[i - 1][d]));
					adjustedBlockSize = rendererFullBlockSizes[i - 1][d] * closestMultipleFactor;
				} else {
					adjustedBlockSize = (int) Math.round(bestBlockSize);
				}
				final int clampedBlockSize = Math.max(
						Viewer3DConfig.RENDERER_BLOCK_SIZE_MIN_VALUE, Math.min(
								Viewer3DConfig.RENDERER_BLOCK_SIZE_MAX_VALUE,
								adjustedBlockSize
							)
					);
				rendererFullBlockSizes[i][d] = clampedBlockSize;
			}
		}
		return rendererFullBlockSizes;
	}


	private static MeshView makeMeshView(final Pair<float[], float[]> verticesAndNormals)
	{
		final float[]      vertices = verticesAndNormals.getA();
		final float[]      normals  = verticesAndNormals.getB();
		final TriangleMesh mesh     = new TriangleMesh();
		mesh.getPoints().addAll(vertices);
		mesh.getNormals().addAll(normals);
		mesh.getTexCoords().addAll(0, 0);
		mesh.setVertexFormat(VertexFormat.POINT_NORMAL_TEXCOORD);
		final int[] faceIndices = new int[vertices.length];
		for (int i = 0, k = 0; i < faceIndices.length; i += 3, ++k)
		{
			faceIndices[i + 0] = k;
			faceIndices[i + 1] = k;
			faceIndices[i + 2] = 0;
		}
		mesh.getFaces().addAll(faceIndices);
		final PhongMaterial material = Meshes.painteraPhongMaterial();
		//						material.diffuseColorProperty().bind( color );
		final MeshView mv = new MeshView(mesh);
		mv.setOpacity(1.0);
		//						synchronized ( this.isVisible )
		//						{
		//							mv.visibleProperty().bind( this.isVisible );
		//						}
		mv.setCullFace(CullFace.FRONT);
		mv.setMaterial(material);
		mv.setDrawMode(DrawMode.FILL);
		return mv;
	}

	private Node createBlockShape(final ShapeKey<T> key)
	{
		final AffineTransform3D transform = new AffineTransform3D();
		source.getSourceTransform(0, key.scaleIndex(), transform);

		final double[] worldMin = new double[3], worldMax = new double[3];
		final Interval keyInterval = key.interval();
		transform.apply(Intervals.minAsDoubleArray(keyInterval), worldMin);
		transform.apply(Intervals.maxAsDoubleArray(Intervals.expand(keyInterval, 1)), worldMax); // add 1px to the source interval so that max coordinate is transformed properly

		final Interval blockInterval = Intervals.smallestContainingInterval(new FinalRealInterval(worldMin, worldMax));

		final PolygonMeshView box = new PolygonMeshView(Meshes.createQuadrilateralMesh(
				blockInterval.dimension(0),
				blockInterval.dimension(1),
				blockInterval.dimension(2)
			));

		box.setTranslateX(blockInterval.min(0) + blockInterval.dimension(0) / 2);
		box.setTranslateY(blockInterval.min(1) + blockInterval.dimension(1) / 2);
		box.setTranslateZ(blockInterval.min(2) + blockInterval.dimension(2) / 2);

		final PhongMaterial material = Meshes.painteraPhongMaterial();

		box.setCullFace(CullFace.NONE);
		box.setMaterial(material);
		box.setDrawMode(DrawMode.LINE);

		return box;
	}
}
