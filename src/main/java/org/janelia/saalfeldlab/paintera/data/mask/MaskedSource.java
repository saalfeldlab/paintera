package org.janelia.saalfeldlab.paintera.data.mask;

import bdv.cache.SharedQueue;
import bdv.viewer.Interpolation;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.Pair;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.AbstractInterval;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.RealRandomAccessibleRealInterval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.Invalidate;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.iterator.LocalizingIntervalIterator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.parallel.TaskExecutors;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.Type;
import net.imglib2.type.label.Label;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRealRandomAccessibleRealInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.UtilityTask;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.net.imglib2.FinalRealRandomAccessibleRealInterval;
import org.janelia.saalfeldlab.net.imglib2.outofbounds.RealOutOfBoundsConstantValueFactory;
import org.janelia.saalfeldlab.net.imglib2.util.AccessedBlocksRandomAccessible;
import org.janelia.saalfeldlab.net.imglib2.view.BundleView;
import org.janelia.saalfeldlab.net.imglib2.view.RealRandomAccessibleTriple;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;
import org.janelia.saalfeldlab.paintera.data.mask.exception.CannotClearCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.exception.CannotPersist;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToPersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToUpdateLabelBlockLookup;
import org.janelia.saalfeldlab.paintera.data.n5.BlockSpec;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers;
import org.janelia.saalfeldlab.paintera.util.IntervalIterable;
import org.janelia.saalfeldlab.paintera.util.ReusableIntervalIterator;
import org.janelia.saalfeldlab.util.TmpVolatileHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Wrap a paintable canvas around a source that provides background.
 * <p>
 * In order to allow for sources that are as general as possible, {@link MaskedSource} adds two layers on top of
 * the original data provided by the underlying source. In total, that makes three layers of data (from deepest to most shallow/short lived):
 * <ul>
 * <li>Background provided by underlying source</li>
 * <li>Canvas that stores current paintings projected to all mipmap levels. Can be merged into background when triggered by user</li>
 * <li>Mask is only active when the user currently paints and only valid for one specific mipmap level at a time (other levels are interpolated).
 * Once a painting action is finished, the mask should be submitted back to the {@link MaskedSource}, to merge it into the canvas and propagate
 * to all mipmap levels.</li>
 * </ul>
 * <p>
 * Only one (or no) mask can be active at any time. {@link MaskedSource} will throw an appropriate exception if a mask is requested while
 * any of these are true:
 * <ul>
 * <li>A mask has been requested previously and is still in use (i.e. not submitted back)</li>
 * <li>A mask was submitted back and propagation of the mask to all mipmap levels of the canvas is still underway</li>
 * <li>The canvas is currently being committed into the background.</li>
 * </ul>
 * <p>
 * The mask that is currently active has to be unique. If a mask that is not identical to the currently active mask is submitted back, or
 * {@link MaskedSource} is busy with committing to the background an appropriate exception will be thrown.
 * <p>
 * The canvas can only be committed to the background if currently no mask is active and no mask is being submitted/propagated back into
 * the canvas. In either of those cases, an appropriate exception is thrown.
 *
 * @param <D> data type
 * @param <T> viewer type
 */
public class MaskedSource<D extends RealType<D>, T extends Type<T>> implements DataSource<D, T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int NUM_DIMENSIONS = 3;

	public static final Predicate<Long> VALID_LABEL_CHECK = it -> it != Label.INVALID;

	private final UnsignedLongType INVALID = new UnsignedLongType(Label.INVALID);

	private final DataSource<D, T> source;

	private final SharedQueue queue;

	private final DiskCachedCellImg<UnsignedLongType, LongAccess>[] dataCanvases;

	private final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType>[] canvases;

	private final RealRandomAccessible<UnsignedLongType>[] dMasks;

	private final RealRandomAccessible<VolatileUnsignedLongType>[] tMasks;

	private final PickAndConvert<D, UnsignedLongType, UnsignedLongType, D> pacD;

	private final PickAndConvert<T, VolatileUnsignedLongType, VolatileUnsignedLongType, T> pacT;

	private final D extensionD;

	private final T extensionT;

	private final ExecutorService propagationExecutor;

	// TODO make sure that BB is handled properly in multi scale case!!!
	private final TLongSet affectedBlocks = new TLongHashSet();

	private final PersistCanvas persistCanvas;

	private final StringProperty cacheDirectory = new SimpleStringProperty();

	private final Supplier<String> nextCacheDirectory;

	private final long[][] dimensions;

	private final int[][] blockSizes;

	private final ObjectProperty<SourceMask> currentMaskProperty = new SimpleObjectProperty<>(null);

	private final BooleanProperty isPersistingProperty = new SimpleBooleanProperty(false);

	private final BooleanProperty isCreatingMaskProperty = new SimpleBooleanProperty(false);

	private final BooleanProperty isApplyingMask = new SimpleBooleanProperty();

	private final BooleanBinding isMaskInUseBinding = Bindings.createBooleanBinding(() ->
					isCreatingMask() || getCurrentMask() != null || isApplyingMask.get() || isPersisting(), isCreatingMaskProperty, currentMaskProperty,
			isApplyingMaskProperty(),
			isPersistingProperty);

	private final BooleanProperty isBusy = new SimpleBooleanProperty(null, "Masked Source Is Busy");

	private final ConcurrentHashMap<Long, TLongHashSet>[] affectedBlocksByLabel;

	private final List<Runnable> canvasClearedListeners = new ArrayList<>();

	private final BooleanProperty showCanvasOverBackground = new SimpleBooleanProperty(this, "show canvas", true);
	private final AtomicInteger busyAlertCount = new AtomicInteger();

	public MaskedSource(
			final DataSource<D, T> source,
			final SharedQueue queue,
			final int[][] blockSizes,
			final Supplier<String> nextCacheDirectory,
			final PickAndConvert<D, UnsignedLongType, UnsignedLongType, D> pacD,
			final PickAndConvert<T, VolatileUnsignedLongType, VolatileUnsignedLongType, T> pacT,
			final D extensionD,
			final T extensionT,
			final PersistCanvas persistCanvas,
			final ExecutorService propagationExecutor) {

		this(
				source,
				queue,
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
			final SharedQueue queue,
			final int[][] blockSizes,
			final Supplier<String> nextCacheDirectory,
			final String initialCacheDirectory,
			final PickAndConvert<D, UnsignedLongType, UnsignedLongType, D> pacD,
			final PickAndConvert<T, VolatileUnsignedLongType, VolatileUnsignedLongType, T> pacT,
			final D extensionD,
			final T extensionT,
			final PersistCanvas persistCanvas,
			final ExecutorService propagationExecutor) {

		super();
		this.source = source;
		this.queue = queue;
		this.dimensions = IntStream
				.range(0, source.getNumMipmapLevels())
				.mapToObj(level -> Intervals.dimensionsAsLongArray(this.source.getSource(0, level)))
				.toArray(long[][]::new);
		this.blockSizes = blockSizes;
		this.dataCanvases = new DiskCachedCellImg[source.getNumMipmapLevels()];
		this.canvases = new TmpVolatileHelpers.RaiWithInvalidate[source.getNumMipmapLevels()];
		this.dMasks = new RealRandomAccessible[this.canvases.length];
		this.tMasks = new RealRandomAccessible[this.canvases.length];
		this.nextCacheDirectory = nextCacheDirectory;

		this.pacD = pacD;
		this.pacT = pacT;
		this.extensionT = extensionT;
		this.extensionD = extensionD;
		this.persistCanvas = persistCanvas;

		this.propagationExecutor = propagationExecutor;

		this.cacheDirectory.addListener(new CanvasBaseDirChangeListener(
				this.queue,
				dataCanvases,
				canvases,
				this.dimensions,
				this.blockSizes));
		this.cacheDirectory.set(initialCacheDirectory);

		this.affectedBlocksByLabel = new ConcurrentHashMap[canvases.length];
		for (int level = 0; level < canvases.length; level++) {
			affectedBlocksByLabel[level] = new ConcurrentHashMap<>();
		}

		isBusyProperty().addListener((obs, oldv, busy) -> {
			if (!busy) {
				busyAlertCount.set(0);
			}
		});

		setMasksConstant();
	}

	public ReadOnlyBooleanProperty isApplyingMaskProperty() {

		return isApplyingMask;
	}

	public ReadOnlyBooleanProperty isBusyProperty() {

		return isBusy;
	}

	public BooleanBinding isMaskInUseBinding() {

		return isMaskInUseBinding;
	}

	public BooleanProperty showCanvasOverBackgroundProperty() {

		return showCanvasOverBackground;
	}

	public SourceMask getCurrentMask() {

		return currentMaskProperty.get();
	}

	public synchronized SourceMask generateMask(
			final MaskInfo maskInfo,
			final Predicate<Long> isPaintedForeground)
			throws MaskInUse {

		LOG.debug("Generating mask: {}", maskInfo);

		final var storeWithVolatile = createMaskStoreWithVolatile(maskInfo.level);
		final DiskCachedCellImg<UnsignedLongType, ?> store = storeWithVolatile.getKey();
		final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType> vstore = storeWithVolatile.getValue();
		final AccessedBlocksRandomAccessible<UnsignedLongType> trackingStore = new AccessedBlocksRandomAccessible<>(store, store.getCellGrid());
		final var mask = new SourceMask(maskInfo, trackingStore, vstore.getRai(), store.getCache(), vstore.getInvalidate(), store::shutdown);
		setMask(mask, isPaintedForeground);
		return mask;
	}

	private void setCreateMaskFlag(boolean isCreateMask) {

		this.isCreatingMaskProperty.set(isCreateMask);
	}

	private void setCurrentMask(SourceMask mask) {

		this.currentMaskProperty.set(mask);
	}

	private boolean isPersisting() {

		return isPersistingProperty.get();
	}

	private boolean isCreatingMask() {

		return isCreatingMaskProperty.get();
	}

	private boolean isMaskInUse() {

		return isMaskInUseBinding.get();
	}

	public synchronized void setMask(
			final SourceMask mask,
			final Predicate<Long> isPaintedForeground)
			throws MaskInUse {

		setMask(mask, mask.getRai(), mask.getVolatileRai(), isPaintedForeground);
	}

	public synchronized void setMask(
			final SourceMask mask,
			RandomAccessibleInterval<UnsignedLongType> rai,
			RandomAccessibleInterval<VolatileUnsignedLongType> volatileRai,
			final Predicate<Long> acceptLabel)
			throws MaskInUse {

		if (isMaskInUse()) {
			LOG.error(
					"Currently processing, cannot set new mask: is persisting? {} has mask? {} is creating mask? {} is applying mask? {}",
					this.isPersisting(),
					this.getCurrentMask() != null,
					this.isCreatingMask(),
					this.isApplyingMask);
			final var offerReset = busyAlertCount.getAndIncrement() >= 3;
			throw new MaskInUse("Busy, cannot set new mask.", offerReset);
		}
		setCreateMaskFlag(true);
		this.isBusy.set(true);

		setMasks(rai, volatileRai, mask.getInfo().level, acceptLabel);

		setCurrentMask(mask);
		setCreateMaskFlag(false);
		this.isBusy.set(false);
	}

	public synchronized void setMask(
			final SourceMask mask,
			RealRandomAccessible<UnsignedLongType> rai,
			RealRandomAccessible<VolatileUnsignedLongType> volatileRai,
			final Predicate<Long> acceptLabel)
			throws MaskInUse {

		if (isMaskInUse()) {
			LOG.error(
					"Currently processing, cannot set new mask: is persisting? {} has mask? {} is creating mask? {} is applying mask? {}",
					this.isPersisting(),
					this.getCurrentMask() != null,
					this.isCreatingMask(),
					this.isApplyingMask);
			final var offerReset = busyAlertCount.getAndIncrement() >= 3;
			throw new MaskInUse("Busy, cannot set new mask.", offerReset);
		}
		setCreateMaskFlag(true);
		this.isBusy.set(true);

		setMasks(rai, volatileRai, mask.getInfo().level, acceptLabel);

		setCurrentMask(mask);
		setCreateMaskFlag(false);
		this.isBusy.set(false);
	}

	public void setMask(
			final MaskInfo maskInfo,
			final RealRandomAccessible<UnsignedLongType> maskRai,
			final RealRandomAccessible<VolatileUnsignedLongType> maskVolatileRai,
			final Invalidate<?> invalidate,
			final Invalidate<?> volatileInvalidate,
			final Runnable shutdown,
			final Predicate<Long> acceptLabel)
			throws MaskInUse {

		if (isMaskInUse()) {
			LOG.error(
					"Currently processing, cannot set new mask: is persisting? {} has mask? {} is creating mask? {} is applying mask? {}",
					this.isPersisting(),
					this.getCurrentMask() != null,
					this.isCreatingMask(),
					this.isApplyingMask);
			final var offerReset = busyAlertCount.getAndIncrement() >= 3;
			throw new MaskInUse("Busy, cannot set new mask.", offerReset);
		}
		setCreateMaskFlag(true);
		this.isBusy.set(true);

		setMasks(maskRai, maskVolatileRai, maskInfo.level, acceptLabel);

		final RandomAccessibleInterval<UnsignedLongType> rasteredMask = Views.interval(Views.raster(maskRai), source.getSource(0, maskInfo.level));
		final RandomAccessibleInterval<VolatileUnsignedLongType> rasteredVolatileMask = Views.interval(Views.raster(maskVolatileRai),
				source.getSource(0, maskInfo.level));
		final SourceMask mask = new SourceMask(maskInfo, rasteredMask, rasteredVolatileMask, invalidate, volatileInvalidate, shutdown);
		setCurrentMask(mask);
		setCreateMaskFlag(false);
		this.isBusy.set(false);
	}

	public void applyMask(
			final SourceMask mask,
			final Interval paintedInterval,
			final Predicate<Long> acceptAsPainted) {

		if (mask == null || Intervals.isEmpty(Intervals.intersect(mask.getRai(), paintedInterval)))
			return;
		final var applyMaskThread = new Thread(() -> {
			Thread.currentThread().setName("apply mask");
			synchronized (this) {
				final boolean maskCanBeApplied = !this.isCreatingMask() && this.getCurrentMask() == mask && !this.isApplyingMask.get() && !this.isPersisting();
				if (!maskCanBeApplied) {
					LOG.debug("Did not pass valid mask {}, will not do anything", mask);
					this.isBusy.set(false);
					return;
				}
				this.isApplyingMask.set(true);
			}

			LOG.debug("Applying mask: {}", mask);
			final MaskInfo maskInfo = mask.getInfo();
			final CachedCellImg<UnsignedLongType, ?> canvas = dataCanvases[maskInfo.level];
			final CellGrid grid = canvas.getCellGrid();

			final int[] blockSize = new int[grid.numDimensions()];
			grid.cellDimensions(blockSize);

			final FinalInterval paintedIntervalOverCanvas = Intervals.intersect(canvas, paintedInterval);

			final TLongSet affectedBlocks = affectedBlocks(mask.getRai(), canvas.getCellGrid(), paintedIntervalOverCanvas);

			final var labelToBlocks = paintAffectedPixels(
					affectedBlocks,
					mask,
					canvas,
					canvas.getCellGrid(),
					paintedIntervalOverCanvas,
					acceptAsPainted);

			for (var label : labelToBlocks.entrySet()) {
				this.affectedBlocksByLabel[maskInfo.level].computeIfAbsent(label.getKey(), k -> new TLongHashSet()).addAll(label.getValue());
			}

			final SourceMask currentMaskBeforePropagation = this.getCurrentMask();
			synchronized (this) {
				setCurrentMask(null);
			}

			final TLongSet paintedBlocksAtHighestResolution = this.scaleBlocksToLevel(
					affectedBlocks,
					maskInfo.level,
					0);

			LOG.debug("Added affected block: {}", affectedBlocksByLabel[maskInfo.level]);
			this.affectedBlocks.addAll(paintedBlocksAtHighestResolution);

			propagationExecutor.submit(() -> {
				try {
					propagateMask(
							mask.getRai(),
							affectedBlocks,
							maskInfo.level,
							paintedIntervalOverCanvas,
							acceptAsPainted,
							propagationExecutor);
				} finally {
					setMasksConstant();
					synchronized (this) {
						LOG.debug("Done applying mask!");
						this.isApplyingMask.set(false);
					}
					// free resources
					if (currentMaskBeforePropagation != null) {
						if (currentMaskBeforePropagation.getShutdown() != null)
							currentMaskBeforePropagation.getShutdown().run();
						if (currentMaskBeforePropagation.getInvalidate() != null)
							currentMaskBeforePropagation.getInvalidate().invalidateAll();
						if (currentMaskBeforePropagation.getInvalidateVolatile() != null)
							currentMaskBeforePropagation.getInvalidateVolatile().invalidateAll();
					}

					this.isBusy.set(false);
				}
			});

		});
		/* Start as busy, so a new mask isn't generated until we are done applying this one. */
		this.isBusy.set(true);
		applyMaskThread.start();

	}

	/**
	 * This method differs from `applyMask` in a few important ways:
	 * <p>
	 * - It runs over each block in parallel
	 * <p>
	 * - It is a blocking method
	 *
	 * @param mask            to apply ( should be same as `currentMask`)
	 * @param intervals       to apply mask over, separately.
	 * @param acceptAsPainted to accept a value
	 */
	public void applyMaskOverIntervals(
			final SourceMask mask,
			final List<Interval> intervals,
			final DoubleProperty progressBinding,
			final Predicate<Long> acceptAsPainted) {

		if (mask == null)
			return;

		final ExecutorService applyPool = Executors.newFixedThreadPool(
				Runtime.getRuntime().availableProcessors(),
				new ThreadFactoryBuilder().setNameFormat("apply-thread-%d").build()
		);
		final ArrayList<Future<?>> applies = new ArrayList<>();
		synchronized (this) {
			final boolean maskCanBeApplied = !this.isCreatingMask() && this.getCurrentMask() == mask && !this.isApplyingMask.get() && !this.isPersisting();
			if (!maskCanBeApplied) {
				LOG.debug("Did not pass valid mask {}, will not do anything", mask);
				this.isBusy.set(false);
				return;
			}
			this.isApplyingMask.set(true);
		}
		var expectedTasks = intervals.size() * 2;
		final var completedTasks = new AtomicInteger();

		final MaskInfo maskInfo = mask.getInfo();
		final CachedCellImg<UnsignedLongType, ?> canvas = dataCanvases[maskInfo.level];
		final CellGrid grid = canvas.getCellGrid();
		final RandomAccessibleInterval<UnsignedLongType> maskRai = mask.getRai();

		/* Start as busy, so a new mask isn't generated until we are done applying this one. */
		this.isBusy.set(true);

		for (Interval interval : intervals) {
			final FinalInterval intervalOverCanvas = Intervals.intersect(canvas, interval);
			if (Intervals.isEmpty(intervalOverCanvas))
				continue;


			var applyFuture = applyPool.submit(() -> {
				final int[] blockSize = new int[grid.numDimensions()];
				grid.cellDimensions(blockSize);

				final TLongSet directlyAffectedBlocks = affectedBlocks(maskRai, grid, intervalOverCanvas);

				final var labelToBlocks = paintAffectedPixels(
						directlyAffectedBlocks,
						mask,
						canvas,
						grid,
						intervalOverCanvas,
						acceptAsPainted);

				InvokeOnJavaFXApplicationThread.invoke(() -> {
					final double progress = completedTasks.incrementAndGet() / (double)expectedTasks;
					progressBinding.set(progress);
				});

				final Map<Long, TLongHashSet> blocksByLabelByLevel = this.affectedBlocksByLabel[maskInfo.level];
				synchronized (blocksByLabelByLevel) {
					for (var label : labelToBlocks.entrySet()) {
						blocksByLabelByLevel.computeIfAbsent(label.getKey(), k -> new TLongHashSet()).addAll(label.getValue());
					}
				}

				final TLongSet paintedBlocksAtHighestResolution = this.scaleBlocksToLevel(
						directlyAffectedBlocks,
						maskInfo.level,
						0);

				LOG.debug("Added affected block: {}", blocksByLabelByLevel);
				synchronized (affectedBlocks) {
					affectedBlocks.addAll(paintedBlocksAtHighestResolution);
				}

				try {
					propagationExecutor.submit(
							() -> propagateMask(
									maskRai,
									directlyAffectedBlocks,
									maskInfo.level,
									intervalOverCanvas,
									acceptAsPainted,
									propagationExecutor)
					).get();
					InvokeOnJavaFXApplicationThread.invoke(() -> {
						final double progress = completedTasks.incrementAndGet() / (double)expectedTasks;
						progressBinding.set(progress);
					});
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e);
				}

			});
			applies.add(applyFuture);
		}
		for (Future<?> it : applies) {
			try {
				it.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		InvokeOnJavaFXApplicationThread.invoke(() -> progressBinding.set(1.0));

		synchronized (this) {
			setCurrentMask(null);
			setMasksConstant();
			LOG.debug("Done applying mask!");
			this.isApplyingMask.set(false);
		}

		if (mask.getShutdown() != null)
			mask.getShutdown().run();
		if (mask.getInvalidate() != null)
			mask.getInvalidate().invalidateAll();
		if (mask.getInvalidateVolatile() != null)
			mask.getInvalidateVolatile().invalidateAll();

		applyPool.shutdown();
		this.isBusy.set(false);
	}

	private void setMasksConstant() {

		for (int level = 0; level < getNumMipmapLevels(); ++level) {
			this.dMasks[level] = ConstantUtils.constantRealRandomAccessible(
					new UnsignedLongType(Label.INVALID),
					NUM_DIMENSIONS);
			this.tMasks[level] = ConstantUtils.constantRealRandomAccessible(
					new VolatileUnsignedLongType(Label.INVALID),
					NUM_DIMENSIONS);
		}
	}

	private Interval scaleIntervalToLevel(final Interval interval, final int intervalLevel, final int targetLevel) {

		if (intervalLevel == targetLevel) {
			return interval;
		}

		final double[] min = LongStream.of(Intervals.minAsLongArray(interval)).asDoubleStream().toArray();
		final double[] max = LongStream.of(Intervals.maxAsLongArray(interval)).asDoubleStream().map(d -> d + 1).toArray();
		final double[] relativeScale = DataSource.getRelativeScales(this, 0, targetLevel, intervalLevel);
		LOG.debug("Scaling interval {} {} {}", min, max, relativeScale);
		org.janelia.saalfeldlab.util.grids.Grids.scaleBoundingBox(min, max, min, max, relativeScale);
		return Intervals.smallestContainingInterval(new FinalRealInterval(min, max));

	}

	private TLongSet scaleBlocksToLevel(final TLongSet blocks, final int blocksLevel, final int targetLevel) {

		if (blocksLevel == targetLevel) {
			return blocks;
		}

		final CellGrid grid = this.dataCanvases[blocksLevel].getCellGrid();
		final CellGrid targetGrid = this.dataCanvases[targetLevel].getCellGrid();
		final double[] toTargetScale = DataSource.getRelativeScales(this, 0, blocksLevel, targetLevel);
		return org.janelia.saalfeldlab.util.grids.Grids.getRelevantBlocksInTargetGrid(
				blocks.toArray(),
				grid,
				targetGrid,
				toTargetScale
		);
	}

	private void scalePositionToLevel(final long[] position, final int intervalLevel, final int targetLevel, final
	long[] targetPosition) {

		Arrays.setAll(targetPosition, d -> position[d]);
		if (intervalLevel == targetLevel) {
			return;
		}

		final double[] positionDouble = LongStream.of(position).asDoubleStream().toArray();

		final Scale3D toTargetScale = new Scale3D(DataSource.getRelativeScales(this, 0, intervalLevel, targetLevel));

		toTargetScale.apply(positionDouble, positionDouble);

		Arrays.setAll(targetPosition, d -> (long)Math.ceil(positionDouble[d]));
	}

	public void resetMasks() throws MaskInUse {

		resetMasks(true);
	}

	public void resetMasks(final boolean clearOldMask) throws MaskInUse {

		synchronized (this) {
			final boolean canResetMask = !isCreatingMask() && !isApplyingMask.get();
			LOG.debug("Can reset mask? {}", canResetMask);
			if (!canResetMask)
				throw new MaskInUse("Cannot reset the mask.");

			var mask = getCurrentMask();
			if (mask != null && mask.shutdown != null)
				mask.shutdown.run();
			setCurrentMask(null);
			this.isBusy.set(true);
		}
		if (clearOldMask) {
			setMasksConstant();
		}

		this.isBusy.set(false);
	}

	public void forgetCanvases() throws CannotClearCanvas {

		synchronized (this) {
			if (this.isPersisting())
				throw new CannotClearCanvas("Currently persisting canvas -- try again later.");
			setCurrentMask(null);
		}
		clearCanvases();
	}

	public void persistCanvas() throws CannotPersist {

		persistCanvas(true);
	}

	public void persistCanvas(final boolean clearCanvas) throws CannotPersist {

		synchronized (this) {
			if (isMaskInUse()) {
				LOG.error(
						"Cannot persist canvas: is persisting? {} has mask? {} is creating mask? {} is applying mask? {}",
						this.isPersisting(),
						this.getCurrentMask() != null,
						this.isCreatingMask(),
						this.isApplyingMask
				);
				throw new CannotPersist("Can not persist canvas!");
			}
			this.isPersistingProperty.set(true);
			this.isBusy.set(true);
		}
		/* Start the task in the background */
		final var progressBar = new ProgressBar();
		progressBar.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		final ObservableList<String> states = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
		final Consumer<String> nextState = (text) -> InvokeOnJavaFXApplicationThread.invoke(() -> states.add(text));
		final Consumer<String> updateState = (update) -> InvokeOnJavaFXApplicationThread.invoke(() -> states.set(states.size() - 1, update));
		persistCanvasTask(clearCanvas, progressBar.progressProperty(), nextState, updateState);

		final BooleanBinding stillPersisting = Bindings.createBooleanBinding(
				() -> this.isPersisting() || progressBar.progressProperty().get() < 1.0,
				this.isPersistingProperty, progressBar.progressProperty()
		);

		/*Show the dialog and wait for committing to finish */
		LOG.warn("Creating commit status dialog.");
		final Alert isCommittingDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, false);
		isCommittingDialog.setHeaderText("Committing canvas.");
		/* The hidden FINISH button blocks the dialog from closing before OK is done. */
		isCommittingDialog.getButtonTypes().add(ButtonType.FINISH);
		final Node finishButton = isCommittingDialog.getDialogPane().lookupButton(ButtonType.FINISH);
		finishButton.setManaged(false);
		final Node okButton = isCommittingDialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(true);
		final var content = new VBox();
		final var statesText = new TextArea();
		statesText.setMouseTransparent(true);
		statesText.setEditable(false);
		statesText.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		final var textScrollPane = new ScrollPane(statesText);
		content.getChildren().add(new HBox(textScrollPane));
		content.getChildren().add(new HBox(progressBar));

		HBox.setHgrow(statesText, Priority.ALWAYS);
		HBox.setHgrow(progressBar, Priority.ALWAYS);
		VBox.setVgrow(statesText, Priority.ALWAYS);
		VBox.setVgrow(progressBar, Priority.ALWAYS);
		InvokeOnJavaFXApplicationThread.invoke(() -> isCommittingDialog.getDialogPane().setContent(content));
		states.addListener((ListChangeListener<String>)change -> statesText.setText(String.join("\n", states)));
		synchronized (this) {
			okButton.disableProperty().bind(stillPersisting);
		}

		LOG.info("Will show dialog? {}", stillPersisting.get());
		if (stillPersisting.get())
			isCommittingDialog.showAndWait();
	}

	private synchronized UtilityTask<?> persistCanvasTask(
			final boolean clearCanvas,
			final DoubleProperty progress,
			final Consumer<String> nextState,
			final Consumer<String> updateState
	) {

		LOG.debug("Merging canvas into background for blocks {}", this.affectedBlocks);
		final CachedCellImg<UnsignedLongType, ?> canvas = this.dataCanvases[0];
		final long[] affectedBlocks = this.affectedBlocks.toArray();
		this.affectedBlocks.clear();

		final Consumer<Double> animateProgressBar = newProgress -> {
			if (progress.get() > newProgress) {
				progress.set(0.0);
			}
			Timeline timeline = new Timeline();
			KeyValue keyValue = new KeyValue(progress, newProgress);
			KeyFrame keyFrame = new KeyFrame(new Duration(500), keyValue);
			timeline.getKeyFrames().add(keyFrame);
			InvokeOnJavaFXApplicationThread.invoke(timeline::play);
		};
		ChangeListener<Number> animateProgressBarListener = (obs, oldv, newv) -> animateProgressBar.accept(newv.doubleValue());
		return Tasks.createTask(() -> {
			try {
				nextState.accept("Persisting painted labels...");
				InvokeOnJavaFXApplicationThread.invoke(() ->
						this.persistCanvas.getProgressProperty().addListener(animateProgressBarListener)
				);

				final List<TLongObjectMap<PersistCanvas.BlockDiff>> blockDiffs = this.persistCanvas.persistCanvas(canvas, affectedBlocks);
				updateState.accept("Persisting painted labels...   Done");

				InvokeOnJavaFXApplicationThread.invoke(() -> {
					this.persistCanvas.getProgressProperty().removeListener(animateProgressBarListener);
					animateProgressBar.accept(0.0);
				});

				if (this.persistCanvas.supportsLabelBlockLookupUpdate()) {
					nextState.accept("Updating label-to-block lookup...");
					this.persistCanvas.updateLabelBlockLookup(blockDiffs);
					updateState.accept("Updating label-to-block lookup...   Done");
				}

				if (clearCanvas) {
					nextState.accept("Clearing canvases...");
					clearCanvases();
					updateState.accept("Clearing canvases...   Done");
					this.source.invalidateAll();
				} else
					LOG.info("Not clearing canvas.");

			} catch (UnableToPersistCanvas | UnableToUpdateLabelBlockLookup e) {
				throw new RuntimeException(e);
			}
		}).onSuccess(result -> {
			synchronized (this) {
				nextState.accept("Successfully finished committing canvas.");
			}
		}).onEnd((result, cause) -> {
			animateProgressBar.accept(1.0);
			this.isPersistingProperty.set(false);
			this.isBusy.set(false);
		}).onFailed(cause -> {
			synchronized (this) {
				LOG.error("Unable to commit canvas", cause);
				nextState.accept("Unable to commit canvas: " + cause.getMessage());
			}
		});
	}

	@Override
	public boolean isPresent(final int t) {

		return source.isPresent(t);
	}

	@Override
	public RandomAccessibleInterval<T> getSource(final int t, final int level) {

		final RealRandomAccessible<T> interpolatedSource = getInterpolatedSource(t, level, null);
		return Views.interval(Views.raster(interpolatedSource), new FinalInterval(source.getSource(t, level)));
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(final int time, final int level, final Interpolation method) {

		final RealRandomAccessible<T> sourceToExtend;

		// ignore interpolation method because we cannot use linear interpolation on LabelMultisetType
		final RealRandomAccessible<T> interpolatedSource = source.getInterpolatedSource(time, level, Interpolation.NEARESTNEIGHBOR);
		if (!showCanvasOverBackground.get() || affectedBlocks.isEmpty() && getCurrentMask() == null) {
			LOG.trace("Hide canvas or no mask/canvas data present -- delegate to underlying source");
			sourceToExtend = interpolatedSource;
		} else {
			final RealRandomAccessible<VolatileUnsignedLongType> canvas = Views.interpolate(
					Views.extendValue(
							this.canvases[level].getRai(),
							new VolatileUnsignedLongType(Label.INVALID)
					), new NearestNeighborInterpolatorFactory<>()
			);
			final RealRandomAccessible<VolatileUnsignedLongType> mask = this.tMasks[level];
			final RealRandomAccessibleTriple<T, VolatileUnsignedLongType, VolatileUnsignedLongType> composed = new
					RealRandomAccessibleTriple<>(
					interpolatedSource,
					canvas,
					mask
			);
			sourceToExtend = new PickOne<>(composed, pacT.copyWithDifferentNumOccurences(numContainedVoxels(level)));
		}

		// extend the interpolated source with the specified out of bounds value
		final RealInterval bounds = new FinalRealInterval(source.getSource(time, level));
		final RealRandomAccessibleRealInterval<T> boundedSource = new FinalRealRandomAccessibleRealInterval<>(sourceToExtend, bounds);
		return new ExtendedRealRandomAccessibleRealInterval<>(boundedSource, new RealOutOfBoundsConstantValueFactory<>(extensionT.copy()));
	}

	@Override
	public void getSourceTransform(final int t, final int level, final AffineTransform3D transform) {

		source.getSourceTransform(t, level, transform);
	}

	public void getSourceTransform(MaskInfo maskInfo, final AffineTransform3D transform) {

		getSourceTransform(maskInfo.time, maskInfo.level, transform);
	}

	public AffineTransform3D getSourceTransformForMask(MaskInfo maskInfo) {

		final var transform = new AffineTransform3D();
		getSourceTransform(maskInfo.time, maskInfo.level, transform);
		return transform;
	}

	@Override
	public T getType() {

		return source.getType();
	}

	@Override
	public String getName() {

		return source.getName();
	}

	// TODO VoxelDimensions is the only class pulled in by spim_data
	@Override
	public VoxelDimensions getVoxelDimensions() {

		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels() {

		return source.getNumMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval<D> getDataSource(final int t, final int level) {

		final RealRandomAccessible<D> interpolatedDataSource = getInterpolatedDataSource(t, level, null);
		return Views.interval(Views.raster(interpolatedDataSource), new FinalInterval(source.getDataSource(t, level)));
	}

	public RandomAccessibleInterval<D> getDataSourceForMask(MaskInfo maskInfo) {

		return getDataSource(maskInfo.time, maskInfo.level);
	}

	@Override
	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method) {

		final RealRandomAccessible<D> dataSourceToExtend;

		// ignore interpolation method because we cannot use linear interpolation on LabelMultisetType
		final RealRandomAccessible<D> interpolatedDataSource = source.getInterpolatedDataSource(t, level, Interpolation.NEARESTNEIGHBOR);
		if (!showCanvasOverBackground.get() || affectedBlocks.isEmpty() && getCurrentMask() == null) {
			LOG.trace("Hide canvas or no mask/canvas data present -- delegate to underlying source");
			dataSourceToExtend = interpolatedDataSource;
		} else {
			final RealRandomAccessible<UnsignedLongType> dataCanvas = Views.interpolate(
					Views.extendValue(
							dataCanvases[level],
							new UnsignedLongType(Label.INVALID)
					),
					new NearestNeighborInterpolatorFactory<>()
			);
			final RealRandomAccessible<UnsignedLongType> dataMask = dMasks[level];
			final RealRandomAccessibleTriple<D, UnsignedLongType, UnsignedLongType> composed = new RealRandomAccessibleTriple<>(
					interpolatedDataSource,
					dataCanvas,
					dataMask);
			dataSourceToExtend = new PickOne<>(composed, pacD.copyWithDifferentNumOccurences(numContainedVoxels(level)));
		}

		// extend the interpolated source with the specified out of bounds value
		final RealInterval bounds = new FinalRealInterval(source.getDataSource(t, level));
		final RealRandomAccessibleRealInterval<D> boundedDataSource = new FinalRealRandomAccessibleRealInterval<>(dataSourceToExtend, bounds);
		return new ExtendedRealRandomAccessibleRealInterval<>(boundedDataSource, new RealOutOfBoundsConstantValueFactory<>(extensionD.copy()));
	}

	@Override
	public D getDataType() {

		return source.getDataType();
	}

	public RandomAccessibleInterval<UnsignedLongType> getReadOnlyDataCanvas(final int t, final int level) {

		return Converters.convert(
				(RandomAccessibleInterval<UnsignedLongType>)this.dataCanvases[level],
				new TypeIdentity<>(),
				new UnsignedLongType()
		);
	}

	public RandomAccessibleInterval<D> getReadOnlyDataBackground(final int t, final int level) {

		return Converters.convert(
				this.source.getDataSource(t, level),
				new TypeIdentity<>(),
				this.source.getDataType().createVariable()
		);
	}

	/**
	 * Downsample affected blocks of img.
	 *
	 * @param source
	 * @param img
	 * @param affectedBlocks
	 * @param steps
	 * @param interval
	 * @param propagationExecutor
	 * @return map of labels to modified block ids
	 */
	private static Map<Long, Set<Long>> downsampleBlocks(
			final RandomAccessible<UnsignedLongType> source,
			final CachedCellImg<UnsignedLongType, LongAccess> img,
			final TLongSet affectedBlocks,
			final int[] steps,
			final Interval interval,
			final ExecutorService propagationExecutor) {

		final TaskExecutor taskExecutor = TaskExecutors.forExecutorService(propagationExecutor);

		final BlockSpec blockSpec = new BlockSpec(img.getCellGrid());

		final long[] intersectedCellMin = new long[blockSpec.grid.numDimensions()];
		final long[] intersectedCellMax = new long[blockSpec.grid.numDimensions()];

		final Map<Long, Set<Long>> blocksModifiedByLabel = new HashMap<>();
		LOG.debug("Initializing affected blocks: {}", affectedBlocks);
		final var labelsForBlockFutures = new ArrayList<Future<Pair<Long, Set<Long>>>>();
		for (final TLongIterator it = affectedBlocks.iterator(); it.hasNext(); ) {
			final long blockId = it.next();
			blockSpec.fromLinearIndex(blockId);

			Arrays.setAll(intersectedCellMin, d -> blockSpec.min[d]);
			Arrays.setAll(intersectedCellMax, d -> blockSpec.max[d]);

			intersect(intersectedCellMin, intersectedCellMax, interval);

			if (isNonEmpty(intersectedCellMin, intersectedCellMax)) {
				LOG.trace("Downsampling for intersected min/max: {} {}", intersectedCellMin, intersectedCellMax);
				final long[] min = new long[intersectedCellMin.length];
				final long[] max = new long[intersectedCellMax.length];
				System.arraycopy(intersectedCellMin, 0, min, 0, min.length);
				System.arraycopy(intersectedCellMax, 0, max, 0, max.length);
				final var future = propagationExecutor.submit(() -> new Pair<>(blockId, downsample(source, Views.interval(img, min, max), steps)));
				labelsForBlockFutures.add(future);
			}
		}
		for (Future<Pair<Long, Set<Long>>> labelsForBlockFuture : labelsForBlockFutures) {
			try {
				final Pair<Long, Set<Long>> futurePair = labelsForBlockFuture.get();
				final var blockId = futurePair.getKey();
				final Set<Long> labelsForBlock = futurePair.getValue();
				labelsForBlock.forEach(label -> blocksModifiedByLabel.computeIfAbsent(label, k -> new HashSet<>()).add(blockId));
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		return blocksModifiedByLabel;
	}

	/**
	 * @param source
	 * @param target
	 * @param steps
	 */
	private static <T extends IntegerType<T>> Set<Long> downsample(
			final RandomAccessible<T> source,
			final RandomAccessibleInterval<T> target,
			final int[] steps) {

		LOG.debug(
				"Downsampling ({} {}) with steps {}",
				Intervals.minAsLongArray(target),
				Intervals.maxAsLongArray(target),
				steps
		);

		final var zeroMinTarget = Views.zeroMin(target);
		final var sourceInterval = IntervalHelpers.scale(target, steps, true);
		final IntervalView<T> zeroMinSource = Views.zeroMin(Views.interval(source, sourceInterval));
		final HashSet<Long> labels = new HashSet<>();

		final RandomAccess<T> zeroMinSourceRA = zeroMinSource.randomAccess();
		final RandomAccess<T> zeroMinTargetRA = zeroMinTarget.randomAccess();

		final ReusableIntervalIterator sourceIntervalIterator = new ReusableIntervalIterator(sourceInterval);
		final long[] sourcePosMin = new long[zeroMinSource.numDimensions()];
		final long[] sourcePosMax = new long[zeroMinSource.numDimensions()];
		final var sourceTileInterval = new AbstractInterval(sourcePosMin, sourcePosMax, false) {

		};

		final TLongLongHashMap maxCounts = new TLongLongHashMap();

		var sourceTileIterable = new IntervalIterable(sourceIntervalIterator);
		for (long[] targetPos : new IntervalIterable(new LocalizingIntervalIterator(zeroMinTarget))) {
			for (int i = 0; i < sourcePosMin.length; i++) {
				sourcePosMin[i] = targetPos[i] * steps[i];
				sourcePosMax[i] = sourcePosMin[i] + steps[i] - 1;
			}
			sourceIntervalIterator.resetInterval(sourceTileInterval);

			var maxCount = -1L;
			var maxId = Label.INVALID;

			for (long[] sourcePos : sourceTileIterable) {
				final long sourceVal = zeroMinSourceRA.setPositionAndGet(sourcePos).getIntegerLong();
				if (sourceVal != Label.INVALID) {
					var curCount = maxCounts.adjustOrPutValue(sourceVal, 1, 1);
					if (curCount > maxCount) {
						maxCount++;
						maxId = sourceVal;
					}
				}
			}

			final T targetVal = zeroMinTargetRA.setPositionAndGet(targetPos);
			targetVal.setInteger(maxId);
			labels.add(maxId);
			maxCounts.clear();
		}
		labels.remove(Label.INVALID);
		return labels;
	}

	public TLongSet getModifiedBlocks(final int level, final long id) {

		LOG.debug("Getting modified blocks for level={} and id={}", level, id);
		return Optional.ofNullable(this.affectedBlocksByLabel[level].get(id)).map(TLongHashSet::new).orElseGet(
				TLongHashSet::new);
	}

	private void propagateMask(
			final RandomAccessibleInterval<UnsignedLongType> mask,
			final TLongSet paintedBlocksAtPaintedScale,
			final int paintedLevel,
			final Interval intervalAtPaintedScale,
			final Predicate<Long> isPaintedForeground,
			final ExecutorService propagationExecutor) {

		final RandomAccessibleInterval<UnsignedLongType> atPaintedLevel = dataCanvases[paintedLevel];
		for (int lowerResLevel = paintedLevel + 1; lowerResLevel < getNumMipmapLevels(); ++lowerResLevel) {
			final CachedCellImg<UnsignedLongType, LongAccess> lowerResCanvas = dataCanvases[lowerResLevel];
			final double[] paintedToLowerScales = DataSource.getRelativeScales(this, 0, paintedLevel, lowerResLevel);
			final Interval intervalAtLowerRes = scaleIntervalToLevel(intervalAtPaintedScale, paintedLevel, lowerResLevel);

			LOG.debug("Downsampling level {} of {}", lowerResLevel, getNumMipmapLevels());

			if (DoubleStream.of(paintedToLowerScales).filter(d -> Math.round(d) != d).count() > 0) {
				LOG.error(
						"Non-integer relative scales found for levels {} and {}: {} -- this does not make sense for label data -- aborting.",
						paintedLevel,
						lowerResLevel,
						paintedToLowerScales
				);
				throw new RuntimeException("Non-integer relative scales: " + Arrays.toString(paintedToLowerScales));
			}
			final TLongSet affectedBlocksAtLowerRes = this.scaleBlocksToLevel(paintedBlocksAtPaintedScale, paintedLevel, lowerResLevel);
			LOG.debug("Affected blocks at level {}: {}", lowerResLevel, affectedBlocksAtLowerRes);
			LOG.debug("Interval at lower resolution level: {} {}", Intervals.minAsLongArray(intervalAtLowerRes), Intervals.maxAsLongArray(intervalAtLowerRes));

			// downsample
			final int[] steps = DoubleStream.of(paintedToLowerScales).mapToInt(d -> (int)d).toArray();
			LOG.debug("Downsample step size: {}", steps);
			final var blocksModifiedByLabel = downsampleBlocks(
					Views.extendValue(atPaintedLevel, new UnsignedLongType(Label.INVALID)),
					lowerResCanvas,
					affectedBlocksAtLowerRes,
					steps,
					intervalAtLowerRes,
					propagationExecutor);
			for (Entry<Long, Set<Long>> entry : blocksModifiedByLabel.entrySet()) {
				final Long labelId = entry.getKey();
				final Set<Long> blocks = entry.getValue();
				synchronized (affectedBlocksByLabel) {
					affectedBlocksByLabel[lowerResLevel].computeIfAbsent(labelId, k -> new TLongHashSet()).addAll(blocks);
				}
			}
			LOG.debug("Downsampled level {}", lowerResLevel);
		}

		for (int higherResLevel = paintedLevel - 1; higherResLevel >= 0; --higherResLevel) {

			LOG.debug("Upsampling to higher resolution level={}", higherResLevel);
			final TLongSet affectedBlocksAtHigherRes = this.scaleBlocksToLevel(paintedBlocksAtPaintedScale, paintedLevel, higherResLevel);
			final double[] paintedToHigherScales = DataSource.getRelativeScales(this, 0, higherResLevel, paintedLevel);
			final Interval intervalAtHigherRes = scaleIntervalToLevel(intervalAtPaintedScale, paintedLevel, higherResLevel);

			// upsample
			final CachedCellImg<UnsignedLongType, LongAccess> higherResCanvas = dataCanvases[higherResLevel];
			final CellGrid highResGrid = higherResCanvas.getCellGrid();
			final int[] blockSize = new int[highResGrid.numDimensions()];
			highResGrid.cellDimensions(blockSize);

			final long[] cellPosHighRes = new long[highResGrid.numDimensions()];
			final long[] minHighRes = new long[highResGrid.numDimensions()];
			final long[] maxHighRes = new long[highResGrid.numDimensions()];
			final long[] stopHighRes = new long[highResGrid.numDimensions()];
			final long[] minPainted = new long[minHighRes.length];
			final long[] maxPainted = new long[minHighRes.length];

			final RealRandomAccessible<UnsignedLongType> higherResMask = this.dMasks[higherResLevel];

			for (final TLongIterator blockIterator = affectedBlocksAtHigherRes.iterator(); blockIterator.hasNext(); ) {
				final long blockId = blockIterator.next();
				highResGrid.getCellGridPositionFlat(blockId, cellPosHighRes);
				Arrays.setAll(minHighRes, d -> Math.min(cellPosHighRes[d] * blockSize[d], highResGrid.imgDimension(d) - 1));
				Arrays.setAll(maxHighRes, d -> Math.min(minHighRes[d] + blockSize[d], highResGrid.imgDimension(d)) - 1);
				Arrays.setAll(stopHighRes, d -> maxHighRes[d] + 1);
				this.scalePositionToLevel(minHighRes, higherResLevel, paintedLevel, minPainted);
				this.scalePositionToLevel(stopHighRes, higherResLevel, paintedLevel, maxPainted);
				Arrays.setAll(minPainted, d -> Math.min(Math.max(minPainted[d], mask.min(d)), mask.max(d)));
				Arrays.setAll(maxPainted, d -> Math.min(Math.max(maxPainted[d] - 1, mask.min(d)), mask.max(d)));

				final long[] intersectionMin = minHighRes.clone();
				final long[] intersectionMax = maxHighRes.clone();

				intersect(intersectionMin, intersectionMax, intervalAtHigherRes);

				if (isNonEmpty(intersectionMin, intersectionMax)) {

					LOG.debug("Intersected min={} max={}", intersectionMin, intersectionMax);

					LOG.debug(
							"Upsampling block: level={}, block min (target)={}, block max (target)={}, block min={}, " +
									"block max={}, scale={}, mask min={}, mask max={}",
							higherResLevel,
							minHighRes,
							maxHighRes,
							minPainted,
							maxPainted,
							paintedToHigherScales,
							Intervals.minAsLongArray(mask),
							Intervals.maxAsLongArray(mask)
					);

					final IntervalView<BoolType> relevantBlockAtPaintedResolution = Views.interval(
							Converters.convert(mask, (s, t) -> t.set(isPaintedForeground.test(s.get())), new BoolType()),
							minPainted,
							maxPainted
					);

					if (Intervals.numElements(relevantBlockAtPaintedResolution) == 0) {
						continue;
					}

					LOG.debug(
							"Upsampling for level {} and intersected intervals ({} {})",
							higherResLevel,
							intersectionMin,
							intersectionMax
					);

					final Interval interval = new FinalInterval(intersectionMin, intersectionMax);
					final RandomAccessibleInterval<RandomAccess<UnsignedLongType>> canvasAtHighResInterval = Views.interval(new BundleView<>(higherResCanvas), interval);
					final RandomAccessibleInterval<RandomAccess<UnsignedLongType>> maskOverInterval = Views.interval(new BundleView<>(Views.raster(higherResMask)), interval);
					final HashSet<Long> labels = new HashSet<>();

					LoopBuilder.setImages(canvasAtHighResInterval, maskOverInterval)
							.multiThreaded()
							.forEachPixel((canvasRa, maskVal) -> {
								final long maskLabel = maskVal.get().get();
								if (maskLabel != Label.INVALID) {
									canvasRa.get().set(maskLabel);
									labels.add(maskLabel);
								}
							});
					for (Long modifiedLabel : labels) {
						this.affectedBlocksByLabel[higherResLevel].computeIfAbsent(modifiedLabel, key -> new TLongHashSet()).add(blockId);
					}
				}
			}

		}
	}

	public static TLongSet affectedBlocks(final long[] gridDimensions, final int[] blockSize, final Interval...
			intervals) {

		final TLongHashSet blocks = new TLongHashSet();
		final int[] ones = IntStream.generate(() -> 1).limit(blockSize.length).toArray();
		final long[] relevantIntervalMin = new long[blockSize.length];
		final long[] relevantIntervalMax = new long[blockSize.length];
		for (final Interval interval : intervals) {
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

	public static TLongSet affectedBlocks(
			final RandomAccessibleInterval<?> input,
			final CellGrid grid,
			final Interval interval) {

		if (input instanceof AccessedBlocksRandomAccessible<?>) {
			final var tracker = (AccessedBlocksRandomAccessible<?>)input;
			if (grid.equals(tracker.getGrid())) {
				final long[] blocks = tracker.listBlocks();
				LOG.debug("Got these blocks from tracker: {}", blocks);
				return new TLongHashSet(blocks);
			}
		}
		return affectedBlocks(grid, interval);
	}

	public static TLongSet affectedBlocks(final CellGrid grid, final Interval interval) {

		return affectedBlocks(grid.getGridDimensions(), grid.getCellDimensions(), interval);
	}

	public static TLongSet affectedBlocks(final long[] gridDimensions, final int[] blockSize, final Interval intervalOverImg) {

		final TLongHashSet blocks = new TLongHashSet();
		final int[] ones = IntStream.generate(() -> 1).limit(blockSize.length).toArray();
		final long[] intervalMinOverGrid = IntStream.range(0, gridDimensions.length).mapToLong(it -> intervalOverImg.min(it) / blockSize[it]).toArray();
		final long[] intervalMaxOverGrid = IntStream.range(0, gridDimensions.length).mapToLong(it -> intervalOverImg.max(it) / blockSize[it]).toArray();
		Grids.forEachOffset(
				intervalMinOverGrid,
				intervalMaxOverGrid,
				ones,
				gridPosition -> blocks.add(IntervalIndexer.positionToIndex(gridPosition, gridDimensions))
		);

		return blocks;
	}

	public static TLongSet affectedBlocksInHigherResolution(
			final TLongSet blocksInLowRes,
			final CellGrid lowResGrid,
			final CellGrid highResGrid,
			final int[] relativeScalingFactors) {

		assert lowResGrid.numDimensions() == highResGrid.numDimensions();

		final int[] blockSizeLowRes = new int[lowResGrid.numDimensions()];
		Arrays.setAll(blockSizeLowRes, lowResGrid::cellDimension);

		final long[] gridDimHighRes = highResGrid.getGridDimensions();
		final int[] blockSizeHighRes = new int[highResGrid.numDimensions()];
		Arrays.setAll(blockSizeHighRes, highResGrid::cellDimension);

		final long[] blockMin = new long[lowResGrid.numDimensions()];
		final long[] blockMax = new long[lowResGrid.numDimensions()];

		final TLongHashSet blocksInHighRes = new TLongHashSet();

		final int[] ones = IntStream.generate(() -> 1).limit(highResGrid.numDimensions()).toArray();

		for (final TLongIterator it = blocksInLowRes.iterator(); it.hasNext(); ) {
			final long index = it.next();
			lowResGrid.getCellGridPositionFlat(index, blockMin);
			for (int d = 0; d < blockMin.length; ++d) {
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

	public static <M extends BooleanType<M>, C extends IntegerType<C>> Map<Long, TLongHashSet> paintAffectedPixels(
			final TLongSet relevantBlocks,
			final SourceMask mask,
			final RandomAccessibleInterval<C> canvas,
			final CellGrid grid,
			final Interval paintedInterval,
			final Predicate<Long> acceptAsPainted) {

		final long[] currentMin = new long[grid.numDimensions()];
		final long[] currentMax = new long[grid.numDimensions()];
		final long[] gridPosition = new long[grid.numDimensions()];
		final long[] gridDimensions = grid.getGridDimensions();
		final int[] blockSize = new int[grid.numDimensions()];
		grid.cellDimensions(blockSize);

		final AtomicReference<HashMap<Long, TLongHashSet>> labelToBlocks = new AtomicReference<>(new HashMap<>());

		final ThreadFactory build = new ThreadFactoryBuilder().setNameFormat("paint-affected-pixels-%d").build();
		final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), build);
		final List<Future<?>> jobs = new ArrayList<>();
		for (final TLongIterator blockIt = relevantBlocks.iterator(); blockIt.hasNext(); ) {
			final long blockId = blockIt.next();
			IntervalIndexer.indexToPosition(blockId, gridDimensions, gridPosition);

			final long[] gridMin = new long[grid.numDimensions()];
			final long[] gridMax = new long[grid.numDimensions()];
			for (int idx = 0; idx < grid.numDimensions(); idx++) {
				gridMin[idx] = gridPosition[idx] * blockSize[idx];
				gridMax[idx] = gridMin[idx] + blockSize[idx] - 1;
			}
			final Interval gridInterval = new FinalInterval(gridMin, gridMax);

			final var restrictedInterval = Intervals.intersect(gridInterval, paintedInterval);
			if (Intervals.isEmpty(restrictedInterval)) {
				LOG.trace("Affected Block not in painted interval. block: {}", blockId);
				continue;
			}

			final Future<?> job = threadPool.submit(() -> {
				LOG.trace("Painting affected pixels for: {} {} {}", blockId, currentMin, currentMax);
				final IntervalView<C> canvasOverRestricted = Views.interval(canvas, restrictedInterval);
				final var labelsForBlock = mask.applyMaskToCanvas(canvasOverRestricted, acceptAsPainted);
				labelToBlocks.getAndUpdate(map -> {
					final HashMap<Long, TLongHashSet> updatedMap = new HashMap<>(map);
					for (Long label : labelsForBlock) {
						updatedMap.computeIfAbsent(label, k -> new TLongHashSet()).add(blockId);
					}
					return updatedMap;
				});
			});
			jobs.add(job);
		}
		for (Future<?> job : jobs) {
			try {
				job.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		threadPool.shutdown();
		return labelToBlocks.getAcquire();
	}

	/**
	 * Intersect min,max with interval. Intersected min/max will be written into input min/max.
	 *
	 * @param min
	 * @param max
	 * @param interval
	 */
	public static void intersect(final long[] min, final long[] max, final Interval interval) {

		for (int d = 0; d < min.length; ++d) {
			min[d] = Math.max(min[d], interval.min(d));
			max[d] = Math.min(max[d], interval.max(d));
		}
	}

	public static boolean isNonEmpty(final long[] min, final long[] max) {

		for (int d = 0; d < min.length; ++d) {
			if (max[d] < min[d]) {
				return false;
			}
		}
		return true;
	}

	private void clearCanvases() {

		this.cacheDirectory.set(this.nextCacheDirectory.get());
		this.affectedBlocks.clear();
		Arrays.stream(this.affectedBlocksByLabel).forEach(Map::clear);
		this.canvasClearedListeners.forEach(Runnable::run);
	}

	@Override
	public void invalidate(final Long key) {
		// TODO what to do with canvas?
		this.source.invalidate(key);
	}

	@Override
	public void invalidateIf(final long parallelismThreshold, final Predicate<Long> condition) {
		// TODO what to do with canvas?
		this.source.invalidateIf(parallelismThreshold, condition);
	}

	@Override
	public void invalidateIf(final Predicate<Long> condition) {
		// TODO what to do with canvas?
		this.source.invalidateIf(condition);
	}

	@Override
	public void invalidateAll(final long parallelismThreshold) {
		// TODO what to do with canvas?
		this.source.invalidateAll(parallelismThreshold);
	}

	@Override
	public void invalidateAll() {
		// TODO what to do with canvas?
		this.source.invalidateAll();
	}

	private static class CanvasBaseDirChangeListener implements ChangeListener<String> {

		private final SharedQueue queue;

		private final DiskCachedCellImg<UnsignedLongType, ?>[] dataCanvases;

		private final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType>[] canvases;

		private final long[][] dimensions;

		private final int[][] blockSizes;

		public CanvasBaseDirChangeListener(
				final SharedQueue queue,
				final DiskCachedCellImg<UnsignedLongType, ?>[] dataCanvases,
				final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType>[] canvases,
				final long[][] dimensions,
				final int[][] blockSizes) {

			super();
			this.queue = queue;
			this.dataCanvases = dataCanvases;
			this.canvases = canvases;
			this.dimensions = dimensions;
			this.blockSizes = blockSizes;
		}

		@Override
		public void changed(final ObservableValue<? extends String> observable, final String oldValue, final String newValue) {

			Optional.ofNullable(oldValue).map(Paths::get).ifPresent(DiskCellCache::addDeleteHook);
			// TODO add clean-up job that already starts deleting before jvm shutdown

			LOG.info("Updating cache directory: observable={} oldValue={} newValue={}", observable, oldValue,
					newValue);

			final DiskCachedCellImgOptions opts = DiskCachedCellImgOptions
					.options()
					.volatileAccesses(true)
					.dirtyAccesses(true);

			for (int level = 0; level < canvases.length; ++level) {
				if (newValue != null) {
					final Path cacheDir = Paths.get(newValue, String.format("%d", level));
					final DiskCachedCellImgOptions o = opts
							.volatileAccesses(true)
							.dirtyAccesses(true)
							.cacheDirectory(cacheDir)
							.deleteCacheDirectoryOnExit(true)
							.cellDimensions(blockSizes[level]);
					final DiskCachedCellImgFactory<UnsignedLongType> f = new DiskCachedCellImgFactory<>(new UnsignedLongType(), o);
					final CellLoader<UnsignedLongType> loader = img -> img.forEach(t -> t.set(Label.INVALID));
					final DiskCachedCellImg<UnsignedLongType, ?> store = f.create(dimensions[level], loader, o);
					final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType> vstore = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate((
									DiskCachedCellImg)store,
							queue,
							new CacheHints(LoadingStrategy.VOLATILE, canvases.length - 1 - level, true));

					if (dataCanvases[level] != null) {
						this.dataCanvases[level].shutdown();
						this.dataCanvases[level].getCache().invalidateAll();
						this.dataCanvases[level].getCache().invalidateAll();
					}
					// TODO how to invalidate volatile canvases?
					if (canvases[level] != null && canvases[level].getInvalidate() != null)
						canvases[level].getInvalidate().invalidateAll();
					this.dataCanvases[level] = store;
					this.canvases[level] = vstore;
				}
			}
		}

	}

	public DataSource<D, T> underlyingSource() {

		return this.source;
	}

	public ReadOnlyStringProperty currentCanvasDirectoryProperty() {

		return this.cacheDirectory;
	}

	public String currentCanvasDirectory() {

		return this.cacheDirectory.get();
	}

	public PersistCanvas getPersister() {

		return this.persistCanvas;
	}

	public CellGrid getCellGrid(final int t, final int level) {

		return ((AbstractCellImg<?, ?, ?, ?>)underlyingSource().getSource(t, level)).getCellGrid();
	}

	@Override
	public CellGrid getGrid(final int level) {

		return getCellGrid(0, level);
	}

	public long[] getAffectedBlocks() {

		return this.affectedBlocks.toArray();
	}

	public void addOnCanvasClearedListener(final Runnable listener) {

		this.canvasClearedListeners.add(listener);
	}

	Map<Long, long[]>[] getAffectedBlocksById() {

		@SuppressWarnings("unchecked") final Map<Long, long[]>[] maps = new HashMap[this.affectedBlocksByLabel.length];

		for (int level = 0; level < maps.length; ++level) {
			maps[level] = new HashMap<>();
			final Map<Long, long[]> map = maps[level];
			for (final Entry<Long, TLongHashSet> entry : this.affectedBlocksByLabel[level].entrySet()) {
				map.put(entry.getKey(), entry.getValue().toArray());
			}
		}

		LOG.debug("Retruning affected blocks by id for {} levels: {}", maps.length, maps);
		return maps;
	}

	void affectBlocks(
			final long[] blocks,
			final Map<Long, long[]>[] blocksById) {

		assert this.affectedBlocksByLabel.length == blocksById.length;

		LOG.debug("Affected blocks: {} to add: {}", this.affectedBlocks, blocks);
		this.affectedBlocks.addAll(blocks);
		LOG.debug("Affected blocks: {}", this.affectedBlocks);

		LOG.debug("Affected blocks by id: {} to add: {}", this.affectedBlocksByLabel, blocksById);
		for (int level = 0; level < blocksById.length; ++level) {
			final Map<Long, TLongHashSet> map = this.affectedBlocksByLabel[level];
			for (final Entry<Long, long[]> entry : blocksById[level].entrySet()) {
				map.computeIfAbsent(entry.getKey(), key -> new TLongHashSet()).addAll(entry.getValue());
			}
		}
		LOG.debug("Affected blocks by id: {}", this.affectedBlocksByLabel, null);

	}

	private DiskCachedCellImgOptions getMaskDiskCachedCellImgOptions(final int level) {

		return DiskCachedCellImgOptions
				.options()
				.volatileAccesses(true)
				.cellDimensions(this.blockSizes[level]);
	}

	private DiskCachedCellImg<UnsignedLongType, ?> createMaskStore(
			final DiskCachedCellImgOptions maskOpts,
			final int level) {

		final CellLoader<UnsignedLongType> loader = img -> img.forEach(pixel -> pixel.set(Label.INVALID));
		return new DiskCachedCellImgFactory<>(new UnsignedLongType(Label.INVALID), maskOpts)
				.create(source.getSource(0, level), loader);

	}

	private DiskCachedCellImg<UnsignedLongType, ?> createMaskStore(
			final DiskCachedCellImgOptions maskOpts,
			final int[] dimensions, long defaultValue) {

		final CellLoader<UnsignedLongType> loader = img -> img.forEach(pixel -> pixel.set(defaultValue));
		return new DiskCachedCellImgFactory<>(new UnsignedLongType(defaultValue), maskOpts)
				.create(Arrays.stream(dimensions).mapToLong(it -> it).toArray(), loader);
	}

	public Pair<DiskCachedCellImg<UnsignedLongType, ?>, TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType>> createMaskStoreWithVolatile(
			final int level) {

		final DiskCachedCellImgOptions maskOpts = getMaskDiskCachedCellImgOptions(level);
		final DiskCachedCellImg<UnsignedLongType, ?> store = createMaskStore(maskOpts, level);
		final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType> vstore =
				TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(
						(CachedCellImg)store,
						queue,
						new CacheHints(LoadingStrategy.VOLATILE, 0, true));
		return new Pair<>(store, vstore);
	}

	public Pair<DiskCachedCellImg<UnsignedLongType, ?>, TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType>> createMaskStoreWithVolatile(
			final int[] cellDimensions,
			final int[] imgDimensions,
			long defaultValue) {

		final var maskOpts = DiskCachedCellImgOptions
				.options()
				.volatileAccesses(true)
				.cellDimensions(cellDimensions);
		final DiskCachedCellImg<UnsignedLongType, ?> store = createMaskStore(maskOpts, imgDimensions, defaultValue);
		final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedLongType> vstore =
				TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(
						(CachedCellImg)store,
						queue,
						new CacheHints(LoadingStrategy.VOLATILE, 0, true));
		return new Pair<>(store, vstore);
	}

	private void setMasks(
			final RandomAccessibleInterval<UnsignedLongType> store,
			final RandomAccessibleInterval<VolatileUnsignedLongType> vstore,
			final int maskLevel,
			final Predicate<Long> isPaintedForeground) {

		setAtMaskLevel(store, vstore, maskLevel, isPaintedForeground);
		LOG.debug("Created mask at scale level {}", maskLevel);
		setMaskScaleLevels(maskLevel);
	}

	private void setMasks(
			final RealRandomAccessible<UnsignedLongType> mask,
			final RealRandomAccessible<VolatileUnsignedLongType> vmask,
			final int maskLevel,
			final Predicate<Long> acceptMask) {

		setAtMaskLevel(mask, vmask, maskLevel, acceptMask);
		LOG.debug("Created mask at scale level {}", maskLevel);
		setMaskScaleLevels(maskLevel);
	}

	private void setMaskScaleLevels(final int maskLevel) {

		final RealRandomAccessible<UnsignedLongType> dMask = this.dMasks[maskLevel];
		final RealRandomAccessible<VolatileUnsignedLongType> tMask = this.tMasks[maskLevel];

		// get mipmap transforms
		final AffineTransform3D[] levelToFullResTransform = new AffineTransform3D[getNumMipmapLevels()];
		final AffineTransform3D fullResTransform = new AffineTransform3D();
		getSourceTransform(0, 0, fullResTransform);
		for (int level = 0; level < getNumMipmapLevels(); ++level) {
			levelToFullResTransform[level] = new AffineTransform3D();
			getSourceTransform(0, level, levelToFullResTransform[level]);
			levelToFullResTransform[level].preConcatenate(fullResTransform.inverse());
		}

		for (int level = 0; level < getNumMipmapLevels(); ++level) {
			if (level != maskLevel) {
				final AffineTransform3D maskToLevelTransform = new AffineTransform3D();
				maskToLevelTransform.preConcatenate(levelToFullResTransform[maskLevel]).preConcatenate(levelToFullResTransform[level].inverse());
				this.dMasks[level] = RealViews.affineReal(dMask, maskToLevelTransform);
				this.tMasks[level] = RealViews.affineReal(tMask, maskToLevelTransform);
			}
		}
	}

	private void setAtMaskLevel(
			final RandomAccessibleInterval<UnsignedLongType> store,
			final RandomAccessibleInterval<VolatileUnsignedLongType> vstore,
			final int maskLevel,
			final Predicate<Long> acceptLabel) {

		setAtMaskLevel(
				Views.interpolate(Views.extendZero(store), new NearestNeighborInterpolatorFactory<>()),
				Views.interpolate(Views.extendZero(vstore), new NearestNeighborInterpolatorFactory<>()),
				maskLevel,
				acceptLabel);

		if (store instanceof AccessedBlocksRandomAccessible<?>) {
			((AccessedBlocksRandomAccessible)store).clear();
		}
		if (vstore instanceof AccessedBlocksRandomAccessible<?>) {
			((AccessedBlocksRandomAccessible)vstore).clear();
		}
	}

	private void setAtMaskLevel(
			final RealRandomAccessible<UnsignedLongType> mask,
			final RealRandomAccessible<VolatileUnsignedLongType> vmask,
			final int maskLevel,
			final Predicate<Long> acceptLabel) {

		this.dMasks[maskLevel] = Converters.convert(
				mask,
				(input, output) -> {
					if (acceptLabel.test(input.get())) {
						output.set(input.get());
					} else {
						output.set(INVALID);
					}
				},
				new UnsignedLongType(Label.INVALID));

		this.tMasks[maskLevel] = Converters.convert(
				vmask,
				(input, output) -> {
					final boolean isValid = input.isValid();
					output.setValid(isValid);
					if (isValid) {
						final long inputVal = input.get().get();
						if (acceptLabel.test(inputVal)) {
							output.get().set(inputVal);
						} else {
							output.get().set(INVALID);
						}
					}
				},
				new VolatileUnsignedLongType(Label.INVALID));
	}

	public Interval getCanvasInterval(final int level, final long label) {

		final CellGrid cellGrid = getCellGrid(0, level);
		final RandomAccess<Interval> cellIntervals = cellGrid.cellIntervals().randomAccess();
		final TLongSet modifiedBlocks = getModifiedBlocks(level, label);
		final long[] cellPos = new long[cellGrid.numDimensions()];
		final Interval[] unionInterval = new Interval[]{new FinalInterval(0, 0, 0)};
		modifiedBlocks.forEach(block -> {
			cellGrid.getCellGridPositionFlat(block, cellPos);
			unionInterval[0] = Intervals.union(unionInterval[0], cellIntervals.setPositionAndGet(cellPos));
			return true;
		});
		return unionInterval[0];
	}

	private static javafx.scene.control.Label[] asLabels(final List<? extends String> strings) {

		return strings.stream().map(javafx.scene.control.Label::new).toArray(javafx.scene.control.Label[]::new);
	}

	private int numContainedVoxels(final int targetLevel) {
		// always compare to original level to get number of contained voxels.
		final double[] resolution = DataSource.getRelativeScales(this, 0, 0, targetLevel);
		return (int)Math.ceil(resolution[0] * resolution[1] * resolution[2]);
	}

}
