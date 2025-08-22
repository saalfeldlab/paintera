package org.janelia.saalfeldlab.paintera.meshes;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.util.HashPriorityQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Throttles mesh updates on the FX thread to keep the application responsive.
 * <p>
 * If a lot of meshes are added at the same time, the application may freeze while they are being uploaded onto the GPU.
 * This class solves it by limiting the number of vertices+normals+faces that can be added to the scene at a time.
 */
public class MeshViewUpdateQueue<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private class MeshViewQueueEntry {

		private final Pair<MeshView, Node> meshAndBlockToAdd;
		private final Pair<Group, Group> meshAndBlockGroup;
		private final Runnable onCompleted;

		private MeshViewQueueEntry(
				final Pair<MeshView, Node> meshAndBlockToAdd,
				final Pair<Group, Group> meshAndBlockGroup,
				final Runnable onCompleted) {

			this.meshAndBlockToAdd = meshAndBlockToAdd;
			this.meshAndBlockGroup = meshAndBlockGroup;
			this.onCompleted = onCompleted;
		}
	}

	private final HashPriorityQueue<MeshWorkerPriority, ShapeKey<T>> priorityQueue = new HashPriorityQueue<>(Comparator.naturalOrder());
	private final Map<ShapeKey<T>, MeshViewQueueEntry> keysToEntries = new HashMap<>();

	private int numElementsPerFrame = Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE;
	private long frameDelayMsec = Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE;

	/**
	 * Places a request to add a mesh onto the scene into the queue.
	 * The request will be executed at some point later on FX application thread, and {@code onCompleted} will be called after that.
	 *
	 * @param key
	 * @param meshAndBlockToAdd
	 * @param meshAndBlockGroup
	 * @param onCompleted
	 */
	public synchronized void addToQueue(
			final ShapeKey<T> key,
			final Pair<MeshView, Node> meshAndBlockToAdd,
			final Pair<Group, Group> meshAndBlockGroup,
			final Runnable onCompleted,
			final MeshWorkerPriority priority) {

		final MeshViewQueueEntry entry = new MeshViewQueueEntry(meshAndBlockToAdd, meshAndBlockGroup, onCompleted);
		keysToEntries.put(key, entry);

		priorityQueue.addOrUpdate(priority, key);
		timer.start();
	}

	/**
	 * Removes the request to add a mesh with a specified key from the queue if it exists.
	 * Returns {@code true} if the queue contained the given key.
	 *
	 * @param key
	 * @return
	 */
	public synchronized boolean removeFromQueue(final ShapeKey<T> key) {

		assert keysToEntries.containsKey(key) == priorityQueue.contains(key);
		keysToEntries.remove(key);
		return priorityQueue.remove(key);
	}

	public synchronized void updatePriority(final ShapeKey<T> key, final MeshWorkerPriority priority) {

		if (!contains(key))
			throw new NoSuchElementException();
		priorityQueue.addOrUpdate(priority, key);
	}

	public synchronized boolean contains(final ShapeKey<T> key) {

		assert keysToEntries.containsKey(key) == priorityQueue.contains(key);
		return keysToEntries.containsKey(key);
	}

	public synchronized void update(final int numElementsPerFrame, final long frameDelayMsec) {

		this.numElementsPerFrame = numElementsPerFrame;
		this.frameDelayMsec = frameDelayMsec;
		LOG.debug("Update mesh update queue limits to numElementsPerFrame={}, frameDelayMsec={}", numElementsPerFrame, frameDelayMsec);
	}

	private AnimationTimer timer = new AnimationTimer() {

		private static final int MIN_ELEMENTS_PER_FRAME = 1000;
		private static final long TARGET_FRAME_TIME = 16_666_667;
		long lastFrameTime = 0;
		int elementsPerFrame = 10 * MIN_ELEMENTS_PER_FRAME;


		private void updateNumElementsPerFrame(long frameTime) {

			if (frameTime > TARGET_FRAME_TIME)
				elementsPerFrame = (int)(elementsPerFrame * .8);
			else
				elementsPerFrame = (int)(elementsPerFrame * 1.2);

			elementsPerFrame = Math.max(MIN_ELEMENTS_PER_FRAME, elementsPerFrame);
		}

		@Override public void handle(long now) {
			updateNumElementsPerFrame(now - lastFrameTime);
			lastFrameTime = now;
			addMeshImpl(elementsPerFrame);
		}
	};

	private void addMeshImpl(int elementsPerFrame) {

		assert Platform.isFxApplicationThread();

		final List<MeshViewQueueEntry> entriesToAdd = new ArrayList<>();
		int numElements = 0;

		synchronized (this) {
			while (!priorityQueue.isEmpty() && numElements <= elementsPerFrame && numElements != -1) {
				final ShapeKey<T> nextKey = priorityQueue.peek();
				final MeshViewQueueEntry nextQueueEntry = keysToEntries.get(nextKey);
				final MeshView nextMeshView = nextQueueEntry.meshAndBlockToAdd.getA();
				if (nextMeshView.getMesh() instanceof TriangleMesh) {
					final TriangleMesh nextMesh = (TriangleMesh)nextMeshView.getMesh();
					final int nextMeshNumVertices = nextMesh.getPoints().size() / nextMesh.getPointElementSize();
					final int nextMeshNumNormals = nextMesh.getNormals().size() / nextMesh.getNormalElementSize();
					final int nextMeshNumFaces = nextMesh.getFaces().size() / nextMesh.getFaceElementSize();
					final int nextNumElements = nextMeshNumVertices + nextMeshNumNormals + nextMeshNumFaces;
					if (!entriesToAdd.isEmpty() && numElements + nextNumElements > elementsPerFrame)
						break;

					numElements += nextNumElements;
				} else {
					numElements = -1;
				}

				// add entry
				entriesToAdd.add(nextQueueEntry);
				keysToEntries.remove(nextKey);
				priorityQueue.poll();
			}

			if (priorityQueue.isEmpty())
				timer.stop();;
		}

		LOG.debug("Adding {} meshes which all together contain {} elements (vertices+normals+faces", entriesToAdd.size(), numElements);
		for (final MeshViewQueueEntry entryToAdd : entriesToAdd) {
			entryToAdd.meshAndBlockGroup.getA().getChildren().add(entryToAdd.meshAndBlockToAdd.getA());
			entryToAdd.meshAndBlockGroup.getB().getChildren().add(entryToAdd.meshAndBlockToAdd.getB());
			if (entryToAdd.onCompleted != null)
				entryToAdd.onCompleted.run();
		}
	}
}
