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

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
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
import gnu.trove.map.TLongObjectMap;
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
		final AtomicBoolean isCompleted = new AtomicBoolean();
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

	private final IntegerProperty numTasks;

	private final IntegerProperty numCompletedTasks;

	private final int rendererBlockSize;

	private final int numScaleLevels;

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	private final ObjectProperty<SceneUpdateJobParameters> sceneJobUpdateParametersProperty = new SimpleObjectProperty<>();

	private BlockTree blockTree = null;

	/**
	 * Temporary storage for new rendered high-res blocks that will be added onto the scene in bulk once their low-res parent block can be fully replaced.
	 * Represented as a mapping from a low-res parent block to a set of contained high-res blocks and generated meshes for them.
	 */
	private final Map<ShapeKey<T>, Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>>> lowResParentBlockToHighResContainedMeshes = new HashMap<>();

	public MeshGeneratorJobManager(
			final DataSource<?, ?> source,
			final T identifier,
			final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks,
			final InterruptibleFunction<T, Interval[]>[] getBlockLists,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes,
			final ExecutorService manager,
			final PriorityExecutorService<MeshWorkerPriority> workers,
			final IntegerProperty numTasks,
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
		this.numTasks = numTasks;
		this.numCompletedTasks = numCompletedTasks;
		this.rendererBlockSize = rendererBlockSize;
		this.numScaleLevels = source.getNumMipmapLevels();
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
		if (isInterrupted.get())
			return;

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

	private final class BlockTree
	{
		/**
		 * Two-directional mapping between block tree entries which are used for building the render list
		 * and shape keys which are used for identifying the blocks in the scene.
		 */
		final BiMap<ShapeKey<T>, BlockTreeEntry> keysAndEntries;

		/**
		 * Helper mapping from block index to its metadata for each scale level. Used to query metadata for parent blocks.
		 */
		final TLongObjectMap<BlockTreeEntry>[] indicesToEntries;

		@SuppressWarnings("unchecked")
		BlockTree(
				final RendererMetadata rendererMetadata,
				final Set<BlockTreeEntry> blockList,
				final SceneUpdateJobParameters params)
		{
			// Populate shape keys
			keysAndEntries = HashBiMap.create();
			for (final BlockTreeEntry entry : blockList)
			{
				final ShapeKey<T> key = createShapeKey(rendererMetadata, entry, params);
				keysAndEntries.put(key, entry);
			}

			// Populate indices to entries
			indicesToEntries = new TLongObjectMap[numScaleLevels];
			Arrays.setAll(indicesToEntries, i -> new TLongObjectHashMap<>());
			for (final BlockTreeEntry entry : blockList)
				indicesToEntries[entry.scaleLevel].put(entry.index, entry);
		}

		BlockTreeEntry getParentEntry(final BlockTreeEntry entry)
		{
			return entry.scaleLevel < numScaleLevels - 1 ? indicesToEntries[entry.scaleLevel + 1].get(entry.parent) : null;
		}
	}


	private synchronized void updateScene()
	{
		LOG.debug("ID {}: scene update initiated", identifier);

		final SceneUpdateJobParameters params;
		synchronized (sceneJobUpdateParametersProperty)
		{
			params = sceneJobUpdateParametersProperty.get();
			sceneJobUpdateParametersProperty.set(null);
		}

		if (isInterrupted.get())
			return;

		final RendererMetadata rendererMetadata = new RendererMetadata();

		final Map<BlockTreeEntry, Double> blocksToRender = getBlocksToRender(
				rendererMetadata,
				params.preferredScaleIndex,
				params.highestScaleIndex,
				params.viewFrustum,
				params.eyeToWorldTransform
			);

		blockTree = new BlockTree(rendererMetadata, blocksToRender.keySet(), params);

		final int numTotalBlocksToRender = blocksToRender.size();
		filterBlocks(blockTree, blocksToRender.keySet()); // blocksToRender this will be modified by this call
		final int numActualBlocksToRender = blocksToRender.size();

		// calculate how many tasks are already completed
		final int numIncompletedTasks = (int) tasks.values().stream().filter(task -> !task.isCompleted.get()).count();
		int numPendingDescendantsTotal = 0;
		for (final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes : lowResParentBlockToHighResContainedMeshes.values())
			for (final Entry<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> entry : highResContainedMeshes.entrySet())
				if (!blocksToRender.containsKey(blockTree.keysAndEntries.get(entry.getKey())))
					if (entry.getValue() == null || !entry.getValue().getC().get())
						++numPendingDescendantsTotal;
		numTasks.set(numTotalBlocksToRender);
		numCompletedTasks.set(numTotalBlocksToRender - numActualBlocksToRender - numIncompletedTasks - numPendingDescendantsTotal);
		LOG.debug("ID {}: numTasks={}, numCompletedTasks={}, numBlocksToRender={}, numIncompletedTasks={}, numPendingDescendantsTotal={}", identifier, numTasks.get(), numCompletedTasks.get(), blocksToRender.size(), numIncompletedTasks, numPendingDescendantsTotal);

		if (blocksToRender.isEmpty())
		{
			LOG.debug("No blocks need to be rendered");
			return;
		}

		LOG.debug("Generating meshes for {} blocks for id {}.", blocksToRender.size(), identifier);
		for (final Entry<BlockTreeEntry, Double> blockEntryAndDistance : blocksToRender.entrySet())
		{
			final BlockTreeEntry blockEntry = blockEntryAndDistance.getKey();
			final ShapeKey<T> key = blockTree.keysAndEntries.inverse().get(blockEntry);

			final Runnable taskRunnable = () ->
			{
				final String initialName = Thread.currentThread().getName();
				try
				{
					Thread.currentThread().setName(initialName + " -- generating mesh: " + key);

					final Task currentTask;
					synchronized (this)
					{
						currentTask = tasks.get(key);
					}

					if (currentTask == null)
					{
						LOG.debug("Task for key {} has been removed", key);
						return;
					}

					final BooleanSupplier isTaskCanceled = () -> isInterrupted.get() || currentTask.future.isCancelled();
					if (!isTaskCanceled.getAsBoolean())
					{
						LOG.debug("Executing task for key {} at distance {}, scale level {}", key, currentTask.priority.distanceFromCamera, currentTask.priority.scaleLevel);
						final Pair<float[], float[]> verticesAndNormals;
						try
						{
							verticesAndNormals = getMeshes[key.scaleIndex()].apply(key);
						}
						catch (final Exception e)
						{
							LOG.debug("Was not able to retrieve mesh for key {}: {}", key, e);
							synchronized (this)
							{
								if (!isTaskCanceled.getAsBoolean())
									tasks.remove(key);
							}
							return;
						}

						if (verticesAndNormals != null)
						{
							synchronized (this)
							{
								if (!isTaskCanceled.getAsBoolean())
								{
									currentTask.isCompleted.set(true);
									onMeshGenerated(key, verticesAndNormals);
								}
							}
						}
					}
				}
				finally
				{
					Thread.currentThread().setName(initialName);
				}
			};

			final MeshWorkerPriority taskPriority = new MeshWorkerPriority(blockEntryAndDistance.getValue(), blockEntry.scaleLevel);
			final Task task = new Task(taskRunnable, taskPriority);

			assert !tasks.containsKey(key);
			tasks.put(key, task);
		}


		// Update the visibility and the task lists because the parent->block mapping may have changed
		for (final Iterator<Entry<ShapeKey<T>, Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>>>> it = lowResParentBlockToHighResContainedMeshes.entrySet().iterator(); it.hasNext();)
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

		for (final BlockTreeEntry blockEntry : blocksToRender.keySet())
		{
			// submit task immediately if top-level block (in the current set, not necessarily the coarsest scale level)
			final BlockTreeEntry parentEntry = blockTree.getParentEntry(blockEntry);
			if (!blocksToRender.containsKey(parentEntry))
			{
				final ShapeKey<T> key = blockTree.keysAndEntries.inverse().get(blockEntry);
				LOG.debug("ID {}: submitting task for {}", identifier, key);
				submitTask(tasks.get(key));
			}
		}
	}

	private synchronized void submitTask(final Task task)
	{
		if (task.future == null && !task.isCompleted.get())
			task.future = workers.submit(task.task, task.priority);
	}

	private synchronized void interruptTask(final Task task)
	{
		if (task.future != null && !task.isCompleted.get())
			task.future.cancel(true);
	}

	private synchronized void onMeshGenerated(final ShapeKey<T> key, final Pair<float[], float[]> verticesAndNormals)
	{
		LOG.debug("ID {}: block {} has been generated", identifier, key);

		final boolean nonEmptyMesh = Math.max(verticesAndNormals.getA().length, verticesAndNormals.getB().length) > 0;
		final MeshView mv = nonEmptyMesh ? makeMeshView(verticesAndNormals) : null;
		final Node blockShape = nonEmptyMesh ? createBlockShape(key) : null;
		final Pair<MeshView, Node> meshAndBlock = new ValuePair<>(mv, blockShape);
		LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);

		final BlockTreeEntry entry = blockTree.keysAndEntries.get(key);
		final BlockTreeEntry parentEntry = blockTree.getParentEntry(entry);
		final ShapeKey<T> parentKey = blockTree.keysAndEntries.inverse().get(parentEntry);

		if (lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
		{
			final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes = lowResParentBlockToHighResContainedMeshes.get(parentKey);
			if (!highResContainedMeshes.containsKey(key))
				throw new RuntimeException("Descendant not found!");

			// Put the mesh in the map. The last value in the triple specifies that the mesh is not added onto the scene yet,
			// and once it's there, this will be changed to true in onMeshAdded() callback
			highResContainedMeshes.put(key, new ValueTriple<>(mv, blockShape, new AtomicBoolean(false)));

			// hide the mesh until all descendants of the parent block are ready, this is handled in onMeshAdded() callback
			if (nonEmptyMesh)
				setMeshVisibility(meshAndBlock, false);
		}

		meshesAndBlocks.put(key, meshAndBlock);
	}

	public synchronized void onMeshAdded(final ShapeKey<T> key)
	{
		// check if this key is still relevant
		if (!tasks.containsKey(key) || !tasks.get(key).isCompleted.get())
			return;

		LOG.debug("ID {}: mesh for block {} has been added onto the scene", identifier, key);

		tasks.remove(key);
		numCompletedTasks.set(numCompletedTasks.get() + 1);

		final BlockTreeEntry entry = blockTree.keysAndEntries.get(key);
		final BlockTreeEntry parentEntry = blockTree.getParentEntry(entry);
		final ShapeKey<T> parentKey = blockTree.keysAndEntries.inverse().get(parentEntry);

		if (lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
		{
			final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes = lowResParentBlockToHighResContainedMeshes.get(parentKey);

			// mark current block as ready
			highResContainedMeshes.get(key).getC().set(true);

			updateLowResToHighResBlockMapping(
					parentKey,
					highResContainedMeshes,
					() -> lowResParentBlockToHighResContainedMeshes.remove(parentKey)
				);
		}
		else
		{
			// this is a top-level block
			submitTasksForDescendants(key);
		}
	}

	private synchronized void updateLowResToHighResBlockMapping(
			final ShapeKey<T> lowResKey,
			final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes,
			final Runnable removeKeyFromRenderListFilter)
	{
		boolean allDescendantsReady = true;
		for (final Triple<MeshView, Node, AtomicBoolean> value : highResContainedMeshes.values())
		{
			if (value == null || !value.getC().get())
			{
				allDescendantsReady = false;
				break;
			}
		}

		if (allDescendantsReady)
		{
			// all descendant blocks are ready, update the visibility of the meshes and remove the parent mesh
			for (final Triple<MeshView, Node, AtomicBoolean> containedValue : highResContainedMeshes.values())
				setMeshVisibility(new ValuePair<>(containedValue.getA(), containedValue.getB()), true);

			removeKeyFromRenderListFilter.run();
			meshesAndBlocks.remove(lowResKey);

			// submit tasks for contained blocks
			highResContainedMeshes.keySet().forEach(this::submitTasksForDescendants);
		}
	}

	private synchronized void submitTasksForDescendants(final ShapeKey<T> key)
	{
		final Set<ShapeKey<T>> descendants = Optional.ofNullable(lowResParentBlockToHighResContainedMeshes.get(key)).map(val -> val.keySet()).orElse(null);
		if (descendants != null)
		{
			descendants.forEach(descendant -> {
				if (tasks.containsKey(descendant))
					submitTask(tasks.get(descendant));
			});
			LOG.debug("ID {}: block {} is fully ready, submitted {} tasks for its descendants", identifier, key, descendants.size());
		}
	}

	private void setMeshVisibility(final Pair<MeshView, Node> meshAndBlock, final boolean isVisible)
	{
		InvokeOnJavaFXApplicationThread.invoke(() ->
		{
			if (meshAndBlock.getA() != null)
				meshAndBlock.getA().setVisible(isVisible);

			if (meshAndBlock.getB() != null)
				meshAndBlock.getB().setVisible(isVisible);
		});
	}

	/**
	 * Returns a set of the blocks to render and the distance from the camera to each block.
	 *
	 * @param rendererMetadata
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
			else if (blockEntry.scaleLevel == numScaleLevels - 1)
			{
				// always render all blocks at lowest resolution even if they are currently outside the screen
				blocksToRender.put(blockEntry, Double.POSITIVE_INFINITY);
			}
		}

		final Map<Integer, Integer> scaleIndexToNumBlocks = new TreeMap<>();
		for (final BlockTreeEntry blockEntry : blocksToRender.keySet())
			scaleIndexToNumBlocks.put(blockEntry.scaleLevel, scaleIndexToNumBlocks.getOrDefault(blockEntry.scaleLevel, 0) + 1);
		LOG.debug("ID {} blocks: {}", identifier, scaleIndexToNumBlocks);

		return blocksToRender;
	}


	/**
	 * Intersects the current state with the new requested set of blocks for rendering.
	 * Updates meshes in the scene, running tasks, lists of blocks for postponed removal.
	 * Modifies {@code blocksToRender} with the intersected set of blocks that still need to be rendered.
	 *
	 * @param blockTree
	 * @param blocksToRender
	 * 			this will be modified
	 */
	private void filterBlocks(final BlockTree blockTree, final Set<BlockTreeEntry> blocksToRender)
	{
		// Remove all pending ancestors for which a descendant already exists in the scene or has been added to the task queue
		// Additionally, remove all pending blocks that are already scheduled for rendering
		final Set<BlockTreeEntry> pendingLowResParentBlocksToIgnore = new HashSet<>();
		for (final Entry<ShapeKey<T>, BlockTreeEntry> pendingKeyAndEntry : blockTree.keysAndEntries.entrySet())
		{
			if (meshesAndBlocks.containsKey(pendingKeyAndEntry.getKey()) || tasks.containsKey(pendingKeyAndEntry.getKey()))
			{
				// if this block is already present in the scene, ignore this block and all parent blocks of this block
				// (there is no point in rendering a lower-resolution version of the requested block that is already displayed)
				BlockTreeEntry entry = pendingKeyAndEntry.getValue();
				while (entry != null)
				{
					pendingLowResParentBlocksToIgnore.add(entry);
					entry = blockTree.getParentEntry(entry);
				}
			}
		}
		blocksToRender.removeAll(pendingLowResParentBlocksToIgnore);

		// Remove blocks from the scene that are not needed anymore
		meshesAndBlocks.keySet().retainAll(blockTree.keysAndEntries.keySet());

		// Interrupt running tasks for blocks that are not needed anymore
		final List<ShapeKey<T>> taskKeysToInterrupt = tasks.keySet().stream()
			.filter(key -> !blockTree.keysAndEntries.containsKey(key))
			.collect(Collectors.toList());
		for (final ShapeKey<T> taskKey : taskKeysToInterrupt)
		{
			getMeshes[taskKey.scaleIndex()].interruptFor(taskKey);
			interruptTask(tasks.remove(taskKey));
		}

		// Remove mapping for parent blocks that are not needed anymore
		lowResParentBlockToHighResContainedMeshes.keySet().retainAll(blockTree.keysAndEntries.keySet());
		for (final Map<ShapeKey<T>, Triple<MeshView, Node, AtomicBoolean>> highResContainedMeshes : lowResParentBlockToHighResContainedMeshes.values())
			highResContainedMeshes.keySet().retainAll(blockTree.keysAndEntries.keySet());

		// Insert new mappings for blocks that are not there yet
		for (final BlockTreeEntry blockEntry : blocksToRender)
		{
			final ShapeKey<T> blockKey = blockTree.keysAndEntries.inverse().get(blockEntry);
			final BlockTreeEntry parentEntry = blockTree.getParentEntry(blockEntry);
			if (parentEntry != null)
			{
				final ShapeKey<T> parentKey = blockTree.keysAndEntries.inverse().get(parentEntry);
				if (!lowResParentBlockToHighResContainedMeshes.containsKey(parentKey))
					lowResParentBlockToHighResContainedMeshes.put(parentKey, new HashMap<>());
				lowResParentBlockToHighResContainedMeshes.get(parentKey).put(blockKey, null);
			}
		}
	}

	private ShapeKey<T> createShapeKey(
			final RendererMetadata rendererMetadata,
			final BlockTreeEntry blockEntry,
			final SceneUpdateJobParameters params)
	{
		final Interval blockInterval = rendererMetadata.getBlockInterval(blockEntry);
		return new ShapeKey<>(
				identifier,
				blockEntry.scaleLevel,
				params.simplificationIterations,
				params.smoothingLambda,
				params.smoothingIterations,
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
				// clamp the block size, but do not limit the block size in Z to allow for closer to isotropic blocks
				final int clampedBlockSize = Math.max(
						d == 2 ? 1 : Viewer3DConfig.RENDERER_BLOCK_SIZE_MIN_VALUE, Math.min(
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
