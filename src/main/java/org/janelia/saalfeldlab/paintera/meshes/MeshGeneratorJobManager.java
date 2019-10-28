package org.janelia.saalfeldlab.paintera.meshes;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import eu.mihosoft.jcsg.ext.openjfx.shape3d.PolygonMeshView;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;

import org.janelia.saalfeldlab.util.grids.Grids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
public class MeshGeneratorJobManager<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final class SceneUpdateJobParameters
	{
		final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree;
		final CellGrid[] rendererGrids;
		final int simplificationIterations;
		final double smoothingLambda;
		final int smoothingIterations;
		final double minLabelRatio;

		SceneUpdateJobParameters(
			final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree,
			final CellGrid[] rendererGrids,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final double minLabelRatio)
		{
			this.sceneBlockTree = sceneBlockTree;
			this.rendererGrids = rendererGrids;
			this.simplificationIterations = simplificationIterations;
			this.smoothingLambda = smoothingLambda;
			this.smoothingIterations = smoothingIterations;
			this.minLabelRatio = minLabelRatio;
		}
	}

	private enum TaskState
	{
		CREATED,
		SCHEDULED,
		RUNNING,
		COMPLETED,
		INTERRUPTED
	}

	private class Task
	{
		final Runnable task;
		MeshWorkerPriority priority;
		final long tag;
		TaskState state = TaskState.CREATED;

		Task(final Runnable task, final MeshWorkerPriority priority, final long tag)
		{
			this.task = task;
			this.priority = priority;
			this.tag = tag;
		}
	}

	private enum BlockTreeNodeState
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

	private final class StatefulBlockTreeNode extends BlockTreeNode<ShapeKey<T>>
	{
		BlockTreeNodeState state = BlockTreeNodeState.PENDING; // initial state is always PENDING

		StatefulBlockTreeNode(final ShapeKey<T> parentKey, final Set<ShapeKey<T>> children, final double distanceFromCamera)
		{
			super(parentKey, children, distanceFromCamera);
		}

		@Override
		public String toString()
		{
			return String.format("[state=%s, parentExists=%b, numChildren=%d, distanceFromCamera=%.5f]", state, parentKey != null, children.size(), distanceFromCamera);
		}
	}


	private final DataSource<?, ?> source;

	private final T identifier;

	private final AffineTransform3D[] unshiftedWorldTransforms;

	private final Map<ShapeKey<T>, Task> tasks = new HashMap<>();

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks;

	private final Pair<Group, Group> meshesAndBlocksGroups;

	private final MeshViewUpdateQueue<T> meshViewUpdateQueue;

	private final InterruptibleFunction<T, Interval[]>[] getBlockLists;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes;

	private final ExecutorService managers;

	private final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers;

	private final int numScaleLevels;

	private final IndividualMeshProgress meshProgress;

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	private final ObjectProperty<SceneUpdateJobParameters> sceneJobUpdateParametersProperty = new SimpleObjectProperty<>();

	private final BlockTree<ShapeKey<T>, StatefulBlockTreeNode> blockTree = new BlockTree<>();

	private final AtomicLong sceneUpdateCounter = new AtomicLong();

	public MeshGeneratorJobManager(
			final DataSource<?, ?> source,
			final T identifier,
			final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks,
			final Pair<Group, Group> meshesAndBlocksGroups,
			final MeshViewUpdateQueue<T> meshViewUpdateQueue,
			final InterruptibleFunction<T, Interval[]>[] getBlockLists,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes,
			final AffineTransform3D[] unshiftedWorldTransforms,
			final ExecutorService managers,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers,
			final IndividualMeshProgress meshProgress)
	{
		this.source = source;
		this.identifier = identifier;
		this.meshesAndBlocks = meshesAndBlocks;
		this.meshesAndBlocksGroups = meshesAndBlocksGroups;
		this.meshViewUpdateQueue = meshViewUpdateQueue;
		this.getBlockLists = getBlockLists;
		this.getMeshes = getMeshes;
		this.unshiftedWorldTransforms = unshiftedWorldTransforms;
		this.managers = managers;
		this.workers = workers;
		this.numScaleLevels = source.getNumMipmapLevels();
		this.meshesAndBlocks.addListener(this::handleMeshListChange);
		this.meshProgress = meshProgress;
	}

	public void submit(
			final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree,
			final CellGrid[] rendererGrids,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final double minLabelRatio)
	{
		if (isInterrupted.get())
			return;

		final SceneUpdateJobParameters params = new SceneUpdateJobParameters(
				sceneBlockTree,
				rendererGrids,
				simplificationIterations,
				smoothingLambda,
				smoothingIterations,
				minLabelRatio
			);

		synchronized (sceneJobUpdateParametersProperty)
		{
			final boolean needToSubmit = sceneJobUpdateParametersProperty.get() == null;
			sceneJobUpdateParametersProperty.set(params);
			if (needToSubmit && !managers.isShutdown())
				managers.submit(withErrorPrinting(this::updateScene));
		}
	}

	public synchronized void interrupt()
	{
		if (isInterrupted.get())
			return;

		isInterrupted.set(true);

		LOG.debug("Interrupting for {} keys={}", this.identifier, tasks.keySet());
		for (final InterruptibleFunction<T, Interval[]> getBlockList : this.getBlockLists)
			getBlockList.interruptFor(this.identifier);

		for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
			tasks.keySet().forEach(getMesh::interruptFor);
		interruptTasks(tasks.keySet());

		meshesAndBlocks.clear();
	}

	private synchronized void updateScene()
	{
		if (isInterrupted.get())
			return;

		LOG.debug("ID {}: scene update initiated", identifier);
		sceneUpdateCounter.incrementAndGet();

		final SceneUpdateJobParameters params;
		synchronized (sceneJobUpdateParametersProperty)
		{
			params = sceneJobUpdateParametersProperty.get();
			sceneJobUpdateParametersProperty.set(null);
		}

		// Update the block tree and get the set of blocks that still need to be rendered (and the total number of blocks in the new tree)
		final Pair<Set<ShapeKey<T>>, Integer> filteredBlocksAndNumTotalBlocks = updateBlockTree(params);

		// remove blocks from the scene that are not in the updated tree
		meshesAndBlocks.keySet().retainAll(blockTree.nodes.keySet());

		// stop tasks for blocks that are not in the updated tree
		final List<ShapeKey<T>> taskKeysToInterrupt = tasks.keySet().stream()
				.filter(key -> !blockTree.nodes.containsKey(key))
				.collect(Collectors.toList());
		interruptTasks(taskKeysToInterrupt);

		// re-prioritize all existing tasks with respect to the new distances between the blocks and the camera
		synchronized (workers)
		{
			final Map<Runnable, MeshWorkerPriority> reprioritizedTasks = new HashMap<>();
			for (final Entry<ShapeKey<T>, Task> entry : tasks.entrySet())
			{
				final ShapeKey<T> key = entry.getKey();
				final Task task = entry.getValue();
				if (task.state == TaskState.CREATED || task.state == TaskState.SCHEDULED)
				{
					assert blockTree.nodes.containsKey(key) : "Task for the pending block already exists but its new priority is missing: " + key;
					task.priority = new MeshWorkerPriority(blockTree.nodes.get(key).distanceFromCamera, key.scaleIndex());
					if (workers.containsTask(task.task))
					{
						assert task.state == TaskState.SCHEDULED : "Task is in the worker queue but its state is " + task.state + ", expected SCHEDULED: " + key;
						reprioritizedTasks.put(task.task, task.priority);
					}
				}
			}
			// check what if the task is already running and is not in the queue anymore
			if (!reprioritizedTasks.isEmpty())
				workers.addOrUpdateTasks(reprioritizedTasks);
		}

		// re-prioritize blocks in the FX mesh queue
		synchronized (meshViewUpdateQueue)
		{
			for (final Entry<ShapeKey<T>, StatefulBlockTreeNode> entry : blockTree.nodes.entrySet())
			{
				final ShapeKey<T> key = entry.getKey();
				final StatefulBlockTreeNode treeNode = entry.getValue();
				if (treeNode.state == BlockTreeNodeState.RENDERED && meshViewUpdateQueue.contains(key))
				{
					final MeshWorkerPriority newPriority = new MeshWorkerPriority(treeNode.distanceFromCamera, key.scaleIndex());
					meshViewUpdateQueue.updatePriority(key, newPriority);
				}
				else
				{
					assert !meshViewUpdateQueue.contains(key) : "Block that is in the " + treeNode.state + " state is not supposed to be in the FX queue: " + key;
				}
			}
		}

		// calculate how many tasks are already completed
		final int numTotalBlocksToRender = filteredBlocksAndNumTotalBlocks.getB();
		final int numActualBlocksToRender = filteredBlocksAndNumTotalBlocks.getA().size();
		final int numCompletedBlocks = numTotalBlocksToRender - numActualBlocksToRender - tasks.size();
		meshProgress.set(numTotalBlocksToRender, numCompletedBlocks);
		final int numExistingNonEmptyMeshes = (int) meshesAndBlocks.values().stream().filter(pair -> pair.getA() != null).count();
		LOG.debug("ID {}: numTasks={}, numCompletedTasks={}, numActualBlocksToRender={}. Number of meshes in the scene: {} ({} of them are non-empty)", identifier, numTotalBlocksToRender, numCompletedBlocks, numActualBlocksToRender, meshesAndBlocks.size(), numExistingNonEmptyMeshes);

		// create tasks for blocks that still need to be generated
		LOG.debug("Creating mesh generation tasks for {} blocks for id {}.", numActualBlocksToRender, identifier);
		filteredBlocksAndNumTotalBlocks.getA().forEach(this::createTask);

		// Update the meshes according to the new tree node states and submit top-level tasks
		final Collection<ShapeKey<T>> topLevelKeys = blockTree.nodes.keySet().stream().filter(key -> blockTree.nodes.get(key).parentKey == null).collect(Collectors.toList());
		final Queue<ShapeKey<T>> keyQueue = new ArrayDeque<>(topLevelKeys);
		final List<ShapeKey<T>> tasksToSubmit = new ArrayList<>();
		while (!keyQueue.isEmpty())
		{
			final ShapeKey<T> key = keyQueue.poll();
			final StatefulBlockTreeNode treeNode = blockTree.nodes.get(key);
			keyQueue.addAll(treeNode.children);

			if (treeNode.parentKey == null && treeNode.state == BlockTreeNodeState.PENDING)
			{
				// Top-level block
				tasksToSubmit.add(key);
			}
			else if (treeNode.state == BlockTreeNodeState.VISIBLE)
			{
				final boolean areAllHigherResBlocksReady = !treeNode.children.isEmpty() && treeNode.children.stream().allMatch(childKey -> blockTree.nodes.get(childKey).state == BlockTreeNodeState.HIDDEN);
				if (areAllHigherResBlocksReady)
				{
					// All children blocks in this block are ready, remove it and submit the tasks for next-level contained blocks if any
					treeNode.children.forEach(childKey -> {
						blockTree.nodes.get(childKey).state = BlockTreeNodeState.VISIBLE;
						final Pair<MeshView, Node> childMeshAndBlock = meshesAndBlocks.get(childKey);
						InvokeOnJavaFXApplicationThread.invoke(() -> setMeshVisibility(childMeshAndBlock, true));
					});

					treeNode.state = BlockTreeNodeState.REMOVED;
					assert !tasks.containsKey(key) : "Low-res parent block is being removed but there is a task for it: " + key;
					meshesAndBlocks.remove(key);

					treeNode.children.forEach(childKey -> tasksToSubmit.addAll(getPendingTasksForChildren(childKey)));
				}
				else
				{
					tasksToSubmit.addAll(getPendingTasksForChildren(key));
				}
			}
			else if (treeNode.state == BlockTreeNodeState.REMOVED)
			{
				tasksToSubmit.addAll(getPendingTasksForChildren(key));
			}
		}
		submitTasks(tasksToSubmit);
	}

	private synchronized void createTask(final ShapeKey<T> key)
	{
		final long tag = sceneUpdateCounter.get();
		final Runnable taskRunnable = () ->
		{
			final Task task;
			final BooleanSupplier isTaskCanceled;
			synchronized (this)
			{
				task = tasks.get(key);
				if (task == null || task.tag != tag)
				{
					LOG.debug("Task for key {} has been removed", key);
					return;
				}

				isTaskCanceled = () -> task.state == TaskState.INTERRUPTED || Thread.currentThread().isInterrupted();
				if (isTaskCanceled.getAsBoolean())
					return;

				assert task.state == TaskState.SCHEDULED : "Started to execute task but its state is " + task.state + " while it's supposed to be SCHEDULED: " + key;
				assert task.priority != null : "Started to execute task but its priority is null: " + key;

				task.state = TaskState.RUNNING;
				LOG.debug("Executing task for key {} at distance {}", key, task.priority.distanceFromCamera);
			}

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
					if (isTaskCanceled.getAsBoolean())
					{
						// Task has been interrupted
						if (!workers.isShutdown())
							assert !tasks.containsKey(key) || tasks.get(key).tag != tag : "Task has been interrupted but it still exists in the tasks collection of size " + tasks.size() + ": " + key;
					}
					else
					{
						// Terminated because of an error
						e.printStackTrace();
						if (tasks.containsKey(key) && tasks.get(key).tag == tag)
							tasks.remove(key);
					}
				}
				return;
			}

			if (verticesAndNormals != null)
			{
				synchronized (this)
				{
					if (!isTaskCanceled.getAsBoolean())
					{
						task.state = TaskState.COMPLETED;
						onMeshGenerated(key, verticesAndNormals);
					}
				}
			}
		};

		assert blockTree.nodes.containsKey(key) : "Requested to create task for block but it's not in the tree, key: " + key;
		final double distanceFromCamera = blockTree.nodes.get(key).distanceFromCamera;

		final MeshWorkerPriority taskPriority = new MeshWorkerPriority(distanceFromCamera, key.scaleIndex());
		final Task task = new Task(withErrorPrinting(taskRunnable), taskPriority, tag);

		assert !tasks.containsKey(key) : "Trying to create new task for block but it already exists: " + key;
		tasks.put(key, task);
	}

	private synchronized void submitTasks(final Collection<ShapeKey<T>> keys)
	{
		if (keys.isEmpty())
			return;

		final Map<Runnable, MeshWorkerPriority> tasksToSubmit = new HashMap<>();
		for (final ShapeKey<T> key : keys)
		{
			final Task task = tasks.get(key);
			if (task != null && task.state == TaskState.CREATED)
			{
				task.state = TaskState.SCHEDULED;
				tasksToSubmit.put(task.task, task.priority);
			}
		}
		if (!tasksToSubmit.isEmpty())
			workers.addOrUpdateTasks(tasksToSubmit);
	}

	private synchronized void interruptTasks(final Collection<ShapeKey<T>> keys)
	{
		if (keys.isEmpty())
			return;

		final Set<Runnable> tasksToInterrupt = new HashSet<>();
		for (final ShapeKey<T> key : keys)
		{
			getMeshes[key.scaleIndex()].interruptFor(key);
			final Task task = tasks.get(key);
			if (task != null && (task.state == TaskState.SCHEDULED || task.state == TaskState.RUNNING))
			{
				task.state = TaskState.INTERRUPTED;
				tasksToInterrupt.add(task.task);
			}
		}
		if (!tasksToInterrupt.isEmpty())
			workers.removeTasks(tasksToInterrupt);
		tasks.keySet().removeAll(new ArrayList<>(keys));
	}

	private synchronized void handleMeshListChange(final MapChangeListener.Change<? extends ShapeKey<T>, ? extends Pair<MeshView, Node>> change)
	{
		final ShapeKey<T> key = change.getKey();
		assert change.wasAdded() != change.wasRemoved() : "Mesh is only supposed to be added or removed at any time but not replaced: " + key;

		if (change.wasAdded())
		{
			assert tasks.containsKey(key) : "Mesh was rendered but its task does not exist: " + key;
			final long tag = tasks.get(key).tag;
			final Runnable onMeshAdded = () -> {
				if (!managers.isShutdown())
					managers.submit(withErrorPrinting(() -> onMeshAdded(key, tag)));
			};

			if (change.getValueAdded().getA() != null || change.getValueAdded().getB() != null)
			{
				// add to the queue, call onMeshAdded() when complete
				final MeshWorkerPriority priority = tasks.get(key).priority;

				meshViewUpdateQueue.addToQueue(
						key,
						change.getValueAdded(),
						meshesAndBlocksGroups,
						onMeshAdded,
						priority
				);
			}
			else
			{
				// nothing to add, invoke the callback immediately
				onMeshAdded.run();
			}
		}

		if (change.wasRemoved() && (change.getValueRemoved().getA() != null || change.getValueRemoved().getB() != null))
		{
			// try to remove the request from the queue in case the mesh has not been added to the scene yet
			if (!meshViewUpdateQueue.removeFromQueue(key))
			{
				// was not in the queue, remove it from the scene
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					meshesAndBlocksGroups.getA().getChildren().remove(change.getValueRemoved().getA());
					meshesAndBlocksGroups.getB().getChildren().remove(change.getValueRemoved().getB());
				});
			}
		}
	}

	private synchronized void onMeshGenerated(final ShapeKey<T> key, final Pair<float[], float[]> verticesAndNormals)
	{
		assert blockTree.nodes.containsKey(key) : "Mesh for block has been generated but it does not exist in the current block tree: " + key;
		assert tasks.containsKey(key) : "Mesh for block has been generated but its task does not exist: " + key;
		assert !meshesAndBlocks.containsKey(key) : "Mesh for block has been generated but it already exists in the current set of generated/visible meshes: " + key;
		LOG.debug("ID {}: block {} has been generated", identifier, key);

		final boolean nonEmptyMesh = Math.max(verticesAndNormals.getA().length, verticesAndNormals.getB().length) > 0;
		final MeshView mv = nonEmptyMesh ? makeMeshView(verticesAndNormals) : null;
		final Node blockShape = nonEmptyMesh ? createBlockShape(key) : null;
		final Pair<MeshView, Node> meshAndBlock = new ValuePair<>(mv, blockShape);
		LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);

		final StatefulBlockTreeNode treeNode = blockTree.nodes.get(key);
		treeNode.state = BlockTreeNodeState.RENDERED;

		if (treeNode.parentKey != null)
			assert blockTree.nodes.containsKey(treeNode.parentKey) : "Generated mesh has a parent block but it doesn't exist in the current block tree: key=" + key + ", parentKey=" + treeNode.parentKey;
		final boolean isParentBlockVisible = treeNode.parentKey != null && blockTree.nodes.get(treeNode.parentKey).state == BlockTreeNodeState.VISIBLE;

		if (isParentBlockVisible)
		{
			assert meshesAndBlocks.containsKey(treeNode.parentKey) : "Parent block of a generated mesh is in the VISIBLE state but it doesn't exist in the current set of generated/visible meshes: key=" + key + ", parentKey=" + treeNode.parentKey;
			setMeshVisibility(meshAndBlock, false);
		}

		meshesAndBlocks.put(key, meshAndBlock);
	}

	private synchronized void onMeshAdded(final ShapeKey<T> key, final long tag)
	{
		// Check if this block is still relevant.
		// The tag value is used to ensure that the block is actually relevant. Even if the task for the same key exists,
		// it might have been removed and created again, so the added block actually needs to be ignored.
		if (!tasks.containsKey(key) || tasks.get(key).state != TaskState.COMPLETED || tasks.get(key).tag != tag)
		{
			LOG.debug("ID {}: the added mesh for block {} is not relevant anymore", identifier, key);
			return;
		}

		assert blockTree.nodes.containsKey(key) : "Mesh has been added onto the scene but it does not exist in the current block tree: " + key;
		assert meshesAndBlocks.containsKey(key) : "Mesh has been added onto the scene but it does not exist in the current set of generated/visible meshes: " + key;
		LOG.debug("ID {}: mesh for block {} has been added onto the scene", identifier, key);

		tasks.remove(key);
		meshProgress.incrementNumCompletedTasks();

		final StatefulBlockTreeNode treeNode = blockTree.nodes.get(key);
		assert treeNode.state == BlockTreeNodeState.RENDERED : "Mesh has been added onto the scene but the block is in the " + treeNode.state + " when it's supposed to be in the RENDERED state: " + key;

		if (treeNode.parentKey != null)
			assert blockTree.nodes.containsKey(treeNode.parentKey) : "Added mesh has a parent block but it doesn't exist in the current block tree: key=" + key + ", parentKey=" + treeNode.parentKey;
		final boolean isParentBlockVisible = treeNode.parentKey != null && blockTree.nodes.get(treeNode.parentKey).state == BlockTreeNodeState.VISIBLE;

		if (isParentBlockVisible)
		{
			assert meshesAndBlocks.containsKey(treeNode.parentKey) : "Parent block of an added mesh is in the VISIBLE state but it doesn't exist in the current set of generated/visible meshes: key=" + key + ", parentKey=" + treeNode.parentKey;

			// check if all children of the parent block are ready, and if so, update their visibility and remove the parent block
			final StatefulBlockTreeNode parentTreeNode = blockTree.nodes.get(treeNode.parentKey);
			treeNode.state = BlockTreeNodeState.HIDDEN;
			final boolean areAllChildrenReady = parentTreeNode.children.stream().map(blockTree.nodes::get).allMatch(childTreeNode -> childTreeNode.state == BlockTreeNodeState.HIDDEN);
			if (areAllChildrenReady)
			{
				parentTreeNode.children.forEach(childKey -> {
					blockTree.nodes.get(childKey).state = BlockTreeNodeState.VISIBLE;
					final Pair<MeshView, Node> childMeshAndBlock = meshesAndBlocks.get(childKey);
					InvokeOnJavaFXApplicationThread.invoke(() -> setMeshVisibility(childMeshAndBlock, true));
				});

				parentTreeNode.state = BlockTreeNodeState.REMOVED;
				assert !tasks.containsKey(treeNode.parentKey) : "Low-res parent block is being removed but there is a task for it: " + key;
				meshesAndBlocks.remove(treeNode.parentKey);

				// Submit tasks for next-level contained blocks
				final List<ShapeKey<T>> tasksToSubmit = new ArrayList<>();
				parentTreeNode.children.forEach(childKey -> tasksToSubmit.addAll(getPendingTasksForChildren(childKey)));
				submitTasks(tasksToSubmit);
			}
		}
		else
		{
			// Update the visibility of this block
			treeNode.state = BlockTreeNodeState.VISIBLE;
			final Pair<MeshView, Node> meshAndBlock = meshesAndBlocks.get(key);
			InvokeOnJavaFXApplicationThread.invoke(() -> setMeshVisibility(meshAndBlock, true));

			// Remove all children nodes that are not needed anymore: this is the case when resolution for the block is decreased,
			// and a set of higher-res blocks needs to be replaced with the single low-res block
			final Queue<ShapeKey<T>> childrenQueue = new ArrayDeque<>(treeNode.children);
			final List<ShapeKey<T>> tasksToInterrupt = new ArrayList<>();
			while (!childrenQueue.isEmpty())
			{
				final ShapeKey<T> childKey = childrenQueue.poll();
				final StatefulBlockTreeNode childNode = blockTree.nodes.get(childKey);
				final boolean removingEntireSubtree = !blockTree.nodes.containsKey(childNode.parentKey);
				if ((childNode.state == BlockTreeNodeState.VISIBLE || childNode.state == BlockTreeNodeState.REMOVED) || removingEntireSubtree)
				{
					tasksToInterrupt.add(childKey);
					meshesAndBlocks.remove(childKey);
					if (blockTree.nodes.containsKey(childNode.parentKey))
						blockTree.nodes.get(childNode.parentKey).children.remove(childKey);
					blockTree.nodes.remove(childKey);
					childrenQueue.addAll(childNode.children);
				}
			}
			interruptTasks(tasksToInterrupt);

			// Submit tasks for pending children in case the resolution for this block needs to increase
			submitTasks(getPendingTasksForChildren(key));
		}
		if (tasks.isEmpty())
		{
			LOG.debug("All tasks are finished");
			assert blockTree.isValid() : "Resulting block tree is not valid";
		}
	}

	private synchronized List<ShapeKey<T>> getPendingTasksForChildren(final ShapeKey<T> key)
	{
		return blockTree.nodes.get(key).children.stream()
				.filter(childKey -> blockTree.nodes.get(childKey).state == BlockTreeNodeState.PENDING)
				.collect(Collectors.toList());
	}

	private void setMeshVisibility(final Pair<MeshView, Node> meshAndBlock, final boolean isVisible)
	{
		if (meshAndBlock.getA() != null)
			meshAndBlock.getA().setVisible(isVisible);

		if (meshAndBlock.getB() != null)
			meshAndBlock.getB().setVisible(isVisible);
	}

	/**
	 * Updates the scene block tree with respect to the newly requested block tree.
	 * Filters out blocks that do not need to be rendered. {@code blocksToRendered.renderListWithDistances} is modified in-place to store the filtered set.
	 *
	 * @param params
	 * @return
	 */
	private synchronized Pair<Set<ShapeKey<T>>, Integer> updateBlockTree(final SceneUpdateJobParameters params)
	{
		// Create mapping of scene tree blocks to only those that contain the current label identifier
		final BiMap<BlockTreeFlatKey, ShapeKey<T>> mapping = HashBiMap.create();
		final int highestScaleLevelInTree = params.sceneBlockTree.nodes.keySet().stream().mapToInt(key -> key.scaleLevel).min().orElse(numScaleLevels);
		for (int scaleLevel = numScaleLevels - 1; scaleLevel >= highestScaleLevelInTree; --scaleLevel)
		{
			final Interval[] containingSourceBlocks = getBlockLists[scaleLevel].apply(identifier);
			for (final Interval sourceInterval : containingSourceBlocks)
			{
				final long[] intersectingRendererBlockIndices = Grids.getIntersectingBlocks(sourceInterval, params.rendererGrids[scaleLevel]);
				for (final long intersectingRendererBlockIndex : intersectingRendererBlockIndices)
				{
					final BlockTreeFlatKey flatKey = new BlockTreeFlatKey(scaleLevel, intersectingRendererBlockIndex);
					if (!mapping.containsKey(flatKey) && params.sceneBlockTree.nodes.containsKey(flatKey))
					{
						final ShapeKey<T> shapeKey = createShapeKey(
								params.rendererGrids[scaleLevel],
								intersectingRendererBlockIndex,
								scaleLevel,
								params
						);
						mapping.put(flatKey, shapeKey);
					}
				}
			}
		}

		// Create complete block tree that represents new scene state for the current label identifier
		final BlockTree<ShapeKey<T>, StatefulBlockTreeNode> blockTreeToRender = new BlockTree<>();
		for (final Entry<BlockTreeFlatKey, ShapeKey<T>> entry : mapping.entrySet())
		{
			final BlockTreeNode<BlockTreeFlatKey> sceneTreeNode = params.sceneBlockTree.nodes.get(entry.getKey());
			final ShapeKey<T> parentKey = mapping.get(sceneTreeNode.parentKey);
			assert (sceneTreeNode.parentKey == null) == (parentKey == null);
			final Set<ShapeKey<T>> children = new HashSet<>(sceneTreeNode.children.stream().map(mapping::get).filter(Objects::nonNull).collect(Collectors.toSet()));
			final StatefulBlockTreeNode treeNode = new StatefulBlockTreeNode(parentKey, children, sceneTreeNode.distanceFromCamera);
			blockTreeToRender.nodes.put(entry.getValue(), treeNode);
		}

		// Remove leaf blocks in the current block tree that have higher-res blocks in the scene block tree
		// (this means that these lower-res parent blocks contain the "overhanging" part of the label data and should not be included)
		final Queue<ShapeKey<T>> leafKeyQueue = new ArrayDeque<>(blockTreeToRender.getLeafKeys());
		while (!leafKeyQueue.isEmpty())
		{
			final ShapeKey<T> leafShapeKey = leafKeyQueue.poll();
			final BlockTreeFlatKey leafFlatKey = mapping.inverse().get(leafShapeKey);
			assert leafFlatKey != null && params.sceneBlockTree.nodes.containsKey(leafFlatKey);
			if (!params.sceneBlockTree.nodes.get(leafFlatKey).children.isEmpty())
			{
				// This block has been subdivided in the scene tree, but the current label data doesn't list any children blocks.
				// Therefore this block needs to be excluded from the renderer block tree to avoid rendering overhanging low-res parts.
				final StatefulBlockTreeNode removedLeafNode = blockTreeToRender.nodes.remove(leafShapeKey);
				assert removedLeafNode != null && removedLeafNode.children.isEmpty();
				if (removedLeafNode.parentKey != null)
				{
					final StatefulBlockTreeNode parentNode = blockTreeToRender.nodes.get(removedLeafNode.parentKey);
					assert parentNode != null && parentNode.children.contains(leafShapeKey);
					parentNode.children.remove(leafShapeKey);
					if (parentNode.children.isEmpty())
						leafKeyQueue.add(removedLeafNode.parentKey);
				}
			}
		}

		// The complete block tree for the current label id representing the new scene state is now ready
		final int numTotalBlocks = blockTreeToRender.nodes.size();
		assert blockTreeToRender.isValid() : "Requested block tree to render is not valid";

		// Initialize the tree if it was empty
		if (blockTree.nodes.isEmpty())
		{
			blockTree.nodes.putAll(blockTreeToRender.nodes);
			return new ValuePair<>(blockTreeToRender.nodes.keySet(), numTotalBlocks);
		}

		// For collecting blocks that are not in the current tree yet and need to be rendered
		final Set<ShapeKey<T>> filteredKeysToRender = new HashSet<>();

		// For collecting blocks that will need to stay in the current tree
		final Set<ShapeKey<T>> touchedBlocks = new HashSet<>();

		// Intersect the current block tree with the new requested tree, starting the traversal from the leaf nodes of the new tree
		for (final ShapeKey<T> newLeafKey : blockTreeToRender.getLeafKeys())
		{
			// Check if the new leaf node is contained in the current tree
			if (blockTree.nodes.containsKey(newLeafKey))
			{
				final StatefulBlockTreeNode treeNodeForNewLeafKey = blockTree.nodes.get(newLeafKey);
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
					assert blockTree.nodes.containsKey(childKey) : "Block was present in the children list but does not exist in the tree: " + childKey;
					final StatefulBlockTreeNode childTreeNode = blockTree.nodes.get(childKey);
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
					final ShapeKey<T> parentKey = blockTreeToRender.nodes.get(keyToRender).parentKey;
					if (!blockTree.nodes.containsKey(keyToRender))
					{
						// The block is not in the tree yet, insert it and add the block to the render list
						filteredKeysToRender.add(keyToRender);
						final double distanceFromCamera = blockTreeToRender.nodes.get(keyToRender).distanceFromCamera;
						blockTree.nodes.put(keyToRender, new StatefulBlockTreeNode(parentKey, new HashSet<>(), distanceFromCamera));
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
				keyToTouch = blockTreeToRender.nodes.get(keyToTouch).parentKey;
			}
		}

		// Remove unneeded blocks from the tree
		blockTree.nodes.keySet().retainAll(touchedBlocks);
		for (final StatefulBlockTreeNode treeNode : blockTree.nodes.values())
		{
			treeNode.children.retainAll(touchedBlocks);
			if (treeNode.parentKey != null)
				assert blockTree.nodes.containsKey(treeNode.parentKey) : "Block has been retained but its parent is not present in the tree: " + treeNode.parentKey;
		}

		// Update distances from the camera for each block in the new tree
		for (final Entry<ShapeKey<T>, StatefulBlockTreeNode> entry : blockTree.nodes.entrySet())
		{
			final StatefulBlockTreeNode blockTreeToRenderNode = blockTreeToRender.nodes.get(entry.getKey());
			entry.getValue().distanceFromCamera = blockTreeToRenderNode != null ? blockTreeToRenderNode.distanceFromCamera : Double.POSITIVE_INFINITY;
		}

		// The current tree should include all the newly requested nodes at this point
		assert blockTree.nodes.size() >= blockTreeToRender.nodes.size();
		assert blockTree.isValid();

		// Filter the rendering list and retain only necessary keys to be rendered
		return new ValuePair<>(filteredKeysToRender, numTotalBlocks);
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
				params.minLabelRatio,
				Intervals.minAsLongArray(blockInterval),
				Intervals.maxAsLongArray(blockInterval)
			);
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
		final Interval keyInterval = key.interval();
		final double[] worldMin = new double[3], worldMax = new double[3];
		Arrays.setAll(worldMin, d -> keyInterval.min(d));
		Arrays.setAll(worldMax, d -> keyInterval.min(d) + keyInterval.dimension(d));
		unshiftedWorldTransforms[key.scaleIndex()].apply(worldMin, worldMin);
		unshiftedWorldTransforms[key.scaleIndex()].apply(worldMax, worldMax);

		final RealInterval blockWorldInterval = new FinalRealInterval(worldMin, worldMax);
		final double[] blockWorldSize = new double[blockWorldInterval.numDimensions()];
		Arrays.setAll(blockWorldSize, d -> blockWorldInterval.realMax(d) - blockWorldInterval.realMin(d));

		// the standard Box primitive is made up of triangles, so the unwanted diagonals are visible when using DrawMode.Line
//		final Box box = new Box(
//				blockWorldSize[0],
//				blockWorldSize[1],
//				blockWorldSize[2]
//			);
		final PolygonMeshView box = new PolygonMeshView(Meshes.createQuadrilateralMesh(
				(float) blockWorldSize[0],
				(float) blockWorldSize[1],
				(float) blockWorldSize[2]
		));

		final double[] blockWorldTranslation = new double[blockWorldInterval.numDimensions()];
		Arrays.setAll(blockWorldTranslation, d -> blockWorldInterval.realMin(d) + blockWorldSize[d] * 0.5);

		box.setTranslateX(blockWorldTranslation[0]);
		box.setTranslateY(blockWorldTranslation[1]);
		box.setTranslateZ(blockWorldTranslation[2]);

		final PhongMaterial material = Meshes.painteraPhongMaterial();
		box.setCullFace(CullFace.NONE);
		box.setMaterial(material);
		box.setDrawMode(DrawMode.LINE);

		return box;
	}

	private static Runnable withErrorPrinting(final Runnable runnable)
	{
		return () -> {
			try {
				runnable.run();
			} catch (final RejectedExecutionException e) {
				// this happens when the application is being shut down and is normal, don't do anything
			} catch (final Throwable e) {
				e.printStackTrace();
			}
		};
	}
}
