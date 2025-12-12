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

	private final AnimationTimer timer = new AnimationTimer() {

		private static final int MIN_ELEMENTS_PER_FRAME = 5_000;
		private static final long TARGET_MIN_FRAME_TIME = 33_333_333; // 30 fps
		private static final long TARGET_MAX_FRAME_TIME = 66_666_667; // 15 fps
		private static final long UNRESPONSIVE_THRESHOLD = 100_000_000; // 10 fps

		private long lastFrameTime = 0;
		private int elementsPerFrame = 50_000;
		private int consecutiveFastFrames = 0;
		private int estimatedMaxElementsFor15Fps = 500_000;

		private void updateNumElementsPerFrame(long frameTime) {
			if (frameTime == 0) return; // First frame

			// Estimate max elements that would achieve 15 fps based on current performance
			// elements/time ratio scaled to TARGET_MAX_FRAME_TIME
			if (frameTime > 0 && elementsPerFrame > 0) {
				final double elementsPerNs = (double) elementsPerFrame / frameTime;
				estimatedMaxElementsFor15Fps = (int) (elementsPerNs * TARGET_MAX_FRAME_TIME);
				estimatedMaxElementsFor15Fps = Math.max(MIN_ELEMENTS_PER_FRAME, estimatedMaxElementsFor15Fps);
			}

			if (frameTime > UNRESPONSIVE_THRESHOLD) {
				// Too slow, reduce aggressively
				elementsPerFrame = (int)(elementsPerFrame * 0.5);
				consecutiveFastFrames = 0;
			} else if (frameTime > TARGET_MAX_FRAME_TIME) {
				// Slightly slow, reduce moderately
				elementsPerFrame = (int)(elementsPerFrame * 0.85);
				consecutiveFastFrames = 0;
			} else if (frameTime < TARGET_MIN_FRAME_TIME) {
				// Very fast, increase aggressively
				consecutiveFastFrames++;
				if (consecutiveFastFrames > 3) {
					elementsPerFrame = (int)(elementsPerFrame * 1.5);
				}
			} else {
				// In target range, increase moderately
				elementsPerFrame = (int)(elementsPerFrame * 1.1);
				consecutiveFastFrames = 0;
			}

			// Clamp to estimated max for 15 fps
			elementsPerFrame = Math.max(MIN_ELEMENTS_PER_FRAME, Math.min(estimatedMaxElementsFor15Fps, elementsPerFrame));
		}

		@Override public void handle(long now) {
			final long frameTime = now - lastFrameTime;
			updateNumElementsPerFrame(frameTime);
			lastFrameTime = now;

			final boolean isThrottling = frameTime > TARGET_MAX_FRAME_TIME;
			final String status = isThrottling ? "THROTTLING" : "keeping up";
			LOG.trace("Frame time: {} ns ({} fps), elements per frame: {}, estimated max: {}, status: {}",
				frameTime,
				1_000_000_000L / Math.max(1, frameTime),
				elementsPerFrame,
				estimatedMaxElementsFor15Fps,
				status);
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
