package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.fxyz3d.shapes.polygon.PolygonMeshView;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.BlockTree.BlockTreeEntry;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustumCulling;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.Affine3DHelpers;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class MeshGeneratorJobManager<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<?, ?> source;

	private final Map<ShapeKey<T>, Future<?>> tasks = new HashMap<>();

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks;

	private final ExecutorService manager;

	private final ExecutorService workers;

	public MeshGeneratorJobManager(
			final DataSource<?, ?> source,
			final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks,
			final ExecutorService manager,
			final ExecutorService workers)
	{
		this.source = source;
		this.meshesAndBlocks = meshesAndBlocks;
		this.manager = manager;
		this.workers = workers;
	}

	public Pair<ManagementTask, CompletableFuture<Void>> submit(
			final DataSource<?, ?> source,
			final T identifier,
			final ViewFrustum viewFrustum,
			final int maxScaleIndex,
			final int simplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final InterruptibleFunction<T, Interval[]>[] getBlockLists,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes,
			final IntConsumer setNumberOfTasks,
			final IntConsumer setNumberOfCompletedTasks)
	{
		final ManagementTask task = new ManagementTask(
				source,
				identifier,
				viewFrustum,
				maxScaleIndex,
				simplificationIterations,
				smoothingLambda,
				smoothingIterations,
				getBlockLists,
				getMeshes,
				setNumberOfTasks,
				setNumberOfCompletedTasks
		);
		final CompletableFuture<Void> future = CompletableFuture.runAsync(task, manager);
		setNumberOfTasks.accept(MeshGenerator.SUBMITTED_MESH_GENERATION_TASK);
		return new ValuePair<>(task, future);
	}

	public class ManagementTask implements Runnable
	{
		final DataSource<?, ?> source;

		private final T identifier;

		private final ViewFrustum viewFrustum;

		private final int maxScaleIndex;

		private final int simplificationIterations;

		private final double smoothingLambda;

		private final int smoothingIterations;

		private final InterruptibleFunction<T, Interval[]>[] getBlockLists;

		private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes;

		private boolean isInterrupted = false;

		private final IntConsumer setNumberOfTasks;

		private final IntConsumer setNumberOfCompletedTasks;

		private ManagementTask(
				final DataSource<?, ?> source,
				final T identifier,
				final ViewFrustum viewFrustum,
				final int maxScaleIndex,
				final int simplificationIterations,
				final double smoothingLambda,
				final int smoothingIterations,
				final InterruptibleFunction<T, Interval[]>[] getBlockLists,
				final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes,
				final IntConsumer setNumberOfTasks,
				final IntConsumer setNumberOfCompletedTasks)
		{
			super();
			this.source = source;
			this.identifier = identifier;
			this.viewFrustum = viewFrustum;
			this.maxScaleIndex = maxScaleIndex;
			this.simplificationIterations = simplificationIterations;
			this.smoothingLambda = smoothingLambda;
			this.smoothingIterations = smoothingIterations;
			this.getBlockLists = getBlockLists;
			this.getMeshes = getMeshes;
			this.setNumberOfTasks = setNumberOfTasks;
			this.setNumberOfCompletedTasks = setNumberOfCompletedTasks;
		}

		public void interrupt()
		{
			LOG.debug("Interrupting for {} keys={}", this.identifier, tasks.keySet());
			this.isInterrupted = true;
			for (final InterruptibleFunction<T, Interval[]> getBlockList : this.getBlockLists)
				getBlockList.interruptFor(this.identifier);
			synchronized (tasks)
			{
				for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
					tasks.keySet().forEach(getMesh::interruptFor);
			}
		}

		@Override
		public void run()
		{
			final CountDownLatch countDownOnBlockList = new CountDownLatch(1);

			synchronized (setNumberOfTasks)
			{
				setNumberOfTasks.accept(MeshGenerator.RETRIEVING_RELEVANT_BLOCKS);
				setNumberOfCompletedTasks.accept(0);
			}

			@SuppressWarnings("unchecked")
			final Set<HashWrapper<Interval>>[] blocks = new Set[getBlockLists.length];
			workers.submit(() -> {
				try
				{
					for (int i = 0; i < getBlockLists.length; ++i)
					{
						blocks[i] = new HashSet<>(
								Arrays
									.stream(getBlockLists[i].apply(identifier))
									.map(HashWrapper::interval)
									.collect(Collectors.toSet())
							);
					}
				} finally
				{
					countDownOnBlockList.countDown();
				}
			});
			try
			{
				countDownOnBlockList.await();
			} catch (final InterruptedException e)
			{
				LOG.debug("Interrupted while waiting for block lists for label {}", identifier);
				this.isInterrupted = true;
				for (final InterruptibleFunction<T, Interval[]> getBlockList : this.getBlockLists)
					getBlockList.interruptFor(identifier);
			}

			final BlockTree blockTree = new BlockTree(source, blocks); // TODO: generate only once
			final Set<BlockTreeEntry> blocksToRender = getBlocksToRender(blockTree);

			if (this.isInterrupted)
			{
				LOG.debug("Got interrupted before building meshes -- returning");
				return;
			}

			final int numBlocksToRenderBeforeFiltering = blocksToRender.size();
			final RenderListFilter renderListFilter = new RenderListFilter(blockTree, blocksToRender);
			final Map<ShapeKey<T>, Map<ShapeKey<T>, Pair<MeshView, Node>>> lowResParentBlockToHighResContainedMeshes = new HashMap<>();

			int numHighResMeshesToRemove = 0;
			for (final Set<ShapeKey<T>> highResMeshesToRemove : renderListFilter.postponeRemovalHighRes.values())
				numHighResMeshesToRemove += highResMeshesToRemove.size();
			LOG.debug("blocksToRender before filtering={}, blocksToRender after filtering={}, low-res meshes to replace={}, high-res meshes to replace={}", numBlocksToRenderBeforeFiltering, blocksToRender.size(), renderListFilter.postponeRemovalLowRes.size(), numHighResMeshesToRemove);

			if (this.isInterrupted)
			{
				LOG.debug("Got interrupted before building meshes -- returning");
				return;
			}

			LOG.debug("Generating mesh with {} blocks for id {}.", blocksToRender.size(), this.identifier);

			synchronized (setNumberOfTasks)
			{
				setNumberOfTasks.accept(blocksToRender.size());
				setNumberOfCompletedTasks.accept(0);
			}

			if (!isInterrupted)
			{
				synchronized (tasks)
				{
					for (final BlockTreeEntry blockEntry : blocksToRender)
					{
						final ShapeKey<T> key = createShapeKey(blockEntry);
						tasks.put(key, workers.submit(() -> {
							final String initialName = Thread.currentThread().getName();
							try
							{
								final Future<?> currentFuture;
								synchronized (tasks)
								{
									currentFuture = tasks.get(key);
								}

								Thread.currentThread().setName(initialName + " -- generating mesh: " + key);
								LOG.trace(
										"Set name of current thread to {} ( was {})",
										Thread.currentThread().getName(),
										initialName
									);

								if (!isInterrupted && !currentFuture.isCancelled())
								{
									final Pair<float[], float[]> verticesAndNormals = getMeshes[key.scaleIndex()].apply(key);
									if (verticesAndNormals != null && !currentFuture.isCancelled())
									{
										final MeshView mv = makeMeshView(verticesAndNormals);
										final Node blockShape = createBlockShape(key);
										LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);
										synchronized (meshesAndBlocks)
										{
											if (!isInterrupted && !currentFuture.isCancelled())
											{
												final BlockTreeEntry entry = blockTree.find(key.interval(), key.scaleIndex());

												if (renderListFilter.postponeRemovalHighRes.containsKey(entry))
												{
													final Set<ShapeKey<T>> meshesToRemove = renderListFilter.postponeRemovalHighRes.get(entry);
													renderListFilter.postponeRemovalHighRes.remove(entry);
													meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
													meshesAndBlocks.keySet().removeAll(meshesToRemove);
												}
												else if (renderListFilter.postponeRemovalLowResParents.containsKey(entry))
												{
													final ShapeKey<T> entryParentKey = renderListFilter.postponeRemovalLowResParents.get(entry);
													final Set<BlockTreeEntry> blocksToRenderBeforeRemovingMesh = renderListFilter.postponeRemovalLowRes.get(entryParentKey);
													blocksToRenderBeforeRemovingMesh.remove(entry);
													renderListFilter.postponeRemovalLowResParents.remove(entry);

													if (!lowResParentBlockToHighResContainedMeshes.containsKey(entryParentKey))
														lowResParentBlockToHighResContainedMeshes.put(entryParentKey, new HashMap<>());
													lowResParentBlockToHighResContainedMeshes.get(entryParentKey).put(key, new ValuePair<>(mv, blockShape));

													if (blocksToRenderBeforeRemovingMesh.isEmpty())
													{
														renderListFilter.postponeRemovalLowRes.remove(entryParentKey);
														meshesAndBlocks.putAll(lowResParentBlockToHighResContainedMeshes.get(entryParentKey));
														meshesAndBlocks.remove(entryParentKey);
														lowResParentBlockToHighResContainedMeshes.remove(entryParentKey);
													}
												}
												else
												{
													meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
												}
											}
										}
									}
								}
							}
							catch (final Exception e)
							{
								LOG.debug("Was not able to retrieve mesh for {}: {}", key, e);
							}
							finally
							{
								synchronized (tasks)
								{
									tasks.remove(key);
								}
								Thread.currentThread().setName(initialName);

								if (!isInterrupted)
								{
									synchronized (setNumberOfTasks)
									{
//										setNumberOfCompletedTasks.accept(numTasks - (int) countDownOnMeshes.getCount());
									}
								}
							}
						}));
					}
				}
			}
		}


		private Set<BlockTreeEntry> getBlocksToRender(final BlockTree blockTree)
		{
			final AffineTransform3D cameraToWorldTransform = viewFrustum.eyeToWorldTransform();
			final ViewFrustumCulling[] viewFrustumCullingInSourceSpace = new ViewFrustumCulling[source.getNumMipmapLevels()];
			final double[] minMipmapPixelSize = new double[source.getNumMipmapLevels()];
			for (int i = 0; i < viewFrustumCullingInSourceSpace.length; ++i)
			{
				final AffineTransform3D sourceToWorldTransform = new AffineTransform3D();
				source.getSourceTransform(0, i, sourceToWorldTransform);

				final AffineTransform3D cameraToSourceTransform = new AffineTransform3D();
				cameraToSourceTransform.preConcatenate(cameraToWorldTransform).preConcatenate(sourceToWorldTransform.inverse());

				viewFrustumCullingInSourceSpace[i] = new ViewFrustumCulling(viewFrustum, cameraToSourceTransform);

				final double[] extractedScale = new double[3];
				Arrays.setAll(extractedScale, d -> Affine3DHelpers.extractScale(cameraToSourceTransform.inverse(), d));

				minMipmapPixelSize[i] = Arrays.stream(extractedScale).min().getAsDouble();
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
					final double pixelSize = viewFrustum.pixelSize(distanceFromCamera);
					final double mipmapPixelSizeOnScreen = pixelSize * minMipmapPixelSize[blockEntry.scaleLevel];
					LOG.debug("scaleIndex={}, pixelSize={}, mipmapPixelSizeOnScreen={}", blockEntry.scaleLevel, pixelSize, mipmapPixelSizeOnScreen);

					if (blockEntry.scaleLevel > 0 && mipmapPixelSizeOnScreen > Math.pow(2, maxScaleIndex))
					{
						if (this.isInterrupted)
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
					else
					{
						blocksToRender.add(blockEntry);
					}
				}
			}

			final Map<Integer, Integer> scaleIndexToNumBlocks = new TreeMap<>();
			for (final BlockTreeEntry blockEntry : blocksToRender)
				scaleIndexToNumBlocks.put(blockEntry.scaleLevel, scaleIndexToNumBlocks.getOrDefault(blockEntry.scaleLevel, 0) + 1);
			LOG.debug("Label ID {}: ", identifier, scaleIndexToNumBlocks);

			return blocksToRender;
		}


		private final class RenderListFilter
		{
			/**
			 * A mapping from a pending low-res block to a set of existing high-res blocks.
			 * The existing high-res blocks (mapped values) need to be removed from the scene once the pending low-res block (key) is ready.
			 */
			final Map<BlockTreeEntry, Set<ShapeKey<T>>> postponeRemovalHighRes = new HashMap<>();

			/**
			 * A mapping from an existing low-res block to a set of pending high-res blocks.
			 * The existing low-res block (key) needs to be removed from the scene once the entire set of pending high-res blocks (mapped values) is ready.
			 */
			final Map<ShapeKey<T>, Set<BlockTreeEntry>> postponeRemovalLowRes = new HashMap<>();

			/**
			 * Helper mapping from a pending high-res block to its existing low-res parent block
			 * (essentially the inverse mapping of {@link #postponeRemovalLowRes}).
			 */
			final Map<BlockTreeEntry, ShapeKey<T>> postponeRemovalLowResParents = new HashMap<>();

			public RenderListFilter(final BlockTree blockTree, final Set<BlockTreeEntry> blocksToRender)
			{
				final Set<ShapeKey<T>> keysToRender = new HashSet<>();
				for (final BlockTreeEntry blockEntry : blocksToRender)
					keysToRender.add(createShapeKey(blockEntry));

				synchronized (tasks)
				{
					// interrupt and remove tasks for rendering blocks that are not needed anymore
					tasks.keySet().stream()
						.filter(key -> !keysToRender.contains(key))
						.map(tasks::remove)
						.forEach(task -> task.cancel(true));

					// filter out pending blocks that are already being processed
					blocksToRender.removeIf(blockEntry -> tasks.containsKey(createShapeKey(blockEntry)));
				}

				if (blocksToRender.isEmpty())
					return;

				synchronized (meshesAndBlocks)
				{
					final Map<ShapeKey<T>, BlockTreeEntry> meshKeysToEntries = new HashMap<>();
					final Map<BlockTreeEntry, ShapeKey<T>> meshEntriesToKeys = new HashMap<>();
					for (final ShapeKey<T> meshKey : meshesAndBlocks.keySet())
					{
						final BlockTreeEntry meshEntry = blockTree.find(meshKey.interval(), meshKey.scaleIndex());
						meshKeysToEntries.put(meshKey, meshEntry);
						meshEntriesToKeys.put(meshEntry, meshKey);
					}

					final Set<ShapeKey<T>> meshKeysToKeep = new HashSet<>();

					for (final Entry<ShapeKey<T>, BlockTreeEntry> meshKeyAndEntry : meshKeysToEntries.entrySet())
					{
						final ShapeKey<T> meshKey = meshKeyAndEntry.getKey();
						final BlockTreeEntry meshEntry = meshKeyAndEntry.getValue();
						if (meshEntry == null)
							continue;

						if (blocksToRender.contains(meshEntry))
						{
							blocksToRender.remove(meshEntry);
							meshKeysToKeep.add(meshKey);
							continue;
						}

						// check if needed to render block at lower resolution than currently displayed
						BlockTreeEntry parentEntry = meshEntry;
						while (parentEntry != null)
						{
							if (blocksToRender.contains(parentEntry))
							{
								// need to render block at lower resolution, postpone removal of meshes at higher resolution
								if (!postponeRemovalHighRes.containsKey(parentEntry))
									postponeRemovalHighRes.put(parentEntry, new HashSet<>());
								postponeRemovalHighRes.get(parentEntry).add(meshKey);
								meshKeysToKeep.add(meshKey);
								break;
							}
							parentEntry = blockTree.getParent(parentEntry);
						}
					}

					for (final BlockTreeEntry blockEntry : blocksToRender)
					{
						// check if needed to render block at higher resolution than currently displayed
						BlockTreeEntry parentEntry = blockEntry;
						while (parentEntry != null)
						{
							if (meshEntriesToKeys.containsKey(parentEntry))
							{
								// need to render block at higher resolution, postpone removal of meshes at lower resolution
								final ShapeKey<T> parentMeshKey = meshEntriesToKeys.get(parentEntry);
								if (!postponeRemovalLowRes.containsKey(parentMeshKey))
									postponeRemovalLowRes.put(parentMeshKey, new HashSet<>());
								postponeRemovalLowRes.get(parentMeshKey).add(blockEntry);
								postponeRemovalLowResParents.put(blockEntry, parentMeshKey);
								meshKeysToKeep.add(parentMeshKey);
								break;
							}
							parentEntry = blockTree.getParent(parentEntry);
						}
					}

					meshesAndBlocks.keySet().retainAll(meshKeysToKeep);
				}
			}
		}

		private ShapeKey<T> createShapeKey(final BlockTreeEntry blockEntry)
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
		final PhongMaterial material = new PhongMaterial();
		material.setSpecularColor(new Color(1, 1, 1, 1.0));
		material.setSpecularPower(50);
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

		final PolygonMeshView box = new PolygonMeshView(MeshUtils.createQuadrilateralMesh(
				blockInterval.dimension(0),
				blockInterval.dimension(1),
				blockInterval.dimension(2)
			));

		box.setTranslateX(blockInterval.min(0) + blockInterval.dimension(0) / 2);
		box.setTranslateY(blockInterval.min(1) + blockInterval.dimension(1) / 2);
		box.setTranslateZ(blockInterval.min(2) + blockInterval.dimension(2) / 2);

		final PhongMaterial material = new PhongMaterial();
		material.setSpecularColor(Color.WHITE);
		material.setSpecularPower(100);

		box.setCullFace(CullFace.NONE);
		box.setMaterial(material);
		box.setDrawMode(DrawMode.LINE);

		return box;
	}
}
