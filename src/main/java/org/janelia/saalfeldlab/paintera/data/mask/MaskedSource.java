package org.janelia.saalfeldlab.paintera.data.mask;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg.LazyCells;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.Type;
import net.imglib2.type.label.Label;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.AccessedBlocksRandomAccessible;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleTriple;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmp.bdv.img.cache.VolatileCachedCellImg;

public class MaskedSource<D extends Type<D>, T extends Type<T>> implements DataSource<D, T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int NUM_DIMENSIONS = 3;

	private final DataSource<D, T> source;

	private final CachedCellImg<UnsignedLongType, LongAccess>[] dataCanvases;

	private final RandomAccessibleInterval<VolatileUnsignedLongType>[] canvases;

	private final HashMap<RandomAccessibleInterval<UnsignedLongType>, MaskInfo<UnsignedLongType>> masks;

	private final RandomAccessible<UnsignedLongType>[] dMasks;

	private final RandomAccessible<VolatileUnsignedLongType>[] tMasks;

	private final PickAndConvert<D, UnsignedLongType, UnsignedLongType, D> pacD;

	private final PickAndConvert<T, VolatileUnsignedLongType, VolatileUnsignedLongType, T> pacT;

	private final D extensionD;

	private final T extensionT;

	private final ExecutorService propagationExecutor;

	// TODO make sure that BB is handled properly in multi scale case!!!
	private final TLongSet affectedBlocks = new TLongHashSet();

	private final BiConsumer<CachedCellImg<UnsignedLongType, ?>, long[]> persistCanvas;

	private final StringProperty cacheDirectory = new SimpleStringProperty();

	private final Supplier<String> nextCacheDirectory;

	private final long[][] dimensions;

	private final int[][] blockSizes;

	private final BooleanProperty isMaskDeployed = new SimpleBooleanProperty(false);

	private final IntegerProperty maskApplyCount = new SimpleIntegerProperty(0);

	private final BooleanBinding noMasksCurrentlyApplied = maskApplyCount.isEqualTo(0);

	private final BooleanProperty isPersisting = new SimpleBooleanProperty(false);

	private final ObservableBooleanValue isMaskNotDeployed = isMaskDeployed.not();

	private final ObservableBooleanValue isNotPersisting = isPersisting.not();

	private final Map<Long, TLongHashSet>[] affectedBlocksByLabel;

	private final List<Runnable> canvasClearedListeners = new ArrayList<>();

	private final ObservableBooleanValue canBePersited = Bindings.createBooleanBinding(
			() -> isMaskNotDeployed.get() && isNotPersisting.get() && noMasksCurrentlyApplied.get(),
			isNotPersisting,
			isMaskNotDeployed,
			maskApplyCount
	                                                                                  );

	private final ObservableBooleanValue canGenerateMask = Bindings.createBooleanBinding(
			() -> isNotPersisting.get() && isMaskNotDeployed.get(),
			isNotPersisting,
			isMaskNotDeployed
	                                                                                    );

	public MaskedSource(
			final DataSource<D, T> source,
			final int[][] blockSizes,
			final Supplier<String> nextCacheDirectory,
			final PickAndConvert<D, UnsignedLongType, UnsignedLongType, D> pacD,
			final PickAndConvert<T, VolatileUnsignedLongType, VolatileUnsignedLongType, T> pacT,
			final D extensionD,
			final T extensionT,
			final BiConsumer<CachedCellImg<UnsignedLongType, ?>, long[]> persistCanvas,
			final ExecutorService propagationExecutor)
	{
		this(
				source,
				blockSizes,
				nextCacheDirectory,
				nextCacheDirectory.get(),
				pacD,
				pacT,
				extensionD,
				extensionT,
				persistCanvas,
				propagationExecutor
		    );
	}

	@SuppressWarnings("unchecked")
	public MaskedSource(
			final DataSource<D, T> source,
			final int[][] blockSizes,
			final Supplier<String> nextCacheDirectory,
			final String initialCacheDirectory,
			final PickAndConvert<D, UnsignedLongType, UnsignedLongType, D> pacD,
			final PickAndConvert<T, VolatileUnsignedLongType, VolatileUnsignedLongType, T> pacT,
			final D extensionD,
			final T extensionT,
			final BiConsumer<CachedCellImg<UnsignedLongType, ?>, long[]> persistCanvas,
			final ExecutorService propagationExecutor)
	{
		super();
		this.source = source;
		this.dimensions = IntStream
				.range(0, source.getNumMipmapLevels())
				.mapToObj(level -> Intervals.dimensionsAsLongArray(this.source.getSource(0, level)))
				.toArray(long[][]::new);
		this.blockSizes = blockSizes;
		this.dataCanvases = new CachedCellImg[source.getNumMipmapLevels()];
		this.canvases = new RandomAccessibleInterval[source.getNumMipmapLevels()];
		this.dMasks = new RandomAccessible[this.canvases.length];
		this.tMasks = new RandomAccessible[this.canvases.length];
		this.nextCacheDirectory = nextCacheDirectory;

		this.masks = new HashMap<>();
		this.pacD = pacD;
		this.pacT = pacT;
		this.extensionT = extensionT;
		this.extensionD = extensionD;
		this.persistCanvas = persistCanvas;

		this.propagationExecutor = propagationExecutor;

		this.cacheDirectory.addListener(new CanvasBaseDirChangeListener(
				dataCanvases,
				canvases,
				this.dimensions,
				this.blockSizes
		));
		this.cacheDirectory.set(initialCacheDirectory);

		this.affectedBlocksByLabel = Stream.generate(HashMap::new).limit(this.canvases.length).toArray(Map[]::new);

		setMasksConstant();

	}

	public RandomAccessibleInterval<UnsignedLongType> generateMask(final int t, final int level, final
	UnsignedLongType value, final Predicate<UnsignedLongType> isPaintedForeground)
	throws MaskInUse
	{
		return generateMask(new MaskInfo<>(t, level, value), isPaintedForeground);
	}

	public RandomAccessibleInterval<UnsignedLongType> generateMask(final MaskInfo<UnsignedLongType> mask, final
	Predicate<UnsignedLongType> isPaintedForeground)
	throws MaskInUse
	{

		synchronized (this)
		{

			if (!canGenerateMask.get())
			{
				LOG.error(
						"Currently processing, cannot generate new mask: persisting? {} mask in use? {}",
						isPersisting.get(),
						isMaskDeployed.get()
				         );
				throw new MaskInUse("Busy, cannot generate new mask.");
			}
			this.isMaskDeployed.set(true);
		}

		final DiskCachedCellImgOptions maskOpts = DiskCachedCellImgOptions
				.options()
				.cacheDirectory(null)
				.deleteCacheDirectoryOnExit(true)
				.volatileAccesses(true)
				.dirtyAccesses(false)
				.cellDimensions(this.blockSizes[mask.level]);

		final CachedCellImg<UnsignedLongType, ?> store = new DiskCachedCellImgFactory<>(
				new UnsignedLongType(),
				maskOpts
		)
				.create(source.getSource(0, mask.level));
		final RandomAccessibleInterval<VolatileUnsignedLongType> vstore  = VolatileViews.wrapAsVolatile(store);
		final UnsignedLongType                                   INVALID = new UnsignedLongType(Label.INVALID);
		this.dMasks[mask.level] = Converters.convert(
				Views.extendZero(store),
				(input, output) -> output.set(isPaintedForeground.test(input) ? mask.value : INVALID),
				new UnsignedLongType()
		                                            );
		this.tMasks[mask.level] = Converters.convert(Views.extendZero(vstore), (input, output) -> {
			final boolean isValid = input.isValid();
			output.setValid(isValid);
			if (isValid)
			{
				output.get().set(input.get().get() > 0 ? mask.value : INVALID);
			}
		}, new VolatileUnsignedLongType());
		final RealRandomAccessible<UnsignedLongType>         dMaskInterpolated = Views.interpolate(
				this.dMasks[mask.level],
				new NearestNeighborInterpolatorFactory<>()
		                                                                                          );
		final RealRandomAccessible<VolatileUnsignedLongType> tMaskInterpolated = Views.interpolate(
				this.tMasks[mask.level],
				new NearestNeighborInterpolatorFactory<>()
		                                                                                          );
		for (int level = 0; level < getNumMipmapLevels(); ++level)
		{
			if (level != mask.level)
			{
				final double[] scale   = DataSource.getRelativeScales(this, 0, mask.level, level);
				final Scale3D  scale3D = new Scale3D(scale);
				this.dMasks[level] = RealViews.affine(dMaskInterpolated, scale3D.inverse());
				this.tMasks[level] = RealViews.affine(tMaskInterpolated, scale3D.inverse());
			}
		}
		final AccessedBlocksRandomAccessible<UnsignedLongType> trackingStore = new AccessedBlocksRandomAccessible<>(
				store,
				store.getCellGrid()
		);
		this.masks.put(trackingStore, mask);
		return trackingStore;
	}

	public void applyMask(final RandomAccessibleInterval<UnsignedLongType> mask, final Interval paintedInterval, final
	Predicate<UnsignedLongType> acceptAsPainted)
	{
		new Thread(() -> {
			synchronized (this)
			{
				LOG.debug("Applying mask: {}", mask, paintedInterval);
				final MaskInfo<UnsignedLongType> maskInfo = this.masks.get(mask);
				if (maskInfo == null)
				{
					LOG.warn("Did not pass valid mask {}, will not do anything", mask);
					return;
				}
				final CachedCellImg<UnsignedLongType, ?> canvas = dataCanvases[maskInfo.level];
				final CellGrid                           grid   = canvas.getCellGrid();

				final int[] blockSize = new int[grid.numDimensions()];
				grid.cellDimensions(blockSize);

				final TLongSet affectedBlocks = affectedBlocks(mask, canvas.getCellGrid(), paintedInterval);

				paintAffectedPixels(
						affectedBlocks,
						Converters.convert(
								Views.extendZero(mask),
								(s, t) -> t.set(acceptAsPainted.test(s)),
								new BitType()
						                  ),
						canvas,
						maskInfo.value,
						canvas.getCellGrid(),
						paintedInterval
				                   );

				forgetMasks();

				final TLongSet paintedBlocksAtHighestResolution = this.scaleBlocksToLevel(
						affectedBlocks,
						maskInfo.level,
						0
				                                                                         );

				this.affectedBlocksByLabel[maskInfo.level].computeIfAbsent(
						maskInfo.value.getIntegerLong(),
						key -> new TLongHashSet()
				                                                          ).addAll(affectedBlocks);
				LOG.warn("Added affected block: {}", affectedBlocksByLabel[maskInfo.level]);
				this.affectedBlocks.addAll(paintedBlocksAtHighestResolution);

				this.maskApplyCount.set(this.maskApplyCount.get() + 1);

				propagationExecutor.submit(() -> {
					propagateMask(
							mask,
							affectedBlocks,
							maskInfo.level,
							maskInfo.value,
							paintedInterval,
							acceptAsPainted
					             );
					setMasksConstant();
				});

			}
		}).start();

	}

	private void setMasksConstant()
	{
		for (int level = 0; level < getNumMipmapLevels(); ++level)
		{
			this.dMasks[level] = ConstantUtils.constantRandomAccessible(
					new UnsignedLongType(Label.INVALID),
					NUM_DIMENSIONS
			                                                           );
			this.tMasks[level] = ConstantUtils.constantRandomAccessible(
					new VolatileUnsignedLongType(Label.INVALID),
					NUM_DIMENSIONS
			                                                           );
		}
	}

	private Interval scaleIntervalToLevel(final Interval interval, final int intervalLevel, final int targetLevel)
	{
		if (intervalLevel == targetLevel) { return interval; }

		final double[] min = LongStream.of(Intervals.minAsLongArray(interval)).asDoubleStream().toArray();
		final double[] max = LongStream.of(Intervals.maxAsLongArray(interval)).asDoubleStream().map(d -> d + 1)
				.toArray();

		final Scale3D toTargetScale = new Scale3D(DataSource.getRelativeScales(
				this,
				0,
				intervalLevel,
				targetLevel
		                                                                      )).inverse();

		toTargetScale.apply(min, min);
		toTargetScale.apply(max, max);
		Arrays.setAll(max, d -> Math.ceil(max[d]) - 1);

		return Intervals.smallestContainingInterval(new FinalRealInterval(min, max));

	}

	private TLongSet scaleBlocksToLevel(final TLongSet blocks, final int blocksLevel, final int targetLevel)
	{
		if (blocksLevel == targetLevel) { return blocks; }

		final CellGrid grid          = this.dataCanvases[blocksLevel].getCellGrid();
		final CellGrid targetGrid    = this.dataCanvases[targetLevel].getCellGrid();
		final double[] toTargetScale = DataSource.getRelativeScales(this, 0, blocksLevel, targetLevel);

		return scaleBlocksToHigherLevel(blocks.toArray(), grid, targetGrid, toTargetScale);

	}

	public static TLongSet scaleBlocksToHigherLevel(
			final long[] blocks,
			final CellGrid grid,
			final CellGrid targetGrid,
			final double[] relativeFromBlocksToTarget)
	{

		assert DoubleStream.of(relativeFromBlocksToTarget).filter(d -> Math.round(d) != d).count() == 0;
		assert DoubleStream.of(relativeFromBlocksToTarget).filter(d -> d <= 0).count() == 0;
		assert DoubleStream.of(relativeFromBlocksToTarget).filter(d -> d > 1).count() > 0;

		final long[]   blockPos     = new long[grid.numDimensions()];
		final int[]    blockSize    = new int[grid.numDimensions()];
		final double[] blockMin     = new double[grid.numDimensions()];
		final double[] blockMax     = new double[grid.numDimensions()];
		final long[]   blockMinLong = new long[grid.numDimensions()];
		final long[]   blockMaxLong = new long[grid.numDimensions()];
		grid.cellDimensions(blockSize);

		final int[] targetBlockSize = new int[targetGrid.numDimensions()];
		targetGrid.cellDimensions(targetBlockSize);
		final int[]  ones                 = IntStream.generate(() -> 1).limit(targetGrid.numDimensions()).toArray();
		final long[] targetGridDimensions = targetGrid.getGridDimensions();

		final Scale3D toTargetScale = new Scale3D(relativeFromBlocksToTarget).inverse();

		final TLongSet targetBlocks = new TLongHashSet();
		for (final long blockId : blocks)
		{
			grid.getCellGridPositionFlat(blockId, blockPos);
			Arrays.setAll(blockMin, d -> blockPos[d] * blockSize[d]);
			Arrays.setAll(blockMax, d -> Math.min(blockMin[d] + blockSize[d], targetGrid.imgDimension(d)));
			toTargetScale.apply(blockMin, blockMin);
			toTargetScale.apply(blockMax, blockMax);
			Arrays.setAll(blockMinLong, d -> (long) blockMin[d] / targetBlockSize[d]);
			Arrays.setAll(blockMaxLong, d -> (long) (blockMax[d] - 1) / targetBlockSize[d]);
			Grids.forEachOffset(
					blockMinLong,
					blockMaxLong,
					ones,
					block -> targetBlocks.add(IntervalIndexer.positionToIndex(block, targetGridDimensions))
			                   );
		}

		return targetBlocks;
	}

	private void scalePositionToLevel(final long[] position, final int intervalLevel, final int targetLevel, final
	long[] targetPosition)
	{
		Arrays.setAll(targetPosition, d -> position[d]);
		if (intervalLevel == targetLevel) { return; }

		final double[] positionDouble = LongStream.of(position).asDoubleStream().toArray();

		final Scale3D toTargetScale = new Scale3D(DataSource.getRelativeScales(this, 0, intervalLevel, targetLevel));

		toTargetScale.apply(positionDouble, positionDouble);

		Arrays.setAll(targetPosition, d -> (long) Math.ceil(positionDouble[d]));

	}

	public void forgetMasks()
	{
		synchronized (this)
		{
			this.masks.clear();
			this.isMaskDeployed.set(false);
		}
	}

	public void persistCanvas() throws CannotPersist
	{
		synchronized (this)
		{
			if (this.canBePersited.get())
			{
				this.isPersisting.set(true);
				LOG.debug("Merging canvas into background for blocks {}", this.affectedBlocks);
				final CachedCellImg<UnsignedLongType, ?> canvas         = this.dataCanvases[0];
				final long[]                             affectedBlocks = this.affectedBlocks.toArray();
				this.affectedBlocks.clear();
				new Thread(() -> {
					try
					{
						this.persistCanvas.accept(canvas, affectedBlocks);
						clearCanvases();
						for (int level = 0; level < this.getNumMipmapLevels(); ++level)
						{
							LOG.debug("Invalidating all for data source for level={}", level);
							invalidateAllIfCachedImg(this.source.getDataSource(0, level));
							LOG.debug("Invalidating all for viewer source for level={}", level);
							invalidateAllIfCachedImg(this.source.getSource(0, level));
						}
					} finally
					{
						synchronized (this)
						{
							this.isPersisting.set(false);
						}
					}
				}).start();
			}
			else
			{
				LOG.error(
						"Cannot persist canvas: is persiting? {} has mask? {} applying {} masks",
						isPersisting.get(),
						this.isMaskDeployed.get(),
						this.maskApplyCount.get()
				         );
				throw new CannotPersist("Can not persist canvas!");
			}
		}
	}

	@Override
	public boolean isPresent(final int t)
	{
		return source.isPresent(t);
	}

	@Override
	public RandomAccessibleInterval<T> getSource(final int t, final int level)
	{
		final RandomAccessibleInterval<T>                                                   source   = this.source
				.getSource(
				t,
				level
		                                                                                                                    );
		final RandomAccessibleInterval<VolatileUnsignedLongType>                            canvas   = this
				.canvases[level];
		final RandomAccessibleInterval<VolatileUnsignedLongType>                            mask     = Views.interval(
				this.tMasks[level],
				source
		                                                                                                             );
		final RandomAccessibleTriple<T, VolatileUnsignedLongType, VolatileUnsignedLongType> composed = new
				RandomAccessibleTriple<>(
				source,
				canvas,
				mask
		);
		return new PickOne<>(Views.interval(composed, source), pacT.copy());
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method)
	{
		final RandomAccessibleInterval<T> source = getSource(t, level);
		return Views.interpolate(
				Views.extendValue(source, this.extensionT.copy()),
				new NearestNeighborInterpolatorFactory<>()
		                        );
	}

	@Override
	public void getSourceTransform(final int t, final int level, final AffineTransform3D transform)
	{
		source.getSourceTransform(t, level, transform);
	}

	@Override
	public T getType()
	{
		return source.getType();
	}

	@Override
	public String getName()
	{
		return source.getName();
	}

	// TODO VoxelDimensions is the only class pulled in by spim_data
	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return source.getNumMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval<D> getDataSource(final int t, final int level)
	{
		final RandomAccessibleInterval<D>                                   source   = this.source.getDataSource(
				t,
				level
		                                                                                                        );
		final RandomAccessibleInterval<UnsignedLongType>                    canvas   = this.dataCanvases[level];
		final RandomAccessibleInterval<UnsignedLongType>                    mask     = Views.interval(
				this.dMasks[level],
				source
		                                                                                             );
		final RandomAccessibleTriple<D, UnsignedLongType, UnsignedLongType> composed = new RandomAccessibleTriple<>(
				source,
				canvas,
				mask
		);
		return new PickOne<>(Views.interval(composed, source), pacD.copy());
	}

	@Override
	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method)
	{
		final RandomAccessibleInterval<D> source = getDataSource(t, level);
		return Views.interpolate(
				Views.extendValue(source, this.extensionD.copy()),
				new NearestNeighborInterpolatorFactory<>()
		                        );
	}

	@Override
	public D getDataType()
	{
		return source.getDataType();
	}

	public RandomAccessibleInterval<UnsignedLongType> getReadOnlyDataCanvas(final int t, final int level)
	{
		return Converters.convert(
				(RandomAccessibleInterval<UnsignedLongType>) this.dataCanvases[level],
				new TypeIdentity<>(),
				new UnsignedLongType()
		                         );
	}

	public RandomAccessibleInterval<D> getReadOnlyDataBackground(final int t, final int level)
	{
		return Converters.convert(
				this.source.getDataSource(t, level),
				new TypeIdentity<>(),
				this.source.getDataType().createVariable()
		                         );
	}

	public static void downsampleBlocks(
			final RandomAccessible<UnsignedLongType> source,
			final CachedCellImg<UnsignedLongType, LongAccess> img,
			final TLongSet affectedBlocks,
			final int[] steps,
			final Interval interval)
	{
		final LazyCells<Cell<LongAccess>> cells     = img.getCells();
		final CellGrid                    grid      = img.getCellGrid();
		final int[]                       blockSize = new int[grid.numDimensions()];
		grid.cellDimensions(blockSize);

		final RandomAccess<Cell<LongAccess>> cellsAccess        = cells.randomAccess();
		final long[]                         cellPosition       = new long[grid.numDimensions()];
		final long[]                         cellMin            = new long[grid.numDimensions()];
		final long[]                         cellMax            = new long[grid.numDimensions()];
		final long[]                         cellDim            = new long[grid.numDimensions()];
		final long[]                         intersectedCellMin = new long[grid.numDimensions()];
		final long[]                         intersectedCellMax = new long[grid.numDimensions()];

		LOG.debug("Initializing affected blocks: {}", affectedBlocks);
		for (final TLongIterator it = affectedBlocks.iterator(); it.hasNext(); )
		{
			final long blockId = it.next();
			grid.getCellGridPositionFlat(blockId, cellPosition);
			cellsAccess.setPosition(cellPosition);
			final Cell<LongAccess> cell = cellsAccess.get();
			cell.min(cellMin);

			Arrays.setAll(cellMax, d -> cellMin[d] + cell.dimension(d) - 1);
			Arrays.setAll(cellDim, cell::dimension);

			Arrays.setAll(intersectedCellMin, d -> cellMin[d]);
			Arrays.setAll(intersectedCellMax, d -> cellMax[d]);

			intersect(intersectedCellMin, intersectedCellMax, interval);

			if (isNonEmpty(cellMin, cellMax))
			{
				LOG.debug("Downsampling for intersected min/max: {} {}", intersectedCellMin, intersectedCellMax);
				downsample(source, Views.interval(img, intersectedCellMin, intersectedCellMax), steps);
			}
		}
	}

	/**
	 * @param source
	 * @param target
	 * @param steps
	 *
	 * @return
	 */
	public static <T extends IntegerType<T>> void downsample(
			final RandomAccessible<T> source,
			final RandomAccessibleInterval<T> target,
			final int[] steps)
	{
		LOG.debug(
				"Downsampling ({} {}) with steps {}",
				Intervals.minAsLongArray(target),
				Intervals.maxAsLongArray(target),
				steps
		         );
		final TLongLongHashMap counts       = new TLongLongHashMap();
		final long[]           start        = new long[source.numDimensions()];
		final long[]           stop         = new long[source.numDimensions()];
		final RandomAccess<T>  sourceAccess = source.randomAccess();
		for (final Cursor<T> targetCursor = Views.flatIterable(target).cursor(); targetCursor.hasNext(); )
		{
			final T t = targetCursor.next();
			counts.clear();

			Arrays.setAll(start, d -> targetCursor.getLongPosition(d) * steps[d]);
			Arrays.setAll(stop, d -> start[d] + steps[d]);
			sourceAccess.setPosition(start);

			for (int dim = 0; dim < start.length; )
			{
				final long id = sourceAccess.get().getIntegerLong();
				//				if ( id != Label.INVALID )
				counts.put(id, counts.get(id) + 1);

				for (dim = 0; dim < start.length; ++dim)
				{
					sourceAccess.fwd(dim);
					if (sourceAccess.getLongPosition(dim) < stop[dim])
					{
						break;
					}
					else
					{
						sourceAccess.setPosition(start[dim], dim);
					}
				}
			}

			long maxCount = 0;
			for (final TLongLongIterator countIt = counts.iterator(); countIt.hasNext(); )
			{
				countIt.advance();
				final long count = countIt.value();
				final long id    = countIt.key();
				if (count > maxCount)
				{
					maxCount = count;
					t.setInteger(id);
				}
			}

		}
	}

	public TLongSet getModifiedBlocks(final int level, final long id)
	{
		LOG.debug("Getting modified blocks for level={} and id={}", level, id);
		return Optional.ofNullable(this.affectedBlocksByLabel[level].get(id)).map(TLongHashSet::new).orElseGet(
				TLongHashSet::new);
	}

	private void propagateMask(
			final RandomAccessibleInterval<UnsignedLongType> mask,
			final TLongSet paintedBlocksAtPaintedScale,
			final int paintedLevel,
			final UnsignedLongType label,
			final Interval intervalAtPaintedScale,
			final Predicate<UnsignedLongType> isPaintedForeground)
	{

		for (int level = paintedLevel + 1; level < getNumMipmapLevels(); ++level)
		{
			final int                                         levelAsFinal          = level;
			final RandomAccessibleInterval<UnsignedLongType>  atLowerLevel          = dataCanvases[level - 1];
			final CachedCellImg<UnsignedLongType, LongAccess> atHigherLevel         = dataCanvases[level];
			final double[]                                    relativeScales        = DataSource.getRelativeScales(
					this,
					0,
					level - 1,
					level
			                                                                                                      );
			final Interval                                    intervalAtHigherLevel = scaleIntervalToLevel(
					intervalAtPaintedScale,
					paintedLevel,
					levelAsFinal
			                                                                                              );

			if (DoubleStream.of(relativeScales).filter(d -> Math.round(d) != d).count() > 0)
			{
				LOG.error(
						"Non-integer relative scales found for levels {} and {}: {} -- this does not make sense for " +
								"label data -- aborting.",
						level - 1,
						level,
						relativeScales
				         );
				throw new RuntimeException("Non-integer relative scales: " + Arrays.toString(relativeScales));
			}
			final TLongSet affectedBlocksAtHigherLevel = this.scaleBlocksToLevel(
					paintedBlocksAtPaintedScale,
					paintedLevel,
					level
			                                                                    );
			this.affectedBlocksByLabel[level].computeIfAbsent(label.getIntegerLong(), key -> new TLongHashSet())
					.addAll(
					affectedBlocksAtHigherLevel);

			// downsample
			final int[] steps = DoubleStream.of(relativeScales).mapToInt(d -> (int) d).toArray();
			downsampleBlocks(
					Views.extendValue(atLowerLevel, new UnsignedLongType(Label.INVALID)),
					atHigherLevel,
					affectedBlocksAtHigherLevel,
					steps,
					intervalAtHigherLevel
			                );
		}

		final RealRandomAccessible<UnsignedLongType> interpolatedMask = Views.interpolate(
				Views.extendZero(mask),
				new NearestNeighborInterpolatorFactory<>()
		                                                                                 );

		for (int level = paintedLevel - 1; level >= 0; --level)
		{
			LOG.debug("Upsampling for level={}", level);
			final TLongSet affectedBlocksAtLowerLevel              = this.scaleBlocksToLevel(
					paintedBlocksAtPaintedScale,
					paintedLevel,
					level
			                                                                                );
			final double[] currentRelativeScaleFromTargetToPainted = DataSource.getRelativeScales(
					this,
					0,
					level,
					paintedLevel
			                                                                                     );
			this.affectedBlocksByLabel[level].computeIfAbsent(label.getIntegerLong(), key -> new TLongHashSet())
					.addAll(
					affectedBlocksAtLowerLevel);

			final Interval paintedIntervalAtTargetLevel = scaleIntervalToLevel(
					intervalAtPaintedScale,
					paintedLevel,
					level
			                                                                  );

			// upsample
			final CachedCellImg<UnsignedLongType, LongAccess> canvasAtTargetLevel = dataCanvases[level];
			final CellGrid                                    gridAtTargetLevel   = canvasAtTargetLevel.getCellGrid();
			final int[]                                       blockSize           = new int[gridAtTargetLevel
					.numDimensions()];
			gridAtTargetLevel.cellDimensions(blockSize);

			final long[]                                 cellPosTarget  = new long[gridAtTargetLevel.numDimensions()];
			final long[]                                 minTarget      = new long[gridAtTargetLevel.numDimensions()];
			final long[]                                 maxTarget      = new long[gridAtTargetLevel.numDimensions()];
			final long[]                                 stopTarget     = new long[gridAtTargetLevel.numDimensions()];
			final long[]                                 minPainted     = new long[minTarget.length];
			final long[]                                 maxPainted     = new long[minTarget.length];
			final Scale3D                                scaleTransform = new Scale3D(
					currentRelativeScaleFromTargetToPainted);
			final RealRandomAccessible<UnsignedLongType> scaledMask     = RealViews.transformReal(
					interpolatedMask,
					scaleTransform
			                                                                                     );
			for (final TLongIterator blockIterator = affectedBlocksAtLowerLevel.iterator(); blockIterator.hasNext(); )
			{
				final long blockId = blockIterator.next();
				gridAtTargetLevel.getCellGridPositionFlat(blockId, cellPosTarget);
				Arrays.setAll(
						minTarget,
						d -> Math.min(cellPosTarget[d] * blockSize[d], gridAtTargetLevel.imgDimension(d) - 1)
				             );
				Arrays.setAll(
						maxTarget,
						d -> Math.min(minTarget[d] + blockSize[d], gridAtTargetLevel.imgDimension(d)) - 1
				             );
				Arrays.setAll(stopTarget, d -> maxTarget[d] + 1);
				this.scalePositionToLevel(minTarget, level, paintedLevel, minPainted);
				this.scalePositionToLevel(stopTarget, level, paintedLevel, maxPainted);
				Arrays.setAll(minPainted, d -> Math.min(Math.max(minPainted[d], mask.min(d)), mask.max(d)));
				Arrays.setAll(maxPainted, d -> Math.min(Math.max(maxPainted[d] - 1, mask.min(d)), mask.max(d)));

				final long[] intersectionMin = minTarget.clone();
				final long[] intersectionMax = maxTarget.clone();

				intersect(intersectionMin, intersectionMax, paintedIntervalAtTargetLevel);

				if (isNonEmpty(intersectionMin, intersectionMax))
				{

					LOG.debug("Intersected min={} max={}", intersectionMin, intersectionMax);

					LOG.debug(
							"Upsampling block: level={}, block min (target)={}, block max (target)={}, block min={}, " +
									"block max={}, scale={}, mask min={}, mask max={}",
							level,
							minTarget,
							maxTarget,
							minPainted,
							maxPainted,
							currentRelativeScaleFromTargetToPainted,
							Intervals.minAsLongArray(mask),
							Intervals.maxAsLongArray(mask)
					         );

					final IntervalView<BoolType> relevantBlockAtPaintedResolution = Views.interval(
							Converters.convert(mask, (s, t) -> t.set(isPaintedForeground.test(s)), new BoolType()),
							minPainted,
							maxPainted
					                                                                              );

					if (Intervals.numElements(relevantBlockAtPaintedResolution) == 0)
					{
						continue;
					}

					LOG.debug(
							"Upsampling for level {} and intersected intervals ({} {})",
							level,
							intersectionMin,
							intersectionMax
					         );
					final Interval                 interval     = new FinalInterval(intersectionMin, intersectionMax);
					final Cursor<UnsignedLongType> canvasCursor = Views.flatIterable(Views.interval(
							canvasAtTargetLevel,
							interval
					                                                                               )).cursor();
					final Cursor<UnsignedLongType> maskCursor   = Views.flatIterable(Views.interval(Views.raster(
							scaledMask), interval)).cursor();
					while (maskCursor.hasNext())
					{
						canvasCursor.fwd();
						final boolean wasPainted = isPaintedForeground.test(maskCursor.next());
						if (wasPainted)
						{
							canvasCursor.get().set(label);
						}
					}
				}
			}

		}

		synchronized (this)
		{
			this.maskApplyCount.set(Math.max(this.maskApplyCount.get() - 1, 0));
		}
	}

	public static TLongSet affectedBlocks(final long[] gridDimensions, final int[] blockSize, final Interval...
			intervals)
	{
		final TLongHashSet blocks              = new TLongHashSet();
		final int[]        ones                = IntStream.generate(() -> 1).limit(blockSize.length).toArray();
		final long[]       relevantIntervalMin = new long[blockSize.length];
		final long[]       relevantIntervalMax = new long[blockSize.length];
		for (final Interval interval : intervals)
		{
			Arrays.setAll(relevantIntervalMin, d -> (interval.min(d) / blockSize[d]));
			Arrays.setAll(relevantIntervalMax, d -> (interval.max(d) / blockSize[d]));
			Grids.forEachOffset(
					relevantIntervalMin,
					relevantIntervalMax,
					ones,
					offset -> blocks.add(IntervalIndexer.positionToIndex(offset, gridDimensions))
			                   );
		}

		return blocks;
	}

	public static TLongSet affectedBlocks(final RandomAccessibleInterval<?> input, final CellGrid grid, final Interval
			interval)
	{
		if (input instanceof AccessedBlocksRandomAccessible<?>)
		{
			final AccessedBlocksRandomAccessible<?> tracker = (net.imglib2.util.AccessedBlocksRandomAccessible<?>)
					input;
			if (grid.equals(tracker.getGrid()))
			{
				final long[] blocks = tracker.listBlocks();
				LOG.debug("Got these blocks from tracker: {}", blocks);
				return new TLongHashSet(blocks);
			}
		}
		return affectedBlocks(grid, interval);
	}

	public static TLongSet affectedBlocks(final CellGrid grid, final Interval interval)
	{
		final int[] blockSize = IntStream.range(0, grid.numDimensions()).map(grid::cellDimension).toArray();
		return affectedBlocks(grid.getGridDimensions(), blockSize, interval);
	}

	public static TLongSet affectedBlocks(final long[] gridDimensions, final int[] blockSize, final Interval interval)
	{
		final TLongHashSet blocks              = new TLongHashSet();
		final int[]        ones                = IntStream.generate(() -> 1).limit(blockSize.length).toArray();
		final long[]       relevantIntervalMin = new long[blockSize.length];
		final long[]       relevantIntervalMax = new long[blockSize.length];
		Arrays.setAll(relevantIntervalMin, d -> (interval.min(d) / blockSize[d]));
		Arrays.setAll(relevantIntervalMax, d -> (interval.max(d) / blockSize[d]));
		Grids.forEachOffset(
				relevantIntervalMin,
				relevantIntervalMax,
				ones,
				offset -> blocks.add(IntervalIndexer.positionToIndex(offset, gridDimensions))
		                   );

		return blocks;
	}

	public static TLongSet affectedBlocksInHigherResolution(
			final TLongSet blocksInLowRes,
			final CellGrid lowResGrid,
			final CellGrid highResGrid,
			final int[] relativeScalingFactors)
	{

		assert lowResGrid.numDimensions() == highResGrid.numDimensions();

		final int[] blockSizeLowRes = new int[lowResGrid.numDimensions()];
		Arrays.setAll(blockSizeLowRes, lowResGrid::cellDimension);

		final long[] gridDimHighRes   = highResGrid.getGridDimensions();
		final int[]  blockSizeHighRes = new int[highResGrid.numDimensions()];
		Arrays.setAll(blockSizeHighRes, highResGrid::cellDimension);

		final long[] blockMin = new long[lowResGrid.numDimensions()];
		final long[] blockMax = new long[lowResGrid.numDimensions()];

		final TLongHashSet blocksInHighRes = new TLongHashSet();

		final int[] ones = IntStream.generate(() -> 1).limit(highResGrid.numDimensions()).toArray();

		for (final TLongIterator it = blocksInLowRes.iterator(); it.hasNext(); )
		{
			final long index = it.next();
			lowResGrid.getCellGridPositionFlat(index, blockMin);
			for (int d = 0; d < blockMin.length; ++d)
			{
				final long m = blockMin[d] * blockSizeLowRes[d];
				blockMin[d] = m * relativeScalingFactors[d] / blockSizeHighRes[d];
				blockMax[d] = (m + blockSizeLowRes[d] - 1) * relativeScalingFactors[d] / blockSizeHighRes[d];
			}

			Grids.forEachOffset(
					blockMin,
					blockMax,
					ones,
					offset -> blocksInHighRes.add(IntervalIndexer.positionToIndex(offset, gridDimHighRes))
			                   );

		}
		return blocksInHighRes;
	}

	public static <M extends BooleanType<M>, C extends IntegerType<C>> void paintAffectedPixels(
			final TLongSet relevantBlocks,
			final RandomAccessible<M> mask,
			final RandomAccessibleInterval<C> canvas,
			final C paintLabel,
			final CellGrid grid,
			final Interval paintedInterval)
	{

		final long[] currentMin     = new long[grid.numDimensions()];
		final long[] currentMax     = new long[grid.numDimensions()];
		final long[] gridPosition   = new long[grid.numDimensions()];
		final long[] gridDimensions = grid.getGridDimensions();
		final int[]  blockSize      = new int[grid.numDimensions()];
		grid.cellDimensions(blockSize);

		final long[] intervalMin = Intervals.minAsLongArray(paintedInterval);
		final long[] intervalMax = Intervals.maxAsLongArray(paintedInterval);

		for (final TLongIterator blockIt = relevantBlocks.iterator(); blockIt.hasNext(); )
		{

			final long blockId = blockIt.next();
			IntervalIndexer.indexToPosition(blockId, gridDimensions, gridPosition);

			for (int d = 0; d < currentMin.length; ++d)
			{
				final long m = gridPosition[d] * blockSize[d];
				final long M = Math.min(m + blockSize[d] - 1, canvas.max(d));
				currentMin[d] = Math.max(m, intervalMin[d]);
				currentMax[d] = Math.min(M, intervalMax[d]);
			}

			LOG.trace("Painting affected pixels for: {} {} {}", blockId, currentMin, currentMax);

			final Interval  restrictedInterval = new FinalInterval(currentMin, currentMax);
			final Cursor<M> sourceCursor       = Views.flatIterable(Views.interval(mask, restrictedInterval)).cursor();
			final Cursor<C> targetCursor       = Views.flatIterable(Views.interval(
					canvas,
					restrictedInterval
			                                                                      )).cursor();
			while (sourceCursor.hasNext())
			{
				targetCursor.fwd();
				if (sourceCursor.next().get())
				{
					targetCursor.get().set(paintLabel);
				}
			}

		}

	}

	/**
	 * Intersect min,max with interval. Intersected min/max will be written into input min/max.
	 *
	 * @param min
	 * @param max
	 * @param interval
	 */
	public static void intersect(final long[] min, final long[] max, final Interval interval)
	{
		for (int d = 0; d < min.length; ++d)
		{
			min[d] = Math.max(min[d], interval.min(d));
			max[d] = Math.min(max[d], interval.max(d));
		}
	}

	public static boolean isNonEmpty(final long[] min, final long[] max)
	{
		for (int d = 0; d < min.length; ++d)
		{
			if (max[d] < min[d]) { return false; }
		}
		return true;
	}

	private void clearCanvases()
	{
		this.cacheDirectory.set(this.nextCacheDirectory.get());
		this.affectedBlocks.clear();
		Arrays.stream(this.affectedBlocksByLabel).forEach(Map::clear);
		this.canvasClearedListeners.forEach(Runnable::run);
	}

	private static class CanvasBaseDirChangeListener implements ChangeListener<String>
	{

		private final CachedCellImg<UnsignedLongType, ?>[] dataCanvases;

		private final RandomAccessibleInterval<VolatileUnsignedLongType>[] canvases;

		private final long[][] dimensions;

		private final int[][] blockSizes;

		public CanvasBaseDirChangeListener(
				final CachedCellImg<UnsignedLongType, ?>[] dataCanvases,
				final RandomAccessibleInterval<VolatileUnsignedLongType>[] canvases,
				final long[][] dimensions,
				final int[][] blockSizes)
		{
			super();
			this.dataCanvases = dataCanvases;
			this.canvases = canvases;
			this.dimensions = dimensions;
			this.blockSizes = blockSizes;
		}

		@Override
		public void changed(final ObservableValue<? extends String> observable, final String oldValue, final String
				newValue)
		{

			Optional.ofNullable(oldValue).map(Paths::get).ifPresent(DiskCellCache::addDeleteHook);
			// TODO add clean-up job that already starts deleting before jvm
			// shutdown

			LOG.info("Updating cache directory: observable={} oldValue={} newValue={}", observable, oldValue,
					newValue);

			final DiskCachedCellImgOptions opts = DiskCachedCellImgOptions
					.options()
					.volatileAccesses(true)
					.dirtyAccesses(true);

			for (int level = 0; level < canvases.length; ++level)
			{
				if (newValue != null)
				{
					final Path cacheDir = Paths.get(newValue, String.format("%d", level));
					final DiskCachedCellImgOptions o = opts
							.volatileAccesses(true)
							.dirtyAccesses(true)
							.cacheDirectory(cacheDir)
							.deleteCacheDirectoryOnExit(true)
							.cellDimensions(blockSizes[level]);
					final DiskCachedCellImgFactory<UnsignedLongType>         f      = new DiskCachedCellImgFactory<>(
							new UnsignedLongType(),
							o
					);
					final CellLoader<UnsignedLongType>                       loader = img -> img.forEach(t -> t.set(
							Label.INVALID));
					final CachedCellImg<UnsignedLongType, ?>                 store  = f.create(
							dimensions[level],
							loader,
							o
					                                                                          );
					final RandomAccessibleInterval<VolatileUnsignedLongType> vstore = VolatileViews.wrapAsVolatile(store);

					this.dataCanvases[level] = store;
					this.canvases[level] = vstore;
				}
			}
		}

	}

	public static void invalidateAllIfCachedImg(final RandomAccessibleInterval<?> img)
	{
		if (img instanceof VolatileCachedCellImg<?, ?>)
		{
			LOG.debug(
					"{} is instance of {} ({}) -- invalidating all",
					img,
					VolatileCachedCellImg.class.getName(),
					img.getClass().getName()
			         );
			((VolatileCachedCellImg<?, ?>) img).getInvalidateAll().run();
		}
		else if (img instanceof CachedCellImg<?, ?>)
		{
			LOG.debug(
					"{} is instance of {} ({}) -- invalidating all",
					img,
					CachedCellImg.class.getName(),
					img.getClass().getName()
			         );
			final Cache<Long, ?> cache = ((CachedCellImg<?, ?>) img).getCache();
			cache.invalidateAll();
		}
	}

	public DataSource<D, T> underlyingSource()
	{
		return this.source;
	}

	public String currentCanvasDirectory()
	{
		return this.cacheDirectory.get();
	}

	public BiConsumer<CachedCellImg<UnsignedLongType, ?>, long[]> getPersister()
	{
		return this.persistCanvas;
	}

	public CellGrid getCellGrid(final int t, final int level)
	{
		return ((AbstractCellImg<?, ?, ?, ?>) underlyingSource().getSource(t, level)).getCellGrid();
	}

	public long[] getAffectedBlocks()
	{
		return this.affectedBlocks.toArray();
	}

	public void addOnCanvasClearedListener(final Runnable listener)
	{
		this.canvasClearedListeners.add(listener);
	}

	Map<Long, long[]>[] getAffectedBlocksById()
	{
		@SuppressWarnings("unchecked") final Map<Long, long[]>[] maps = new HashMap[this.affectedBlocksByLabel.length];

		for (int level = 0; level < maps.length; ++level)
		{
			maps[level] = new HashMap<>();
			final Map<Long, long[]> map = maps[level];
			for (final Entry<Long, TLongHashSet> entry : this.affectedBlocksByLabel[level].entrySet())
			{
				map.put(entry.getKey(), entry.getValue().toArray());
			}
		}

		LOG.debug("Retruning affected blocks by id for {} levels: {}", maps.length, maps);
		return maps;
	}

	void affectBlocks(
			final long[] blocks,
			final Map<Long, long[]>[] blocksById)
	{
		assert this.affectedBlocksByLabel.length == blocksById.length;

		LOG.debug("Affected blocks: {} to add: {}", this.affectedBlocks, blocks);
		this.affectedBlocks.addAll(blocks);
		LOG.debug("Affected blocks: {}", this.affectedBlocks);

		LOG.debug("Affected blocks by id: {} to add: {}", this.affectedBlocksByLabel, blocksById);
		for (int level = 0; level < blocksById.length; ++level)
		{
			final Map<Long, TLongHashSet> map = this.affectedBlocksByLabel[level];
			for (final Entry<Long, long[]> entry : blocksById[level].entrySet())
			{
				map.computeIfAbsent(entry.getKey(), key -> new TLongHashSet()).addAll(entry.getValue());
			}
		}
		LOG.debug("Affected blocks by id: {}", this.affectedBlocksByLabel, null);

	}

}
