package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import net.imglib2.util.Pair;

/**
 * Throttles mesh updates on the FX thread to keep the application responsive.
 *
 * If a lot of meshes are added at the same time, the application may freeze while they are being uploaded onto the GPU.
 * This class solves it by limiting the number of vertices+normals+faces that can be added to the scene at a time.
 */
public class MeshViewUpdateQueue<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static class MeshViewQueueEntry
	{
		private final Pair<MeshView, Node> meshAndBlockToAdd;
		private final Pair<Group, Group> meshAndBlockGroup;
		private final Runnable onCompleted;

		private MeshViewQueueEntry(
				final Pair<MeshView, Node> meshAndBlockToAdd,
				final Pair<Group, Group> meshAndBlockGroup,
				final Runnable onCompleted)
		{
			this.meshAndBlockToAdd = meshAndBlockToAdd;
			this.meshAndBlockGroup = meshAndBlockGroup;
			this.onCompleted = onCompleted;
		}
	}

	private final LinkedHashMap<ShapeKey<T>, MeshViewQueueEntry> queue = new LinkedHashMap<>();
	private final Timer timer = new Timer(true);

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
			final Runnable onCompleted)
	{
		final boolean queueWasEmpty = queue.isEmpty();
		queue.put(key, new MeshViewQueueEntry(meshAndBlockToAdd, meshAndBlockGroup, onCompleted));
		if (queueWasEmpty)
			scheduleTask();
	}

	/**
	 * Removes the request to add a mesh with a specified key from the queue if it exists.
	 * Returns {@code true} if the queue contained the given key.
	 *
	 * @param key
	 * @return
	 */
	public synchronized boolean removeFromQueue(final ShapeKey<T> key)
	{
		return queue.remove(key) != null;
	}

	public synchronized void update(final int numElementsPerFrame, final long frameDelayMsec)
	{
		this.numElementsPerFrame = numElementsPerFrame;
		this.frameDelayMsec = frameDelayMsec;
	}

	private synchronized void scheduleTask()
	{
		final TimerTask task = new TimerTask()
		{
			@Override
			public void run()
			{
				InvokeOnJavaFXApplicationThread.invoke(MeshViewUpdateQueue.this::addMeshImpl);
			}
		};

		timer.schedule(task, frameDelayMsec);
	}

	private void addMeshImpl()
	{
		assert Platform.isFxApplicationThread();

		final List<MeshViewQueueEntry> entriesToAdd = new ArrayList<>();
		int numElements = 0;

		synchronized (this)
		{
			for (final Iterator<MeshViewQueueEntry> it = queue.values().iterator(); it.hasNext() && numElements <= numElementsPerFrame && numElements != -1;)
			{
				final MeshViewQueueEntry nextQueueEntry = it.next();
				final MeshView nextMeshView = nextQueueEntry.meshAndBlockToAdd.getA();
				if (nextMeshView.getMesh() instanceof TriangleMesh)
				{
					final TriangleMesh nextMesh = (TriangleMesh) nextMeshView.getMesh();
					final int nextMeshNumVertices = nextMesh.getPoints().size() / nextMesh.getPointElementSize();
					final int nextMeshNumNormals = nextMesh.getNormals().size() / nextMesh.getNormalElementSize();
					final int nextMeshNumFaces = nextMesh.getFaces().size() / nextMesh.getFaceElementSize();
					final int nextNumElements = nextMeshNumVertices + nextMeshNumNormals + nextMeshNumFaces;
					if (entriesToAdd.isEmpty() || numElements + nextNumElements <= numElementsPerFrame)
					{
						numElements += nextNumElements;
						entriesToAdd.add(nextQueueEntry);
						it.remove();
					}
					else
					{
						break;
					}
				}
				else
				{
					numElements = -1;
					entriesToAdd.add(nextQueueEntry);
					it.remove();
				}
			}

			if (!queue.isEmpty())
				scheduleTask();
		}

		LOG.debug("Adding {} meshes which all together contain {} elements (vertices+normals+faces", entriesToAdd.size(), numElements);
		for (final MeshViewQueueEntry entryToAdd : entriesToAdd)
		{
			entryToAdd.meshAndBlockGroup.getA().getChildren().add(entryToAdd.meshAndBlockToAdd.getA());
			entryToAdd.meshAndBlockGroup.getB().getChildren().add(entryToAdd.meshAndBlockToAdd.getB());
			if (entryToAdd.onCompleted != null)
				entryToAdd.onCompleted.run();
		}
	}
}
