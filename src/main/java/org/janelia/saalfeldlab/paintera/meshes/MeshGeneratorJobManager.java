package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

import bdv.util.Affine3DHelpers;
import eu.mihosoft.jcsg.ext.openjfx.shape3d.PolygonMeshView;
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
import net.imglib2.util.ValuePair;

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
		final long tag;
		Future<?> future;

		Task(final Runnable task, final MeshWorkerPriority priority, final long tag)
		{
			this.task = task;
			this.priority = priority;
			this.tag = tag;
		}
	}

	private static enum BlockTreeNodeState
	{
		/**
		 * Mesh for the block is displayed normally.
		 */
		VISIBLE,

		/**
		 * Mesh for the block has been generated, but has not been added onto the scene yet.
		 */
		RENDERED,

		/**
		 * Mesh for the block has been generated and added onto the scene, but is currently hidden
		 * because there are pending blocks with the same parent node that is currently visible.
		 *
		 * This state is used when increasing the resolution for a block that is currently visible:
		 *
		 *   --------------------
		 *  |       Visible      |
		 *   --------------------
		 *      |            |
		 *      |            |
		 *   --------   ---------
		 *  | Hidden | | Pending |
		 *   --------   ---------
		 *
		 * Once the pending block is rendered and added onto the scene, the parent block will be transitioned into the REMOVED state,
		 * and the higher-resolution blocks will be transitioned into the VISIBLE state.
		 */
		HIDDEN,

		/**
		 * Mesh for the block has been replaced by a set of higher-resolution blocks.
		 */
		REMOVED,

		/**
		 * Mesh for the blocks needs to be generated.
		 * This state is used for blocks that are already being generated and for those that are not yet started or scheduled.
		 */
		PENDING
	}

	private final class BlockTreeNode
	{
		final ShapeKey<T> parentKey;
		final Set<ShapeKey<T>> children;
		BlockTreeNodeState state = BlockTreeNodeState.PENDING; // initial state is always PENDING

		BlockTreeNode(final ShapeKey<T> parentKey, final Set<ShapeKey<T>> children)
		{
			this.parentKey = parentKey;
			this.children = children;
		}

		@Override
		public String toString()
		{
			return String.format("[state=%s, numChildren=%d]", state, children.size());
		}
	}

	private final class BlockTree
	{
		final Map<ShapeKey<T>, BlockTreeNode> nodes = new HashMap<>();
	}

	private final class BlocksToRender
	{
		final BlockTree blockTree;
		final Map<ShapeKey<T>, Double> renderListWithDistances;

		BlocksToRender(final BlockTree blockTree, final Map<ShapeKey<T>, Double> renderListWithDistances)
		{
			this.blockTree = blockTree;
			this.renderListWithDistances = renderListWithDistances;
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

	private final BlockTree blockTree = new BlockTree();

	private final AtomicLong sceneUpdateCounter = new AtomicLong();

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


	private synchronized void updateScene()
	{
		LOG.debug("ID {}: scene update initiated", identifier);
		sceneUpdateCounter.incrementAndGet();

		final SceneUpdateJobParameters params;
		synchronized (sceneJobUpdateParametersProperty)
		{
			params = sceneJobUpdateParametersProperty.get();
			sceneJobUpdateParametersProperty.set(null);
		}

		if (isInterrupted.get())
			return;

		// build new block tree and update the current tree
		final BlocksToRender blocksToRender = getBlocksToRender(params);
		final Map<ShapeKey<T>, Double> newBlockDistancesFromCamera = new HashMap<>(blocksToRender.renderListWithDistances); // store a copy of all new distances
		updateBlockTree(blocksToRender); // blocksToRender will be filtered by this call

		// remove blocks from the scene that are not in the updated tree
		meshesAndBlocks.keySet().retainAll(blockTree.nodes.keySet());

		// stop tasks for blocks that are not in the updated tree
		final List<ShapeKey<T>> taskKeysToInterrupt = tasks.keySet().stream()
				.filter(key -> !blockTree.nodes.containsKey(key))
				.collect(Collectors.toList());
		for (final ShapeKey<T> taskKey : taskKeysToInterrupt)
		{
			interruptTask(tasks.remove(taskKey));
			getMeshes[taskKey.scaleIndex()].interruptFor(taskKey);
		}

		// update distances for all existing tasks that have not been scheduled yet
		for (final Entry<ShapeKey<T>, Task> entry : tasks.entrySet())
		{
			if (newBlockDistancesFromCamera.containsKey(entry.getKey()) && entry.getValue().future == null)
			{
				final MeshWorkerPriority newTaskPriority = new MeshWorkerPriority(newBlockDistancesFromCamera.get(entry.getKey()), entry.getKey().scaleIndex());
				final Task newTask = new Task(entry.getValue().task, newTaskPriority, entry.getValue().tag);
				entry.setValue(newTask);
			}
		}

		// calculate how many tasks are already completed
		final int numTotalBlocksToRender = blocksToRender.blockTree.nodes.size();
		final int numActualBlocksToRender = blocksToRender.renderListWithDistances.size();
		numTasks.set(numTotalBlocksToRender);
		numCompletedTasks.set(numTotalBlocksToRender - numActualBlocksToRender - tasks.size());
		LOG.debug("ID {}: numTasks={}, numCompletedTasks={}, numActualBlocksToRender={}", identifier, numTasks.get(), numCompletedTasks.get(), numActualBlocksToRender);

		// create tasks for blocks that still need to be generated
		LOG.debug("Creating mesh generation tasks for {} blocks for id {}.", numActualBlocksToRender, identifier);
		blocksToRender.renderListWithDistances.forEach(this::createTask);

		// Update the meshes according to the new tree node states and submit necessary tasks
		final Collection<ShapeKey<T>> topLevelKeys = blockTree.nodes.keySet().stream().filter(key -> blockTree.nodes.get(key).parentKey == null).collect(Collectors.toList());
		final Queue<ShapeKey<T>> keyQueue = new ArrayDeque<>(topLevelKeys);
		while (!keyQueue.isEmpty())
		{
			final ShapeKey<T> key = keyQueue.poll();
			final BlockTreeNode treeNode = blockTree.nodes.get(key);
			keyQueue.addAll(treeNode.children);

			if (treeNode.parentKey == null && treeNode.state == BlockTreeNodeState.PENDING)
			{
				// Top-level block
				submitTask(tasks.get(key));
			}
			else if (treeNode.state == BlockTreeNodeState.VISIBLE)
			{
				final boolean areAllHigherResBlocksReady = !treeNode.children.isEmpty() && treeNode.children.stream().allMatch(childKey -> blockTree.nodes.get(childKey).state == BlockTreeNodeState.HIDDEN);
				if (areAllHigherResBlocksReady)
				{
					// All children blocks in this block are ready, remove it and submit the tasks for next-level contained blocks if any
					treeNode.children.forEach(childKey -> {
						blockTree.nodes.get(childKey).state = BlockTreeNodeState.VISIBLE;
						setMeshVisibility(meshesAndBlocks.get(childKey), true);
					});

					treeNode.state = BlockTreeNodeState.REMOVED;
					assert !tasks.containsKey(key);
					meshesAndBlocks.remove(key);

					treeNode.children.forEach(this::submitTasksForChildren);
				}
				else
				{
					submitTasksForChildren(key);
				}
			}
			else if (treeNode.state == BlockTreeNodeState.REMOVED)
			{
				submitTasksForChildren(key);
			}
		}
	}

	private synchronized void createTask(final ShapeKey<T> key, final double distanceFromCamera)
	{
		final long tag = sceneUpdateCounter.get();
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
					if (currentTask == null || currentTask.tag != tag)
					{
						LOG.debug("Task for key {} has been removed", key);
						return;
					}
					assert currentTask.future != null;
				}

				final BooleanSupplier isTaskCanceled = () -> isInterrupted.get() || currentTask.future.isCancelled() || Thread.currentThread().isInterrupted();
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
			catch (final Exception e)
			{
				e.printStackTrace();
			}
			finally
			{
				Thread.currentThread().setName(initialName);
			}
		};

		final MeshWorkerPriority taskPriority = new MeshWorkerPriority(distanceFromCamera, key.scaleIndex());
		final Task task = new Task(taskRunnable, taskPriority, tag);

		assert !tasks.containsKey(key);
		tasks.put(key, task);
	}

	private synchronized void submitTask(final Task task)
	{
		if (task != null && task.future == null && !task.isCompleted.get())
			task.future = workers.submit(task.task, task.priority);
	}

	private synchronized void interruptTask(final Task task)
	{
		if (task != null && task.future != null && !task.isCompleted.get())
			task.future.cancel(true);
	}

	private synchronized void onMeshGenerated(final ShapeKey<T> key, final Pair<float[], float[]> verticesAndNormals)
	{
		assert blockTree.nodes.containsKey(key);
		assert tasks.containsKey(key);
		assert !meshesAndBlocks.containsKey(key);
		LOG.debug("ID {}: block {} has been generated", identifier, key);

		final boolean nonEmptyMesh = Math.max(verticesAndNormals.getA().length, verticesAndNormals.getB().length) > 0;
		final MeshView mv = nonEmptyMesh ? makeMeshView(verticesAndNormals) : null;
		final Node blockShape = nonEmptyMesh ? createBlockShape(key) : null;
		final Pair<MeshView, Node> meshAndBlock = new ValuePair<>(mv, blockShape);
		LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);

		final BlockTreeNode treeNode = blockTree.nodes.get(key);
		treeNode.state = BlockTreeNodeState.RENDERED;

		if (treeNode.parentKey != null)
			assert blockTree.nodes.containsKey(treeNode.parentKey);
		final boolean isParentBlockVisible = treeNode.parentKey != null && blockTree.nodes.get(treeNode.parentKey).state == BlockTreeNodeState.VISIBLE;

		if (isParentBlockVisible)
		{
			assert meshesAndBlocks.containsKey(treeNode.parentKey);
			setMeshVisibility(meshAndBlock, false);
		}

		meshesAndBlocks.put(key, meshAndBlock);
	}

	public synchronized boolean taskExists(final ShapeKey<T> key)
	{
		return tasks.containsKey(key);
	}

	public synchronized long getBlockTag(final ShapeKey<T> key)
	{
		assert taskExists(key);
		return tasks.get(key).tag;
	}

	public synchronized void onMeshAdded(final ShapeKey<T> key, final long tag)
	{
		// Check if this block is still relevant.
		// The tag value is used to ensure that the block is actually relevant. Even if the task for the same key exists,
		// it might have been removed and created again, so the added block actually needs to be ignored.
		if (!tasks.containsKey(key) || !tasks.get(key).isCompleted.get() || tasks.get(key).tag != tag)
		{
			LOG.debug("ID {}: the added mesh for block {} is not relevant anymore", identifier, key);
			return;
		}

		assert blockTree.nodes.containsKey(key);
		assert meshesAndBlocks.containsKey(key);
		LOG.debug("ID {}: mesh for block {} has been added onto the scene", identifier, key);

		tasks.remove(key);
		numCompletedTasks.set(numCompletedTasks.get() + 1);

		final BlockTreeNode treeNode = blockTree.nodes.get(key);
		assert treeNode.state == BlockTreeNodeState.RENDERED;

		if (treeNode.parentKey != null)
			assert blockTree.nodes.containsKey(treeNode.parentKey);
		final boolean isParentBlockVisible = treeNode.parentKey != null && blockTree.nodes.get(treeNode.parentKey).state == BlockTreeNodeState.VISIBLE;

		if (isParentBlockVisible)
		{
			assert meshesAndBlocks.containsKey(treeNode.parentKey);

			// check if all children of the parent block are ready, and if so, update their visibility and remove the parent block
			final BlockTreeNode parentTreeNode = blockTree.nodes.get(treeNode.parentKey);
			treeNode.state = BlockTreeNodeState.HIDDEN;
			final boolean areAllChildrenReady = parentTreeNode.children.stream().map(blockTree.nodes::get).allMatch(childTreeNode -> childTreeNode.state == BlockTreeNodeState.HIDDEN);
			if (areAllChildrenReady)
			{
				parentTreeNode.children.forEach(childKey -> {
					blockTree.nodes.get(childKey).state = BlockTreeNodeState.VISIBLE;
					setMeshVisibility(meshesAndBlocks.get(childKey), true);
				});

				parentTreeNode.state = BlockTreeNodeState.REMOVED;
				assert !tasks.containsKey(treeNode.parentKey);
				meshesAndBlocks.remove(treeNode.parentKey);

				// Submit tasks for next-level contained blocks
				parentTreeNode.children.forEach(this::submitTasksForChildren);
			}
		}
		else
		{
			// Update the visibility of this block
			treeNode.state = BlockTreeNodeState.VISIBLE;
			setMeshVisibility(meshesAndBlocks.get(key), true);

			// Remove all children nodes that are not needed anymore: this is the case when resolution for the block is decreased,
			// and a set of higher-res blocks needs to be replaced with the single low-res block
			final Queue<ShapeKey<T>> childrenQueue = new ArrayDeque<>(treeNode.children);
			while (!childrenQueue.isEmpty())
			{
				final ShapeKey<T> childKey = childrenQueue.poll();
				final BlockTreeNode childNode = blockTree.nodes.get(childKey);
				final boolean removingEntireSubtree = !blockTree.nodes.containsKey(childNode.parentKey);
				if ((childNode.state == BlockTreeNodeState.VISIBLE || childNode.state == BlockTreeNodeState.REMOVED) || removingEntireSubtree)
				{
					tasks.remove(childKey);
					meshesAndBlocks.remove(childKey);
					if (blockTree.nodes.containsKey(childNode.parentKey))
						blockTree.nodes.get(childNode.parentKey).children.remove(childKey);
					blockTree.nodes.remove(childKey);
					childrenQueue.addAll(childNode.children);
				}
			}

			// Submit tasks for pending children in case the resolution for this block needs to increase
			submitTasksForChildren(key);
		}
	}

	private synchronized void submitTasksForChildren(final ShapeKey<T> key)
	{
		blockTree.nodes.get(key).children.forEach(childKey -> {
			if (blockTree.nodes.get(childKey).state == BlockTreeNodeState.PENDING)
				submitTask(tasks.get(childKey));
		});
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
	 * Returns a set of blocks to render and the distance from the camera to each block.
	 *
	 * @param params
	 * @return
	 */
	private synchronized BlocksToRender getBlocksToRender(final SceneUpdateJobParameters params)
	{
		// Fill in renderer metadata
		final double[][] scales = new double[numScaleLevels][];
		Arrays.setAll(scales, i -> DataSource.getScale(source, 0, i));

		final CellGrid[] sourceGrids = new CellGrid[numScaleLevels];
		Arrays.setAll(sourceGrids, i -> source.getGrid(i));

		final int[][] rendererFullBlockSizes = getRendererFullBlockSizes(rendererBlockSize, scales);
		LOG.debug("Scales: {}, renderer block sizes: {}", scales, rendererFullBlockSizes);

		final CellGrid[] rendererGrids = new CellGrid[sourceGrids.length];
		for (int i = 0; i < rendererGrids.length; ++i)
			rendererGrids[i] = new CellGrid(sourceGrids[i].getImgDimensions(), rendererFullBlockSizes[i]);

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
			cameraToSourceTransform.preConcatenate(params.eyeToWorldTransform).preConcatenate(sourceToWorldTransform.inverse());

			viewFrustumCullingInSourceSpace[i] = new ViewFrustumCulling(params.viewFrustum, cameraToSourceTransform);

			final double[] extractedScale = new double[3];
			Arrays.setAll(extractedScale, d -> Affine3DHelpers.extractScale(cameraToSourceTransform.inverse(), d));

			minMipmapPixelSize[i] = Arrays.stream(extractedScale).min().getAsDouble();
			maxRelativeScaleFactors[i] = Arrays.stream(DataSource.getRelativeScales(source, 0, 0, i)).max().getAsDouble();
		}

		final BlockTree blockTreeToRender = new BlockTree();
		final Map<ShapeKey<T>, Double> distancesFromCamera = new HashMap<>();
		final LinkedHashMap<ShapeKey<T>, ShapeKey<T>> blockAndParentQueue = new LinkedHashMap<>();

		// start with all blocks at the lowest resolution
		final int lowestScaleLevel = numScaleLevels - 1;
		final CellGrid rendererGridAtLowestResolition = rendererGrids[lowestScaleLevel];
		for (final HashWrapper<Interval> sourceBlockAtLowestResolution : sourceBlocks[lowestScaleLevel])
		{
			final long[] intersectingRendererBlockIndices = Grids.getIntersectingBlocks(sourceBlockAtLowestResolution.getData(), rendererGridAtLowestResolition);
			for (final long rendererBlockIndex : intersectingRendererBlockIndices)
			{
				final ShapeKey<T> key = createShapeKey(
						rendererGridAtLowestResolition,
						rendererBlockIndex,
						lowestScaleLevel,
						params
					);
				blockAndParentQueue.put(key, null);
			}
		}

		while (!blockAndParentQueue.isEmpty())
		{
			final Iterator<Entry<ShapeKey<T>, ShapeKey<T>>> it = blockAndParentQueue.entrySet().iterator();
			final Entry<ShapeKey<T>, ShapeKey<T>> entry = it.next();
			it.remove();

			final ShapeKey<T> key = entry.getKey();
			final ShapeKey<T> parentKey = entry.getValue();

			final int scaleLevel = key.scaleIndex();
			final Interval blockInterval = key.interval();

			if (viewFrustumCullingInSourceSpace[scaleLevel].intersects(blockInterval))
			{
				final double distanceFromCamera = viewFrustumCullingInSourceSpace[scaleLevel].distanceFromCamera(blockInterval);
				final double screenSizeToViewPlaneRatio = params.viewFrustum.screenSizeToViewPlaneRatio(distanceFromCamera);
				final double screenPixelSize = screenSizeToViewPlaneRatio * minMipmapPixelSize[scaleLevel];
				LOG.debug("scaleIndex={}, screenSizeToViewPlaneRatio={}, screenPixelSize={}", scaleLevel, screenSizeToViewPlaneRatio, screenPixelSize);

				final BlockTreeNode treeNode = new BlockTreeNode(parentKey, new HashSet<>());
				blockTreeToRender.nodes.put(key, treeNode);
				if (parentKey != null)
					blockTreeToRender.nodes.get(parentKey).children.add(key);
				distancesFromCamera.put(key, distanceFromCamera);

				// check if needed to subdivide the block
				if (scaleLevel > params.highestScaleIndex && screenPixelSize > maxRelativeScaleFactors[params.preferredScaleIndex])
				{
					final int nextScaleLevel = scaleLevel - 1;

					// figure out what source blocks at the next scale level intersect with the source block at the current scale level
					final CellGrid sourceNextLevelGrid = sourceGrids[nextScaleLevel];
					final CellGrid rendererNextLevelGrid = rendererGrids[nextScaleLevel];

					final double[] relativeScales = new double[3];
					Arrays.setAll(relativeScales, d -> scales[scaleLevel][d] / scales[nextScaleLevel][d]);

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
						final ShapeKey<T> childKey = createShapeKey(
								rendererNextLevelGrid,
								intersectingNextLevelBlockIndex,
								nextScaleLevel,
								params
							);

						final long[] intersectingSourceNextLevelBlockIndices = Grids.getIntersectingBlocks(childKey.interval(), sourceNextLevelGrid);
						// check if there is a source block that intersects with the target block at higher resolution that is currently being considered
						for (final long intersectingSourceNextLevelBlockIndex : intersectingSourceNextLevelBlockIndices)
						{
							final HashWrapper<Interval> intersectingSourceNextLevelBlock = HashWrapper.interval(Grids.getCellInterval(sourceNextLevelGrid, intersectingSourceNextLevelBlockIndex));
							if (sourceBlocks[nextScaleLevel].contains(intersectingSourceNextLevelBlock))
							{
								blockAndParentQueue.put(childKey, key);
								break;
							}
						}
					}
				}
			}
			else if (parentKey == null)
			{
				// always render all blocks at lowest resolution even if they are currently outside the screen
				final BlockTreeNode treeNode = new BlockTreeNode(parentKey, new HashSet<>());
				blockTreeToRender.nodes.put(key, treeNode);
				distancesFromCamera.put(key, Double.POSITIVE_INFINITY);
			}
		}

		return new BlocksToRender(blockTreeToRender, distancesFromCamera);
	}



	/**
	 * Updates the scene block tree with respect to the newly requested block tree.
	 * Filters out blocks that do not need to be rendered. {@code blocksToRendered.renderListWithDistances} is modified in-place to store the filtered set.
	 *
	 * @param blocksToRender
	 */
	private synchronized void updateBlockTree(final BlocksToRender blocksToRender)
	{
		if (blockTree.nodes.isEmpty())
		{
			blockTree.nodes.putAll(blocksToRender.blockTree.nodes);
			return;
		}

		// For collecting blocks that are not in the current tree yet and need to be rendered
		final Set<ShapeKey<T>> filteredKeysToRender = new HashSet<>();

		// For collecting blocks that will need to stay in the current tree
		final Set<ShapeKey<T>> touchedBlocks = new HashSet<>();

		// Find all leaf nodes in the new tree
		final Set<ShapeKey<T>> newLeafKeys = new HashSet<>(blocksToRender.blockTree.nodes.keySet());
		blocksToRender.blockTree.nodes.values().forEach(newTreeNode -> newLeafKeys.remove(newTreeNode.parentKey));

		for (final ShapeKey<T> newLeafKey : newLeafKeys)
		{
			// Check if the new leaf node is contained in the current tree
			if (blockTree.nodes.containsKey(newLeafKey))
			{
				final BlockTreeNode treeNodeForNewLeafKey = blockTree.nodes.get(newLeafKey);
				if (treeNodeForNewLeafKey.state == BlockTreeNodeState.REMOVED)
				{
					// Request to render the block if it's already been removed
					// (this is the case when it's not a leaf node in the current tree and has already been replaced with higher-res blocks)
					treeNodeForNewLeafKey.state = BlockTreeNodeState.PENDING;
					filteredKeysToRender.add(newLeafKey);
				}

				// Update the state for all children in the current tree: they will be removed once this block is added onto the scene
				// (not only direct children, but all recursive children are affected)
				final Queue<ShapeKey<T>> childrenQueue = new ArrayDeque<>(treeNodeForNewLeafKey.children);
				while (!childrenQueue.isEmpty())
				{
					final ShapeKey<T> childKey = childrenQueue.poll();
					assert blockTree.nodes.containsKey(childKey);
					final BlockTreeNode childTreeNode = blockTree.nodes.get(childKey);
					if (childTreeNode.state == BlockTreeNodeState.VISIBLE || childTreeNode.state == BlockTreeNodeState.REMOVED)
					{
						touchedBlocks.add(childKey);
						childrenQueue.addAll(childTreeNode.children);
					}
				}
			}
			else
			{
				// Block is not in the current tree yet, need to add this block and all its intermediate ancestors
				// This adds remaining nodes in the tree and required blocks to the to-be-rendered list
				ShapeKey<T> keyToRender = newLeafKey, lastChildKey = null;
				while (keyToRender != null)
				{
					final ShapeKey<T> parentKey = blocksToRender.blockTree.nodes.get(keyToRender).parentKey;
					if (!blockTree.nodes.containsKey(keyToRender))
					{
						// The block is not in the tree yet, insert it and add the block to the render list
						filteredKeysToRender.add(keyToRender);
						blockTree.nodes.put(keyToRender, new BlockTreeNode(parentKey, new HashSet<>()));
					}

					if (lastChildKey != null)
						blockTree.nodes.get(keyToRender).children.add(lastChildKey);

					lastChildKey = keyToRender;
					keyToRender = parentKey;
				}
			}

			// Mark the block and all its ancestors to be kept in the tree
			ShapeKey<T> keyToTouch = newLeafKey;
			while (keyToTouch != null)
			{
				touchedBlocks.add(keyToTouch);
				keyToTouch = blocksToRender.blockTree.nodes.get(keyToTouch).parentKey;
			}
		}

		// Remove unneeded blocks from the tree
		blockTree.nodes.keySet().retainAll(touchedBlocks);
		for (final BlockTreeNode treeNode : blockTree.nodes.values())
		{
			treeNode.children.retainAll(touchedBlocks);
			if (treeNode.parentKey != null)
				assert blockTree.nodes.containsKey(treeNode.parentKey);
		}

		// Filter the rendering list and retain only necessary keys to be rendered
		blocksToRender.renderListWithDistances.keySet().retainAll(filteredKeysToRender);
	}

	private ShapeKey<T> createShapeKey(
			final CellGrid grid,
			final long index,
			final int scaleLevel,
			final SceneUpdateJobParameters params)
	{
		final Interval blockInterval = Grids.getCellInterval(grid, index);
		return new ShapeKey<>(
				identifier,
				scaleLevel,
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
		final MeshView mv = new MeshView(mesh);
		mv.setOpacity(1.0);
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
