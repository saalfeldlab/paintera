package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

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
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class MeshGeneratorJobManager<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ObservableMap<ShapeKey<T>, MeshView> meshes;

	private final ExecutorService manager;

	private final ExecutorService workers;

	public MeshGeneratorJobManager(
			final ObservableMap<ShapeKey<T>, MeshView> meshes,
			final ExecutorService manager,
			final ExecutorService workers)
	{
		super();
		this.meshes = meshes;
		this.manager = manager;
		this.workers = workers;
	}

	public Pair<CompletableFuture<Void>, ManagementTask> submit(
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
			final IntConsumer setNumberOfCompletedTasks,
			final Runnable onFinish)
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
				setNumberOfCompletedTasks,
				onFinish
		);
		final CompletableFuture<Void> future = CompletableFuture.runAsync(task, manager);
		setNumberOfTasks.accept(MeshGenerator.SUBMITTED_MESH_GENERATION_TASK);
		return new ValuePair<>(future, task);
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

		private final Runnable onFinish;

		private final Set<ShapeKey<T>> keys = new HashSet<>();

		public ManagementTask(
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
				final IntConsumer setNumberOfCompletedTasks,
				final Runnable onFinish)
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
			this.onFinish = onFinish;
		}

		public void interrupt()
		{
			LOG.debug("Interrupting for {} keys={}", this.identifier, this.keys);
			this.isInterrupted = true;
			for (final InterruptibleFunction<T, Interval[]> getBlockList : this.getBlockLists)
				getBlockList.interruptFor(this.identifier);
			synchronized (this.keys)
			{
				for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
					this.keys.forEach(getMesh::interruptFor);
			}
		}

		@Override
		public void run()
		{
			try
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
				final Map<ShapeKey<T>, Map<ShapeKey<T>, MeshView>> parentToPostponeAddMeshView = new HashMap<>();

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

				synchronized (keys)
				{
					keys.clear();
					for (final BlockTreeEntry blockEntry : blocksToRender)
						keys.add(createShapeKey(blockEntry));
				}

				if (!isInterrupted)
				{

					final int            numTasks          = keys.size();
					final CountDownLatch countDownOnMeshes = new CountDownLatch(numTasks);

					final ArrayList<Callable<Void>> tasks = new ArrayList<>();

					for (final ShapeKey<T> key : keys)
					{
						tasks.add(() -> {
							try
							{
								final String initialName = Thread.currentThread().getName();
								try
								{
									Thread.currentThread().setName(initialName + " -- generating mesh: " + key);
									LOG.trace(
											"Set name of current thread to {} ( was {})",
											Thread.currentThread().getName(),
											initialName
									         );
									if (!isInterrupted)
									{
										final Pair<float[], float[]> verticesAndNormals = getMeshes[key.scaleIndex()].apply(key);
										final MeshView               mv                 = makeMeshView(verticesAndNormals);
										LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);
										synchronized (meshes)
										{
											if (!isInterrupted)
											{
												final BlockTreeEntry entry = blockTree.find(key.interval(), key.scaleIndex());

												if (renderListFilter.postponeRemovalHighRes.containsKey(entry))
												{
													final Set<ShapeKey<T>> meshesToRemove = renderListFilter.postponeRemovalHighRes.get(entry);
													renderListFilter.postponeRemovalHighRes.remove(entry);
													meshes.keySet().removeAll(meshesToRemove);
													meshes.put(key, mv);
												}
												else if (renderListFilter.postponeRemovalLowResParents.containsKey(entry))
												{
													final ShapeKey<T> entryParentKey = renderListFilter.postponeRemovalLowResParents.get(entry);
													final Set<BlockTreeEntry> blocksToRenderBeforeRemovingMesh = renderListFilter.postponeRemovalLowRes.get(entryParentKey);
													blocksToRenderBeforeRemovingMesh.remove(entry);
													renderListFilter.postponeRemovalLowResParents.remove(entry);

													if (!parentToPostponeAddMeshView.containsKey(entryParentKey))
														parentToPostponeAddMeshView.put(entryParentKey, new HashMap<>());
													parentToPostponeAddMeshView.get(entryParentKey).put(key, mv);

													if (blocksToRenderBeforeRemovingMesh.isEmpty())
													{
														renderListFilter.postponeRemovalLowRes.remove(entryParentKey);
														meshes.remove(entryParentKey);
														meshes.putAll(parentToPostponeAddMeshView.get(entryParentKey));
														parentToPostponeAddMeshView.remove(entryParentKey);
													}
												}
												else
												{
													meshes.put(key, mv);
												}
											}
										}
									}
								} catch (final RuntimeException e)
								{
									LOG.debug("Was not able to retrieve mesh for {}: {}", key, e);
								} finally
								{
									Thread.currentThread().setName(initialName);
								}
								return null;
							} finally
							{
								synchronized (setNumberOfTasks)
								{
									countDownOnMeshes.countDown();
									if (!isInterrupted)
									{
										setNumberOfCompletedTasks.accept(numTasks - (int) countDownOnMeshes.getCount
												());
									}
								}
								LOG.debug("Counted down latch. {} remaining", countDownOnMeshes.getCount());
							}

						});
					}

					try
					{
						workers.invokeAll(tasks);
					} catch (final InterruptedException e)
					{
						this.isInterrupted = true;
						for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
							keys.forEach(getMesh::interruptFor);
					}

					try
					{
						if (this.isInterrupted)
						{
							for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
								keys.forEach(getMesh::interruptFor);
						}
						else
						{
							countDownOnMeshes.await();
						}
					} catch (final InterruptedException e)
					{
						LOG.debug(
								"Current thread was interrupted while waiting for mesh count down latch ({} " +
										"remaining)",
								countDownOnMeshes.getCount()
						         );
						synchronized (getMeshes)
						{
							this.isInterrupted = true;
							for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
								keys.forEach(getMesh::interruptFor);
						}
					}
				}
			}
			finally
			{
				this.onFinish.run();
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
			final Map<BlockTreeEntry, Set<ShapeKey<T>>> postponeRemovalHighRes = new HashMap<>();

			final Map<ShapeKey<T>, Set<BlockTreeEntry>> postponeRemovalLowRes = new HashMap<>();
			final Map<BlockTreeEntry, ShapeKey<T>> postponeRemovalLowResParents = new HashMap<>();

			public RenderListFilter(final BlockTree blockTree, final Set<BlockTreeEntry> blocksToRender)
			{
				synchronized (meshes)
				{
					final Map<ShapeKey<T>, BlockTreeEntry> meshKeysToEntries = new HashMap<>();
					final Map<BlockTreeEntry, ShapeKey<T>> meshEntriesToKeys = new HashMap<>();
					for (final ShapeKey<T> meshKey : meshes.keySet())
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

					meshes.keySet().retainAll(meshKeysToKeep);
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

}
