package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.data.DataSource;
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
import gnu.trove.map.hash.TLongObjectHashMap;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableMap;
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
		final MeshWorkerPriority priority;

		Future<?> future;

		Task(final Runnable task, final MeshWorkerPriority priority)
		{
			this.task = task;
			this.priority = priority;
		}
	}

	private static final class BlockTreeEntry
	{
		static final long EMPTY = -1;

		final long index;
		final int scaleLevel;
		final long parent;

		public BlockTreeEntry(final long index, final int scaleLevel, final long parent)
		{
			this.index = index;
			this.scaleLevel = scaleLevel;
			this.parent = parent;
		}

		@Override
		public int hashCode()
		{
			return Long.hashCode(index) + 31 * Integer.hashCode(scaleLevel);
		}

		@Override
		public boolean equals(final Object o)
		{
			if (o instanceof BlockTreeEntry)
			{
				final BlockTreeEntry other = (BlockTreeEntry)o;
				return this.index == other.index && this.scaleLevel == other.scaleLevel;
			}
			return false;
		}

		@Override
		public String toString()
		{
			return String.format("[index=%d, scaleLevel=%d, parent=%d]", index, scaleLevel, parent);
		}
	}

	private final DataSource<?, ?> source;

	private final T identifier;

	private final Map<ShapeKey<T>, Task> tasks = new HashMap<>();

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks;

	private final InterruptibleFunction<T, Interval[]>[] getBlockLists;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes;

	private final ExecutorService manager;

	private final PriorityExecutorService<MeshWorkerPriority> workers;

	private final IntegerProperty numPendingTasks;

	private final IntegerProperty numCompletedTasks;

	private final int rendererBlockSize;

	private final int numScaleLevels;

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	private final RenderListFilter renderListFilter;

	private final ObjectProperty<SceneUpdateJobParameters> sceneJobUpdateParametersProperty = new SimpleObjectProperty<>();

	public MeshGeneratorJobManager(
			final DataSource<?, ?> source,
			final T identifier,
			final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks,
			final InterruptibleFunction<T, Interval[]>[] getBlockLists,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes,
			final ExecutorService manager,
			final PriorityExecutorService<MeshWorkerPriority> workers,
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
		this.numScaleLevels = source.getNumMipmapLevels();
		this.renderListFilter = new RenderListFilter(this.numScaleLevels);
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


	private final class RendererMetadata
	{
		final double[][] scales;
		final CellGrid[] sourceGrids;
		final CellGrid[] rendererGrids;

		RendererMetadata()
		{
			scales = new double[numScaleLevels][];
			Arrays.setAll(scales, i -> DataSource.getScale(source, 0, i));

			sourceGrids = new CellGrid[numScaleLevels];
			Arrays.setAll(sourceGrids, i -> source.getGrid(i));

			final int[][] rendererFullBlockSizes = getRendererFullBlockSizes(rendererBlockSize, scales);
			LOG.debug("Scales: {}, renderer block sizes: {}", scales, rendererFullBlockSizes);

			rendererGrids = new CellGrid[sourceGrids.length];
			for (int i = 0; i < rendererGrids.length; ++i)
				rendererGrids[i] = new CellGrid(sourceGrids[i].getImgDimensions(), rendererFullBlockSizes[i]);
		}

		Interval getBlockInterval(final BlockTreeEntry blockEntry)
		{
			return Grids.getCellInterval(rendererGrids[blockEntry.scaleLevel], blockEntry.index);
		}
	}

	private synchronized void updateScene()
	{
		System.out.println("Scene update initiated...");

		final SceneUpdateJobParameters params;
		synchronized (sceneJobUpdateParametersProperty)
		{
			params = sceneJobUpdateParametersProperty.get();
			sceneJobUpdateParametersProperty.set(null);
		}

		final RendererMetadata rendererMetadata = new RendererMetadata();

		final Map<BlockTreeEntry, Double> blocksToRender = getBlocksToRender(
				rendererMetadata,
				params.preferredScaleIndex,
				params.highestScaleIndex,
				params.viewFrustum,
				params.eyeToWorldTransform
			);

		System.out.println(System.lineSeparator() + "Blocks to render:");
		for (final BlockTreeEntry blockEntry : blocksToRender.keySet())
			System.out.println("   " + blockEntry);
		System.out.println();

		if (isInterrupted.get())
		{
			LOG.debug("Got interrupted before building meshes -- returning");
			System.out.println("...interrupted...");
			return;
		}

		final int numBlocksToRenderBeforeFiltering = blocksToRender.size();

		long elapsedFilter = System.nanoTime();
		renderListFilter.update(
				rendererMetadata,
				blocksToRender.keySet(),
				params.simplificationIterations,
				params.smoothingLambda,
				params.smoothingIterations
			);
		elapsedFilter = System.nanoTime() - elapsedFilter;

		System.out.println("### Scene updated, lowResParentBlockToHighResContainedMeshes size: " + renderListFilter.lowResParentBlockToHighResContainedMeshes.size() + " ###");
		System.out.println("Blocks to render before filtering: " + numBlocksToRenderBeforeFiltering + ", after filtering: " + blocksToRender.size());

		if (blocksToRender.isEmpty())
		{
			LOG.debug("No blocks need to be rendered");
			return;
		}

		if (isInterrupted.get())
		{
			LOG.debug("Got interrupted before building meshes -- returning");
			return;
		}

		LOG.debug("Generating mesh with {} blocks for id {}.", blocksToRender.size(), identifier);

		numCompletedTasks.set(numBlocksToRenderBeforeFiltering - blocksToRender.size() - tasks.size());

		if (!isInterrupted.get())
		{
			for (final Entry<BlockTreeEntry, Double> blockEntryAndDistance : blocksToRender.entrySet())
			{
				final BlockTreeEntry blockEntry = blockEntryAndDistance.getKey();
				final ShapeKey<T> key = renderListFilter.allKeysAndEntriesToRender.inverse().get(blockEntry);

				final Runnable taskRunnable = () ->
				{
					final String initialName = Thread.currentThread().getName();
					Thread.currentThread().setName(initialName + " -- generating mesh: " + key);

					final Future<?> currentFuture;
					synchronized (this)
					{
						currentFuture = tasks.get(key).future;
						System.out.println("Executing task for distance " + tasks.get(key).priority.distanceFromCamera + " at scale level " + tasks.get(key).priority.scaleLevel);
					}

					final BooleanSupplier isTaskCanceled = () -> isInterrupted.get() || currentFuture.isCancelled();
					try
					{
						if (!isTaskCanceled.getAsBoolean())
						{
							final Pair<float[], float[]> verticesAndNormals = getMeshes[key.scaleIndex()].apply(key);
							if (verticesAndNormals != null)
								onMeshGenerated(key, verticesAndNormals, isTaskCanceled);
						}
					}
					catch (final Exception e)
					{
						LOG.debug("Was not able to retrieve mesh for {}: {}", key, e);
						e.printStackTrace();
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

				final MeshWorkerPriority taskPriority = new MeshWorkerPriority(blockEntryAndDistance.getValue(), blockEntry.scaleLevel);
				final Task task = new Task(taskRunnable, taskPriority);
				tasks.put(key, task);
			}


			// Update the visibility and the task lists because the parent->block mapping may have changed
			for (final Iterator<Entry<ShapeKey<T>, Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>>>> it = renderListFilter.lowResParentBlockToHighResContainedMeshes.entrySet().iterator(); it.hasNext();)
			{
				final Entry<ShapeKey<T>, Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>>> entry = it.next();
				final ShapeKey<T> key = entry.getKey();
				final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes = entry.getValue();

				updateLowResToHighResBlockMapping(
						key,
						highResContainedMeshes,
						it::remove
					);
			}

			System.out.println();
			for (final BlockTreeEntry blockEntry : blocksToRender.keySet())
			{
				// submit task immediately if top-level block (in the current set, not necessarily the coarsest scale level)
				final BlockTreeEntry parentEntry = getParentEntry(blockEntry);
				if (!blocksToRender.containsKey(parentEntry))
				{
					final ShapeKey<T> key = renderListFilter.allKeysAndEntriesToRender.inverse().get(blockEntry);
					System.out.println("......... Submitting task for " + key + " ........");
					submitTask(tasks.get(key));
				}
			}
			System.out.println();

			numPendingTasks.set(tasks.size());
		}
	}

	private synchronized void submitTask(final Task task)
	{
		if (task.future == null)
			task.future = workers.submit(task.task, task.priority);
	}

	private synchronized void interruptTask(final Task task)
	{
		if (task.future != null)
			task.future.cancel(true);
	}

	private synchronized BlockTreeEntry getParentEntry(final BlockTreeEntry entry)
	{
		return entry.scaleLevel < numScaleLevels - 1 ? renderListFilter.blockIndicesToEntries[entry.scaleLevel + 1].get(entry.parent) : null;
	}

	private synchronized void onMeshGenerated(
			final ShapeKey<T> key,
			final Pair<float[], float[]> verticesAndNormals,
			final BooleanSupplier isTaskCanceled)
	{
		if (isTaskCanceled.getAsBoolean())
		{
			System.out.println("Task has been canceled");
			return;
		}

		System.out.println("Block at scale level " + key.scaleIndex() + " is ready");

		long elapsedMeshView = System.nanoTime();
		final boolean nonEmptyMesh = Math.max(verticesAndNormals.getA().length, verticesAndNormals.getB().length) > 0;
		final MeshView mv = nonEmptyMesh ? makeMeshView(verticesAndNormals) : null;
		final Node blockShape = nonEmptyMesh ? createBlockShape(key) : null;
		LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);
		elapsedMeshView = System.nanoTime() - elapsedMeshView;

		long elapsedUpdate = System.nanoTime();

		final BlockTreeEntry entry = renderListFilter.allKeysAndEntriesToRender.get(key);
		final BlockTreeEntry parentEntry = getParentEntry(entry);
		final ShapeKey<T> parentKey = renderListFilter.allKeysAndEntriesToRender.inverse().get(parentEntry);

		if (renderListFilter.lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
		{
			final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes = renderListFilter.lowResParentBlockToHighResContainedMeshes.get(parentKey);
			if (!highResContainedMeshes.containsKey(key))
			{
				System.err.println("descendant block not found!");
				throw new RuntimeException();
			}

			// Put the mesh in the map. The last value in the triple specifies that the mesh is not added onto the scene yet,
			// and once it's there, this will be changed to true in onMeshAdded() callback
			highResContainedMeshes.put(key, new ValueTriple<>(mv, blockShape, new AtomicBoolean(false)));

			if (nonEmptyMesh)
			{
				// hide the mesh until all descendants of the parent block are ready, this is handled in onMeshAdded() callback
				mv.setVisible(false);
				blockShape.setVisible(false);
			}
		}

		meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));

		elapsedUpdate = System.nanoTime() - elapsedUpdate;
		System.out.println(String.format("onMeshGenerated(), nonEmptyMesh: %b. Elapsed:  makeMeshView=%.2fs,  update=%.2fs", nonEmptyMesh, elapsedMeshView / 1e9, elapsedUpdate / 1e9));
	}

	public synchronized void onMeshAdded(final ShapeKey<T> key)
	{
		numCompletedTasks.set(numCompletedTasks.get() + 1);

		final BlockTreeEntry entry = renderListFilter.allKeysAndEntriesToRender.get(key);
		final BlockTreeEntry parentEntry = getParentEntry(entry);
		final ShapeKey<T> parentKey = renderListFilter.allKeysAndEntriesToRender.inverse().get(parentEntry);

		if (renderListFilter.lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
		{
			final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes = renderListFilter.lowResParentBlockToHighResContainedMeshes.get(parentKey);

			// mark current block as ready
			highResContainedMeshes.get(key).getC().set(true);

			updateLowResToHighResBlockMapping(
					parentKey,
					highResContainedMeshes,
					() -> renderListFilter.lowResParentBlockToHighResContainedMeshes.remove(parentKey)
				);
		}
		else
		{
			// this is a top-level block
			submitTasksForDescendants(key);
		}

		System.out.println("*** Mesh added, lowResParentBlockToHighResContainedMeshes size: " + renderListFilter.lowResParentBlockToHighResContainedMeshes.size() + " ***");
	}

	private synchronized void updateLowResToHighResBlockMapping(
			final ShapeKey<T> lowResKey,
			final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes,
			final Runnable removeKeyFromRenderListFilter)
	{
		// check if all descendants of the block are ready
		int numPendingBlocks = 0;
		for (final Triple<MeshView, Node, AtomicBoolean> value : highResContainedMeshes.values())
			if (value == null || !value.getC().get())
				++numPendingBlocks;

		if (numPendingBlocks == 0)
		{
			// all descendant blocks are ready, update the visibility of the meshes and remove the parent mesh
			for (final Triple<MeshView, Node, AtomicBoolean> containedValue : highResContainedMeshes.values())
			{
				if (containedValue.getA() != null)
					containedValue.getA().setVisible(true);

				if (containedValue.getB() != null)
					containedValue.getB().setVisible(true);
			}

			removeKeyFromRenderListFilter.run();
			meshesAndBlocks.remove(lowResKey);

			// submit tasks for contained blocks
			highResContainedMeshes.keySet().forEach(this::submitTasksForDescendants);
		}
		else
		{
			System.out.println(String.format("Parent block %s is not ready yet, number of pending blocks: %d", lowResKey, numPendingBlocks));
		}
	}

	private synchronized void submitTasksForDescendants(final ShapeKey<T> key)
	{
		final Set<ShapeKey<T>> descendants = Optional.ofNullable(renderListFilter.lowResParentBlockToHighResContainedMeshes.get(key)).map(val -> val.keySet()).orElse(null);
		if (descendants != null)
		{
			descendants.forEach(descendant -> {
				if (tasks.containsKey(descendant))
					submitTask(tasks.get(descendant));
			});
		}
	}

	/**
	 * Returns a set of the blocks to render and the distance from the camera to each block.
	 *
	 * @param blockTree
	 * @param preferredScaleIndex
	 * @param highestScaleIndex
	 * @param viewFrustum
	 * @param eyeToWorldTransform
	 * @return
	 */
	private Map<BlockTreeEntry, Double> getBlocksToRender(
			final RendererMetadata rendererMetadata,
			final int preferredScaleIndex,
			final int highestScaleIndex,
			final ViewFrustum viewFrustum,
			final AffineTransform3D eyeToWorldTransform)
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

		final ViewFrustumCulling[] viewFrustumCullingInSourceSpace = new ViewFrustumCulling[numScaleLevels];
		final double[] minMipmapPixelSize = new double[numScaleLevels];
		final double[] maxRelativeScaleFactors = new double[numScaleLevels];
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

		final Map<BlockTreeEntry, Double> blocksToRender = new HashMap<>();

		final LinkedHashSet<BlockTreeEntry> blocksQueue = new LinkedHashSet<>();

		// start with all blocks at the lowest resolution
		final CellGrid rendererGridAtLowestResolition = rendererMetadata.rendererGrids[rendererMetadata.rendererGrids.length - 1];
		for (final HashWrapper<Interval> sourceBlockAtLowestResolution : sourceBlocks[sourceBlocks.length - 1])
		{
			final long[] intersectingRendererBlockIndices = Grids.getIntersectingBlocks(sourceBlockAtLowestResolution.getData(), rendererGridAtLowestResolition);
			for (final long rendererBlockIndex : intersectingRendererBlockIndices)
				blocksQueue.add(new BlockTreeEntry(rendererBlockIndex, rendererMetadata.rendererGrids.length - 1, BlockTreeEntry.EMPTY));
		}

		while (!blocksQueue.isEmpty())
		{
			final Iterator<BlockTreeEntry> it = blocksQueue.iterator();
			final BlockTreeEntry blockEntry = it.next();
			it.remove();

			final Interval blockInterval = rendererMetadata.getBlockInterval(blockEntry);
			if (viewFrustumCullingInSourceSpace[blockEntry.scaleLevel].intersects(blockInterval))
			{
				final double distanceFromCamera = viewFrustumCullingInSourceSpace[blockEntry.scaleLevel].distanceFromCamera(blockInterval);
				final double screenSizeToViewPlaneRatio = viewFrustum.screenSizeToViewPlaneRatio(distanceFromCamera);
				final double screenPixelSize = screenSizeToViewPlaneRatio * minMipmapPixelSize[blockEntry.scaleLevel];
				LOG.debug("scaleIndex={}, screenSizeToViewPlaneRatio={}, screenPixelSize={}", blockEntry.scaleLevel, screenSizeToViewPlaneRatio, screenPixelSize);

				blocksToRender.put(blockEntry, distanceFromCamera);

				// check if needed to subdivide the block
				if (blockEntry.scaleLevel > highestScaleIndex && screenPixelSize > maxRelativeScaleFactors[preferredScaleIndex])
				{
					if (isInterrupted.get())
					{
						LOG.debug("Interrupted while building a list of blocks for rendering for label {}", identifier);
						break;
					}

					// figure out what source blocks at the next scale level intersect with the source block at the current scale level
					final CellGrid sourceNextLevelGrid = rendererMetadata.sourceGrids[blockEntry.scaleLevel - 1];
					final CellGrid rendererNextLevelGrid = rendererMetadata.rendererGrids[blockEntry.scaleLevel - 1];

					final double[] relativeScales = new double[3];
					Arrays.setAll(relativeScales, d -> rendererMetadata.scales[blockEntry.scaleLevel][d] / rendererMetadata.scales[blockEntry.scaleLevel - 1][d]);

					final double[] nextScaleLevelBlockMin = new double[3], nextScaleLevelBlockMax = new double[3];
					for (int d = 0; d < 3; ++d)
					{
						nextScaleLevelBlockMin[d] = blockInterval.min(d) * relativeScales[d];
						nextScaleLevelBlockMax[d] = (blockInterval.max(d) + 1) * relativeScales[d] - 1;
					}
					final Interval nextLevelBlockInterval = Intervals.smallestContainingInterval(new FinalRealInterval(nextScaleLevelBlockMin, nextScaleLevelBlockMax));

					// find out what blocks at higher resolution intersect with this block
					final long[] intersectingNextLevelBlockIndices = Grids.getIntersectingBlocks(nextLevelBlockInterval, rendererNextLevelGrid);
					for (final long intersectingNextLevelBlockIndex : intersectingNextLevelBlockIndices)
					{
						final BlockTreeEntry nextLevelBlockEntry = new BlockTreeEntry(intersectingNextLevelBlockIndex, blockEntry.scaleLevel - 1, blockEntry.index);
						final long[] intersectingSourceNextLevelBlockIndices = Grids.getIntersectingBlocks(rendererMetadata.getBlockInterval(nextLevelBlockEntry), sourceNextLevelGrid);
						// check if there is a source block that intersects with the target block at higher resolution that is currently being considered
						for (final long intersectingSourceNextLevelBlockIndex : intersectingSourceNextLevelBlockIndices)
						{
							final HashWrapper<Interval> intersectingSourceNextLevelBlock = HashWrapper.interval(Grids.getCellInterval(sourceNextLevelGrid, intersectingSourceNextLevelBlockIndex));
							if (sourceBlocks[blockEntry.scaleLevel - 1].contains(intersectingSourceNextLevelBlock))
							{
								blocksQueue.add(nextLevelBlockEntry);
								break;
							}
						}
					}
				}
			}
		}

		final Map<Integer, Integer> scaleIndexToNumBlocks = new TreeMap<>();
		for (final BlockTreeEntry blockEntry : blocksToRender.keySet())
			scaleIndexToNumBlocks.put(blockEntry.scaleLevel, scaleIndexToNumBlocks.getOrDefault(blockEntry.scaleLevel, 0) + 1);
		LOG.debug("Label ID {}: ", identifier, scaleIndexToNumBlocks);

		return blocksToRender;
	}


	private final class RenderListFilter
	{
		/**
		 * Temporary storage for new rendered high-res blocks that will be added onto the scene in bulk once their low-res parent block can be fully replaced.
		 * Represented as a mapping from a low-res parent block to a set of contained high-res blocks and generated meshes for them.
		 */
		final Map<ShapeKey<T>, Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>>> lowResParentBlockToHighResContainedMeshes = new HashMap<>();

		/**
		 * Two-directional mapping between block tree entries which are used for building the render list
		 * and shape keys which are used for identifying the blocks in the scene.
		 */
		final BiMap<ShapeKey<T>, BlockTreeEntry> allKeysAndEntriesToRender = HashBiMap.create();

		/**
		 * Helper mapping from block index to its metadata for each scale level. Used to query metadata for parent blocks.
		 */
		final TLongObjectHashMap<BlockTreeEntry>[] blockIndicesToEntries;

		@SuppressWarnings("unchecked")
		RenderListFilter(final int numScaleLevels)
		{
			blockIndicesToEntries = new TLongObjectHashMap[numScaleLevels];
			Arrays.setAll(blockIndicesToEntries, i -> new TLongObjectHashMap<>());
		}

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
				final RendererMetadata rendererMetadata,
				final Set<BlockTreeEntry> blocksToRender,
				final int simplificationIterations,
				final double smoothingLambda,
				final int smoothingIterations)
		{
			// blocksToRender will be used to store filtered set, copy the full set to separate collections
			Arrays.stream(blockIndicesToEntries).forEach(map -> map.clear());
			for (final BlockTreeEntry blockEntry : blocksToRender)
				blockIndicesToEntries[blockEntry.scaleLevel].put(blockEntry.index, blockEntry);

			allKeysAndEntriesToRender.clear();
			for (final BlockTreeEntry blockEntry : blocksToRender)
			{
				final ShapeKey<T> keyToRender = createShapeKey(
						rendererMetadata,
						blockEntry,
						simplificationIterations,
						smoothingLambda,
						smoothingIterations
					);
				allKeysAndEntriesToRender.put(keyToRender, blockEntry);
			}

			// Remove all pending ancestors for which a descendant already exists in the scene or has been added to the task queue
			// Additionally, remove all pending blocks that are already scheduled for rendering
			final Set<BlockTreeEntry> pendingLowResParentBlocksToIgnore = new HashSet<>();
			for (final Entry<ShapeKey<T>, BlockTreeEntry> pendingKeyAndEntry : allKeysAndEntriesToRender.entrySet())
			{
				if (meshesAndBlocks.containsKey(pendingKeyAndEntry.getKey()) || tasks.containsKey(pendingKeyAndEntry.getKey()))
				{
					// if this block is already present in the scene, ignore this block and all parent blocks of this block
					// (there is no point in rendering a lower-resolution version of the requested block that is already displayed)
					BlockTreeEntry entry = pendingKeyAndEntry.getValue();
					while (entry != null)
					{
						pendingLowResParentBlocksToIgnore.add(entry);
						entry = getParentEntry(entry);
					}
				}
			}
			blocksToRender.removeAll(pendingLowResParentBlocksToIgnore);

			// Remove blocks from the scene that are not needed anymore
			final int blocksBeforeRemoving = meshesAndBlocks.size();
			meshesAndBlocks.keySet().retainAll(allKeysAndEntriesToRender.keySet());
			System.out.println("Blocks in the scene before removing unneeded blocks: " + blocksBeforeRemoving + ",  after: " + meshesAndBlocks.size());

			// Interrupt running tasks for blocks that are not needed anymore
			final List<ShapeKey<T>> taskKeysToInterrupt = tasks.keySet().stream()
				.filter(key -> !allKeysAndEntriesToRender.containsKey(key))
				.collect(Collectors.toList());
			for (final ShapeKey<T> taskKey : taskKeysToInterrupt)
			{
				getMeshes[taskKey.scaleIndex()].interruptFor(taskKey);
				interruptTask(tasks.remove(taskKey));
			}

			// Remove mapping for parent blocks that are not needed anymore
			lowResParentBlockToHighResContainedMeshes.keySet().retainAll(allKeysAndEntriesToRender.keySet());
			for (final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes : lowResParentBlockToHighResContainedMeshes.values())
				highResContainedMeshes.keySet().retainAll(allKeysAndEntriesToRender.keySet());

			// Insert new mappings for blocks that are not there yet
			for (final BlockTreeEntry blockEntry : blocksToRender)
			{
				final ShapeKey<T> blockKey = allKeysAndEntriesToRender.inverse().get(blockEntry);
				final BlockTreeEntry parentEntry = getParentEntry(blockEntry);
				if (parentEntry != null)
				{
					final ShapeKey<T> parentKey = allKeysAndEntriesToRender.inverse().get(parentEntry);
					if (!lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
						lowResParentBlockToHighResContainedMeshes.put(parentKey, new HashMap<>());
					lowResParentBlockToHighResContainedMeshes.get(parentKey).put(blockKey, null);
				}
			}
		}
	}

	private ShapeKey<T> createShapeKey(
			final RendererMetadata rendererMetadata,
			final BlockTreeEntry blockEntry,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations)
	{
		final Interval blockInterval = rendererMetadata.getBlockInterval(blockEntry);
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
