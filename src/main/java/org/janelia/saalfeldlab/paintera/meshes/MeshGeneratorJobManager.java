package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.BlockTree.BlockTreeEntry;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustumCulling;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.grids.Grids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.Affine3DHelpers;
import eu.mihosoft.jcsg.ext.openjfx.shape3d.PolygonMeshView;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import javafx.beans.property.IntegerProperty;
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
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class MeshGeneratorJobManager<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<?, ?> source;

	private final T identifier;

	private final Map<ShapeKey<T>, Future<?>> tasks = new HashMap<>();

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks;

	private final InterruptibleFunction<T, Interval[]>[] getBlockLists;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes;

	private final ExecutorService manager;

	private final ExecutorService workers;

	private final IntegerProperty numPendingTasks;

	private final IntegerProperty numCompletedTasks;

	private final int rendererBlockSize;

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	public MeshGeneratorJobManager(
			final DataSource<?, ?> source,
			final T identifier,
			final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks,
			final InterruptibleFunction<T, Interval[]>[] getBlockLists,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] getMeshes,
			final ExecutorService manager,
			final ExecutorService workers,
			final IntegerProperty numPendingTasks,
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
		this.numPendingTasks = numPendingTasks;
		this.numCompletedTasks = numCompletedTasks;
		this.rendererBlockSize = rendererBlockSize;
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
		manager.submit(new ManagementTask(
				preferredScaleIndex,
				highestScaleIndex,
				simplificationIterations,
				smoothingLambda,
				smoothingIterations,
				viewFrustum,
				eyeToWorldTransform
			));
	}

	public void interrupt()
	{
		isInterrupted.set(true);

		LOG.debug("Interrupting for {} keys={}", this.identifier, tasks.keySet());
		for (final InterruptibleFunction<T, Interval[]> getBlockList : this.getBlockLists)
			getBlockList.interruptFor(this.identifier);

		synchronized (tasks)
		{
			tasks.values().forEach(future -> future.cancel(true));

			for (final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>> getMesh : this.getMeshes)
				tasks.keySet().forEach(getMesh::interruptFor);
		}
	}

	private class ManagementTask implements Runnable
	{
		private final int preferredScaleIndex;

		private final int highestScaleIndex;

		private final int simplificationIterations;

		private final double smoothingLambda;

		private final int smoothingIterations;

		private final ViewFrustum viewFrustum;

		private final AffineTransform3D eyeToWorldTransform;

		private ManagementTask(
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

		@Override
		public void run()
		{
			try {

			 // TODO: save previously created tree and re-use it if the set of blocks hasn't changed (i.e. affected blocks in the canvas haven't changed)
			final BlockTree rendererBlockTree = createRendererBlockTree();
			final Set<BlockTreeEntry> blocksToRender = getBlocksToRender(rendererBlockTree);

			if (isInterrupted.get())
			{
				LOG.debug("Got interrupted before building meshes -- returning");
				return;
			}

			final int numBlocksToRenderBeforeFiltering = blocksToRender.size();
			final RenderListFilter renderListFilter = new RenderListFilter(rendererBlockTree, blocksToRender);

			// Temporary storage for new rendered high-res blocks that will be added onto the scene in bulk once their low-res parent block can be fully replaced
			final Map<ShapeKey<T>, Map<ShapeKey<T>, Pair<MeshView, Node>>> lowResParentBlockToHighResContainedMeshes = new HashMap<>();

			int numHighResMeshesToRemove = 0;
			for (final Set<ShapeKey<T>> highResMeshesToRemove : renderListFilter.postponeRemovalHighRes.values())
				numHighResMeshesToRemove += highResMeshesToRemove.size();
			LOG.debug("blocksToRender before filtering={}, blocksToRender after filtering={}, low-res meshes to replace={}, high-res meshes to replace={}", numBlocksToRenderBeforeFiltering, blocksToRender.size(), renderListFilter.postponeRemovalLowRes.size(), numHighResMeshesToRemove);

			if (isInterrupted.get())
			{
				LOG.debug("Got interrupted before building meshes -- returning");
				return;
			}

			LOG.debug("Generating mesh with {} blocks for id {}.", blocksToRender.size(), identifier);

			synchronized (numCompletedTasks) {
				numCompletedTasks.set(numBlocksToRenderBeforeFiltering - blocksToRender.size() - tasks.size());
			}

			if (!isInterrupted.get())
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

								if (!isInterrupted.get() && !currentFuture.isCancelled())
								{
									final Pair<float[], float[]> verticesAndNormals = getMeshes[key.scaleIndex()].apply(key);
									if (!currentFuture.isCancelled() && verticesAndNormals != null)
									{
										final boolean nonEmptyMesh = Math.max(verticesAndNormals.getA().length, verticesAndNormals.getB().length) > 0;
										final MeshView mv = nonEmptyMesh ? makeMeshView(verticesAndNormals) : null;
										final Node blockShape = nonEmptyMesh ? createBlockShape(key) : null;
										LOG.debug("Found {}/3 vertices and {}/3 normals", verticesAndNormals.getA().length, verticesAndNormals.getB().length);
										synchronized (meshesAndBlocks)
										{
											if (!isInterrupted.get() && !currentFuture.isCancelled())
											{
												final BlockTreeEntry entry = rendererBlockTree.find(key.interval(), key.scaleIndex());

												if (renderListFilter.postponeRemovalHighRes.containsKey(entry))
												{
													// new low-res block replaces a set of existing high-res blocks
													final Set<ShapeKey<T>> meshesToRemove = renderListFilter.postponeRemovalHighRes.remove(entry);
													if (nonEmptyMesh)
														meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
													meshesAndBlocks.keySet().removeAll(meshesToRemove);
												}
												else if (renderListFilter.postponeRemovalLowResParents.containsKey(entry))
												{
													// new high-res block is part of a group that will replace an existing low-res block
													final ShapeKey<T> entryParentKey = renderListFilter.postponeRemovalLowResParents.get(entry);
													final Set<BlockTreeEntry> blocksToRenderBeforeRemovingMesh = renderListFilter.postponeRemovalLowRes.get(entryParentKey);

													blocksToRenderBeforeRemovingMesh.remove(entry);
													renderListFilter.postponeRemovalLowResParents.remove(entry);

													if (!lowResParentBlockToHighResContainedMeshes.containsKey(entryParentKey))
														lowResParentBlockToHighResContainedMeshes.put(entryParentKey, new HashMap<>());

													if (nonEmptyMesh)
														lowResParentBlockToHighResContainedMeshes.get(entryParentKey).put(key, new ValuePair<>(mv, blockShape));

													if (blocksToRenderBeforeRemovingMesh.isEmpty())
													{
														renderListFilter.postponeRemovalLowRes.remove(entryParentKey);
														meshesAndBlocks.putAll(lowResParentBlockToHighResContainedMeshes.remove(entryParentKey));
														meshesAndBlocks.remove(entryParentKey);
													}
												}
												else if (nonEmptyMesh)
												{
													meshesAndBlocks.put(key, new ValuePair<>(mv, blockShape));
												}

												synchronized (numCompletedTasks) {
													numCompletedTasks.set(numCompletedTasks.get() + 1);
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

									synchronized (numPendingTasks) {
										numPendingTasks.set(tasks.size());
									}
								}
								Thread.currentThread().setName(initialName);
							}
						}));
					}

					synchronized (numPendingTasks) {
						numPendingTasks.set(tasks.size());
					}
				}
			}

			} catch (final Exception e) {
				e.printStackTrace();
			}
		}

		private BlockTree createRendererBlockTree()
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

			final long[][] sourceDimensions = new long[source.getNumMipmapLevels()][];
			Arrays.setAll(sourceDimensions, i -> source.getGrid(i).getImgDimensions());

			final double[][] sourceScales = new double[source.getNumMipmapLevels()][];
			Arrays.setAll(sourceScales, i -> DataSource.getScale(source, 0, i));

			final int[][] rendererFullBlockSizes = getRendererFullBlockSizes(rendererBlockSize, sourceScales);
			LOG.debug("Source scales: {}, renderer block sizes: {}", sourceScales, rendererFullBlockSizes);

			// Create new block grids with renderer block size based on source blocks
			@SuppressWarnings("unchecked")
			final Set<HashWrapper<Interval>>[] rendererBlocks = new Set[sourceBlocks.length];
			for (int i = 0; i < rendererBlocks.length; ++i)
			{
				rendererBlocks[i] = new HashSet<>();
				final CellGrid rendererBlockGrid = new CellGrid(sourceDimensions[i], rendererFullBlockSizes[i]);
				for (final HashWrapper<Interval> sourceBlock : sourceBlocks[i])
				{
					final long[] intersectingRendererBlocksIndices = Grids.getIntersectingBlocks(sourceBlock.getData(), rendererBlockGrid);
					for (final long intersectingRendererBlocksIndex : intersectingRendererBlocksIndices)
					{
						final Interval intersectingRendererBlock = Grids.getCellInterval(rendererBlockGrid, intersectingRendererBlocksIndex);
						rendererBlocks[i].add(HashWrapper.interval(intersectingRendererBlock));
					}
				}
			}

			return new BlockTree(rendererBlocks, sourceDimensions, rendererFullBlockSizes, sourceScales);
		}

		private Set<BlockTreeEntry> getBlocksToRender(final BlockTree blockTree)
		{
			final ViewFrustumCulling[] viewFrustumCullingInSourceSpace = new ViewFrustumCulling[source.getNumMipmapLevels()];
			final double[] minMipmapPixelSize = new double[source.getNumMipmapLevels()];
			final double[] maxRelativeScaleFactors = new double[source.getNumMipmapLevels()];
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

			final Set<BlockTreeEntry> blocksToRender = new HashSet<>();

			final Queue<BlockTreeEntry> blocksQueue = new ArrayDeque<>();
			blocksQueue.addAll(blockTree.getTreeLevel(blockTree.getNumLevels() - 1).valueCollection());

			while (!blocksQueue.isEmpty())
			{
				final BlockTreeEntry blockEntry = blocksQueue.poll();
				if (viewFrustumCullingInSourceSpace[blockEntry.scaleLevel].intersects(blockEntry.interval()))
				{
					final double distanceFromCamera = viewFrustumCullingInSourceSpace[blockEntry.scaleLevel].distanceFromCamera(blockEntry.interval());
					final double screenSizeToViewPlaneRatio = viewFrustum.screenSizeToViewPlaneRatio(distanceFromCamera);
					final double screenPixelSize = screenSizeToViewPlaneRatio * minMipmapPixelSize[blockEntry.scaleLevel];
					LOG.debug("scaleIndex={}, screenSizeToViewPlaneRatio={}, screenPixelSize={}", blockEntry.scaleLevel, screenSizeToViewPlaneRatio, screenPixelSize);

					if (blockEntry.scaleLevel > highestScaleIndex && screenPixelSize > maxRelativeScaleFactors[preferredScaleIndex])
					{
						if (isInterrupted.get())
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
					final List<ShapeKey<T>> taskKeysToInterrupt = tasks.keySet().stream()
						.filter(key -> !keysToRender.contains(key))
						.collect(Collectors.toList());

					for (final ShapeKey<T> taskKeyToInterrupt : taskKeysToInterrupt)
					{
						getMeshes[taskKeyToInterrupt.scaleIndex()].interruptFor(taskKeyToInterrupt);
						Optional.ofNullable(tasks.remove(taskKeyToInterrupt)).ifPresent(task -> task.cancel(true));
					}

					// filter out pending blocks that are already being processed
					blocksToRender.removeIf(blockEntry -> tasks.containsKey(createShapeKey(blockEntry)));
				}

				synchronized (meshesAndBlocks)
				{
					final Map<BlockTreeEntry, ShapeKey<T>> existingMeshEntriesToKeys = new HashMap<>();
					for (final ShapeKey<T> meshKey : meshesAndBlocks.keySet())
					{
						final BlockTreeEntry meshEntry = blockTree.find(meshKey.interval(), meshKey.scaleIndex());
						existingMeshEntriesToKeys.put(meshEntry, meshKey);
					}

					final Set<ShapeKey<T>> existingMeshKeysToKeep = new HashSet<>();

					for (final Entry<BlockTreeEntry, ShapeKey<T>> existingMeshEntryAndKey : existingMeshEntriesToKeys.entrySet())
					{
						final ShapeKey<T> existingMeshKey = existingMeshEntryAndKey.getValue();
						final BlockTreeEntry existingMeshEntry = existingMeshEntryAndKey.getKey();

						// check if the requested block already exists in the scene
						if (keysToRender.contains(existingMeshKey))
						{
							blocksToRender.remove(existingMeshEntry);
							existingMeshKeysToKeep.add(existingMeshKey);
							continue;
						}

						// check if needed to render block at lower resolution than currently displayed
						BlockTreeEntry parentEntry = existingMeshEntry;
						while (parentEntry != null)
						{
							if (blocksToRender.contains(parentEntry))
							{
								// need to render block at lower resolution, postpone removal of meshes at higher resolution
								if (!postponeRemovalHighRes.containsKey(parentEntry))
									postponeRemovalHighRes.put(parentEntry, new HashSet<>());
								postponeRemovalHighRes.get(parentEntry).add(existingMeshKey);
								existingMeshKeysToKeep.add(existingMeshKey);
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
							if (existingMeshEntriesToKeys.containsKey(parentEntry))
							{
								// need to render block at higher resolution, postpone removal of meshes at lower resolution
								final ShapeKey<T> existingParentMeshKey = existingMeshEntriesToKeys.get(parentEntry);
								if (!postponeRemovalLowRes.containsKey(existingParentMeshKey))
									postponeRemovalLowRes.put(existingParentMeshKey, new HashSet<>());
								postponeRemovalLowRes.get(existingParentMeshKey).add(blockEntry);
								postponeRemovalLowResParents.put(blockEntry, existingParentMeshKey);
								existingMeshKeysToKeep.add(existingParentMeshKey);
								break;
							}
							parentEntry = blockTree.getParent(parentEntry);
						}
					}

					// remove blocks from the scene that are not needed anymore
					meshesAndBlocks.keySet().retainAll(existingMeshKeysToKeep);
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
				final int clampedBlockSize = Math.max(
						Viewer3DConfig.RENDERER_BLOCK_SIZE_MIN_VALUE, Math.min(
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
