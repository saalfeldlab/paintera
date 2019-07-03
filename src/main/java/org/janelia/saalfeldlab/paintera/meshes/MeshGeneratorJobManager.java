package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustumCulling;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.grids.Grids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.Affine3DHelpers;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class MeshGeneratorJobManager<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final class BlockEntry
	{
		public final Interval block;
		public final int scaleIndex;

		public BlockEntry(final Interval block, final int scaleIndex)
		{
			this.block = block;
			this.scaleIndex = scaleIndex;
		}
	}

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

				final Collection<BlockEntry> blockList = getBlocksToRender(blocks);

				synchronized (setNumberOfTasks)
				{
					setNumberOfTasks.accept(blockList.size());
					setNumberOfCompletedTasks.accept(0);
				}

				LOG.debug("Found {} blocks", blockList.size());

				if (this.isInterrupted)
				{
					LOG.debug("Got interrupted before building meshes -- returning");
					return;
				}

				LOG.debug("Generating mesh with {} blocks for id {}.", blockList.size(), this.identifier);

				synchronized (keys)
				{
					keys.clear();
					keys.addAll(blockList
							.stream()
							.map(blockEntry -> new ShapeKey<>(
										identifier,
										blockEntry.scaleIndex,
										simplificationIterations,
										smoothingLambda,
										smoothingIterations,
										Intervals.minAsLongArray(blockEntry.block),
										Intervals.maxAsLongArray(blockEntry.block)))
							.collect(Collectors.toSet())
						);

					// remove previously rendered blocks that are not needed anymore
					meshes.keySet().retainAll(keys);

					// remove pending blocks that are already rendered
					keys.removeAll(meshes.keySet());
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
												meshes.remove(key);
												meshes.put(key, mv);
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


		private Collection<BlockEntry> getBlocksToRender(final Set<HashWrapper<Interval>>[] blocks)
		{
			// get blocks containing the given label at all scale levels
			final CellGrid[] grids = source.getGrids();

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

			final List<BlockEntry> blocksToRender = new ArrayList<>();
			if (checkIfBlockSizesAreMultiples())
			{
				// use optimized block subdivision algorithm
				final Queue<BlockEntry> blocksQueue = new ArrayDeque<>();
				for (final HashWrapper<Interval> lowestResBlock : blocks[blocks.length - 1])
					blocksQueue.add(new BlockEntry(lowestResBlock.getData(), blocks.length - 1));

				while (!blocksQueue.isEmpty())
				{
					final BlockEntry blockEntry = blocksQueue.poll();
					if (viewFrustumCullingInSourceSpace[blockEntry.scaleIndex].intersects(blockEntry.block))
					{
						final double distanceFromCamera = viewFrustumCullingInSourceSpace[blockEntry.scaleIndex].distanceFromCamera(blockEntry.block);
						final double pixelSize = viewFrustum.pixelSize(distanceFromCamera);
						final double mipmapPixelSizeOnScreen = pixelSize * minMipmapPixelSize[blockEntry.scaleIndex];
						LOG.debug("scaleIndex={}, pixelSize={}, mipmapPixelSizeOnScreen={}", blockEntry.scaleIndex, pixelSize, mipmapPixelSizeOnScreen);

						if (blockEntry.scaleIndex > 0 && mipmapPixelSizeOnScreen > Math.pow(2, maxScaleIndex))
						{
							if (this.isInterrupted)
							{
								LOG.debug("Interrupted while building a list of blocks for rendering for label {}", identifier);
								break;
							}

							final int nextScaleIndex = blockEntry.scaleIndex - 1;
							final long[] blockPos = new long[blockEntry.block.numDimensions()];
							final CellGrid currentGrid = grids[blockEntry.scaleIndex], nextGrid = grids[nextScaleIndex];

							currentGrid.getCellPosition(Intervals.minAsLongArray(blockEntry.block), blockPos);
							final long currentBlockIndex = IntervalIndexer.positionToIndex(blockPos, grids[blockEntry.scaleIndex].getGridDimensions());

							final double[] relativeScales = DataSource.getRelativeScales(source, 0, blockEntry.scaleIndex, nextScaleIndex);
							final TLongSet nextBlockIndices = Grids.getRelevantBlocksInTargetGrid(
									new long[] {currentBlockIndex},
									currentGrid,
									nextGrid,
									relativeScales
								);

							for (final TLongIterator it = nextBlockIndices.iterator(); it.hasNext();)
							{
								final long nextBlockIndex = it.next();
								final Interval nextBlock = Grids.getCellInterval(nextGrid, nextBlockIndex);
								if (blocks[nextScaleIndex].contains(HashWrapper.interval(nextBlock)))
									blocksQueue.add(new BlockEntry(nextBlock, nextScaleIndex));
							}
						}
						else
						{
							blocksToRender.add(blockEntry);
						}
					}
				}
			}
			else
			{
				// less efficient block subdivision algorithm because blocks may intersect arbitrarily
				throw new UnsupportedOperationException("TODO");
			}

			final Map<Integer, Integer> scaleIndexToNumBlocks = new TreeMap<>();
			for (final BlockEntry blockEntry : blocksToRender)
				scaleIndexToNumBlocks.put(blockEntry.scaleIndex, scaleIndexToNumBlocks.getOrDefault(blockEntry.scaleIndex, 0) + 1);
			LOG.debug("Label ID {}: ", identifier, scaleIndexToNumBlocks);

			return blocksToRender;
		}

		private boolean checkIfBlockSizesAreMultiples()
		{
			final CellGrid[] blockGrids = source.getGrids();
			assert blockGrids.length > 0;
			final int[] blockSize = new int[blockGrids[0].numDimensions()];
			blockGrids[0].cellDimensions(blockSize);
			for (final CellGrid blockGrid : blockGrids)
			{
				for (int d = 0; d < blockSize.length; ++d)
				{
					final int largerSize  = Math.max(blockGrid.cellDimension(d), blockSize[d]);
					final int smallerSize = Math.min(blockGrid.cellDimension(d), blockSize[d]);
					if (largerSize % smallerSize != 0)
						return false;
				}
			}
			return true;
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
