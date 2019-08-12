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

	public synchronized void removeMesh(
			final Pair<MeshView, Node> meshAndBlockToAdd,
			final Pair<Group, Group> meshAndBlockGroup,
			final Runnable onCompleted)
	{
		final boolean queueWasEmpty = queue.isEmpty();
		queue.add(new MeshViewQueueEntry(meshAndBlockToAdd, meshAndBlockGroup, onCompleted));
		if (queueWasEmpty)
			scheduleTask();
	}

	private void scheduleTask()
	{
		final Object lock = this;
		final TimerTask task = new TimerTask()
		{
			@Override
			public void run()
			{
				InvokeOnJavaFXApplicationThread.invoke(() ->
				{
					final List<MeshViewQueueEntry> entriesToAdd = new ArrayList<>();
					int numElements = 0;

					synchronized (lock)
					{
//						System.out.println("** " + id + ": timer, num requests: " + meshRequestsQueue.size() + " **");

//						for (final Iterator<Pair<Boolean, MeshView>> it = queue.values().iterator(); it.hasNext();)
//						{
//							final Pair<Boolean, MeshView> request = it.next();
//							it.remove();
//
//							if (request.getA())
//							{
//								if (!meshesGroup.getChildren().contains(request.getB()))
//									meshesGroup.getChildren().add(request.getB());
//
//								final TriangleMesh mesh = (TriangleMesh) request.getB().getMesh();
//								final int numPoints = mesh.getPoints().size() / mesh.getPointElementSize();
//								final int numFaces = mesh.getFaces().size() / mesh.getFaceElementSize();
////								System.out.println(String.format("ID %s: added mesh with %d vertices and %d faces", id, numPoints, numFaces));
//
//								break; // only one addition at a time
//							}
//							else
//							{
//								meshesGroup.getChildren().remove(request.getB());
//								// continue to allow multiple removals at a time
//							}
//						}


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

//							if (queueValue.keysAndMeshesToAdd != null && !queueValue.keysAndMeshesToAdd.isEmpty())
//							{
//								onlyRemovals = false;
//
//							}
//
//							meshesGroup.getChildren().remove(queueValue.meshToRemove.getA());
//							blocksGroup.getChildren().remove(queueValue.meshToRemove.getB());
//						}



//						if (!queue.isEmpty())
//						{
//							scheduleTask();
////							System.out.println("queue size is " + meshRequestsQueue.size() + ", continue processing");
//						}
//						else
//						{
//							System.out.println(id + " queue is empty");
//						}
//					}
				});
			}
		};

		timer.schedule(task, DELAY_MSEC);
	}
}