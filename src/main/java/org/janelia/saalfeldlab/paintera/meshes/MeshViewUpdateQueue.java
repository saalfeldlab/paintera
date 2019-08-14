package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
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
public class MeshViewUpdateQueue
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

	// TODO: turn this values into 3D viewer settings with reasonable limits
	private static final int MAX_ELEMENTS = 5000;
	private static final long DELAY_MSEC = 50;

	private final Queue<MeshViewQueueEntry> queue = new ArrayDeque<>();
	private final Timer timer = new Timer(true);

	public synchronized void addMesh(
			final Pair<MeshView, Node> meshAndBlockToAdd,
			final Pair<Group, Group> meshAndBlockGroup,
			final Runnable onCompleted)
	{
		final boolean queueWasEmpty = queue.isEmpty();
		queue.add(new MeshViewQueueEntry(meshAndBlockToAdd, meshAndBlockGroup, onCompleted));
		if (queueWasEmpty)
			scheduleTask();
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

		timer.schedule(task, DELAY_MSEC);
	}

	private void addMeshImpl()
	{
		assert Platform.isFxApplicationThread();

		final List<MeshViewQueueEntry> entriesToAdd = new ArrayList<>();
		int numElements = 0;

		synchronized (this)
		{
			while (!queue.isEmpty() && numElements <= MAX_ELEMENTS && numElements != -1)
			{
				final MeshView nextMeshView = queue.peek().meshAndBlockToAdd.getA();
				if (nextMeshView.getMesh() instanceof TriangleMesh)
				{
					final TriangleMesh nextMesh = (TriangleMesh) nextMeshView.getMesh();
					final int nextMeshNumVertices = nextMesh.getPoints().size() / nextMesh.getPointElementSize();
					final int nextMeshNumNormals = nextMesh.getNormals().size() / nextMesh.getNormalElementSize();
					final int nextMeshNumFaces = nextMesh.getFaces().size() / nextMesh.getFaceElementSize();
					final int nextNumElements = nextMeshNumVertices + nextMeshNumNormals + nextMeshNumFaces;
					if (entriesToAdd.isEmpty() || numElements + nextNumElements <= MAX_ELEMENTS)
					{
						numElements += nextNumElements;
						entriesToAdd.add(queue.poll());
					}
					else
					{
						break;
					}
				}
				else
				{
					numElements = -1;
					entriesToAdd.add(queue.poll());
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