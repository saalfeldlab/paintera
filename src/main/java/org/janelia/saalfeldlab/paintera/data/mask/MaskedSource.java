package org.janelia.saalfeldlab.paintera.data.mask;

import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.util.Pair;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.FinalRealRandomAccessibleRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.RealRandomAccessibleRealInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.Cache;
import net.imglib2.cache.Invalidate;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.outofbounds.RealOutOfBoundsConstantValueFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.NativeType;
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
import net.imglib2.view.ExtendedRealRandomAccessibleRealInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RealRandomAccessibleTriple;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static net.imglib2.img.basictypeaccess.AccessFlags.DIRTY;
import static net.imglib2.img.basictypeaccess.AccessFlags.VOLATILE;

/**
 *
 * Wrap a paintable canvas around a source that provides background.
 *
 * In order to allow for sources that are as general as possible, {@link MaskedSource} adds two layers on top of
 * the original data provided by the underlying source. In total, that makes three layers of data (from deepest to most shallow/short lived):
 * <ul>
 * <li>Background provided by underlying source</li>
 * <li>Canvas that stores current paintings projected to all mipmap levels. Can be merged into background when triggered by user</li>
 * <li>Mask is only active when the user currently paints and only valid for one specific mipmap level at a time (other levels are interpolated).
 * Once a painting action is finished, the mask should be submitted back to the {@link MaskedSource}, to merge it into the canvas and propagate
 * to all mipmap levels.</li>
 * </ul>
 *
 * Only one (or no) mask can be active at any time. {@link MaskedSource} will throw an appropriate exception if a mask is requested while
 * any of these are true:
 * <ul>
 * <li>A mask has been requested previously and is still in use (i.e. not submitted back)</li>
 * <li>A mask was submitted back and propagation of the mask to all mipmap levels of the canvas is still underway</li>
 * <li>The canvas is currently being committed into the background.</li>
 * </ul>
 *
 * The mask that is currently active has to be unique. If a mask that is not identical to the currently active mask is submitted back, or
 * {@link MaskedSource} is busy with committing to the background an appropriate exception will be thrown.
 *
 * The canvas can only be committed to the background if currently no mask is active and no mask is being submitted/propagated back into
 * the canvas. In either of those cases, an appropriate exception is thrown.
 *
 *
 *
 *
 * @param <D> data type
 * @param <T> viewer type
 */
public class MaskedSource<D extends Type<D>, T extends Type<T>> implements DataSource<D, T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int NUM_DIMENSIONS = 3;

	private final UnsignedLongType INVALID = new UnsignedLongType(Label.INVALID);

	private final DataSource<D, T> source;

	private final SharedQueue queue;

	private final DiskCachedCellImg<UnsignedLongType, LongAccess>[] dataCanvases;

	private final RaiWithInvalidate<VolatileUnsignedLongType>[] canvases;

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

	private Mask<UnsignedLongType> currentMask = null;

	private boolean isPersisting = false;

	private boolean isCreatingMask = false;

	private final BooleanProperty isApplyingMask = new SimpleBooleanProperty();

	private final Map<Long, TLongHashSet>[] affectedBlocksByLabel;

	private final List<Runnable> canvasClearedListeners = new ArrayList<>();

	private final BooleanProperty showCanvasOverBackground = new SimpleBooleanProperty(this, "show canvas", true);

	private static class RaiWithInvalidate<T> {

		public final RandomAccessibleInterval<T> rai;

		public final Invalidate<Long> invalidate;

		private RaiWithInvalidate(RandomAccessibleInterval<T> rai, Invalidate<Long> invalidate) {
			this.rai = rai;
			this.invalidate = invalidate;
		}
	}

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
			final ExecutorService propagationExecutor)
	{
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
			final ExecutorService propagationExecutor)
	{
		super();
		this.source = source;
		this.queue = queue;
		this.dimensions = IntStream
				.range(0, source.getNumMipmapLevels())
				.mapToObj(level -> Intervals.dimensionsAsLongArray(this.source.getSource(0, level)))
				.toArray(long[][]::new);
		this.blockSizes = blockSizes;
		this.dataCanvases = new DiskCachedCellImg[source.getNumMipmapLevels()];
		this.canvases = new RaiWithInvalidate[source.getNumMipmapLevels()];
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

		this.affectedBlocksByLabel = Stream.generate(HashMap::new).limit(this.canvases.length).toArray(Map[]::new);

		setMasksConstant();

	}

	public ReadOnlyBooleanProperty isApplyingMaskProperty()
	{
		return isApplyingMask;
	}

	public BooleanProperty showCanvasOverBackgroundProperty()
	{
		return showCanvasOverBackground;
	}

	public Mask<UnsignedLongType> getCurrentMask()
	{
		return currentMask;
	}

	public Mask<UnsignedLongType> generateMask(
			final MaskInfo<UnsignedLongType> maskInfo,
			final Predicate<UnsignedLongType> isPaintedForeground)
	throws MaskInUse
	{
		LOG.debug("Asking for mask: {}", maskInfo);
		synchronized (this)
		{
			final boolean canGenerateMask = !isCreatingMask && currentMask == null && !isApplyingMask.get() && !isPersisting;
			LOG.debug("Can generate mask? {}", canGenerateMask);
			if (!canGenerateMask)
			{
				LOG.error(
						"Currently processing, cannot generate new mask: persisting? {} mask in use? {}",
						isPersisting,
						currentMask);
				throw new MaskInUse("Busy, cannot generate new mask.");
			}
			this.isCreatingMask = true;
		}
		LOG.debug("Generating mask: {}", maskInfo);

		final Pair<DiskCachedCellImg<UnsignedLongType, ?>, RaiWithInvalidate<VolatileUnsignedLongType>>
				storeWithVolatile = createMaskStoreWithVolatile(maskInfo.level);
		final DiskCachedCellImg<UnsignedLongType, ?> store = storeWithVolatile.getKey();
		final RaiWithInvalidate<VolatileUnsignedLongType> vstore  = storeWithVolatile.getValue();
		setMasks(store, vstore.rai, maskInfo.level, maskInfo.value, isPaintedForeground);
		final AccessedBlocksRandomAccessible<UnsignedLongType> trackingStore = new AccessedBlocksRandomAccessible<>(
				store,
				store.getCellGrid());
		final Mask<UnsignedLongType> mask = new Mask<>(maskInfo, trackingStore, store.getCache(), vstore.invalidate, store::shutdown);
		synchronized (this)
		{
			this.currentMask = mask;
			this.isCreatingMask = false;
		}
		return mask;
	}

	public void setMask(
			final Mask<UnsignedLongType> mask,
			final Predicate<UnsignedLongType> isPaintedForeground)
	throws MaskInUse
	{
		synchronized (this)
		{
			final boolean canSetMask = !isCreatingMask && currentMask == null && !isApplyingMask.get() && !isPersisting;
			LOG.debug("Can set mask? {}", canSetMask);
			if (!canSetMask)
			{
				LOG.error(
						"Currently processing, cannot set new mask: persisting? {} mask in use? {}",
						isPersisting,
						currentMask);
				throw new MaskInUse("Busy, cannot set new mask.");
			}
			this.isCreatingMask = true;
		}

		// TODO should we always require CachedcellImage for mask.mask? That would make this check obsolete.
		final RandomAccessibleInterval<UnsignedLongType> store;
		if (mask.mask instanceof AccessedBlocksRandomAccessible<?>)
			store = ((AccessedBlocksRandomAccessible<UnsignedLongType>) mask.mask).getSource();
		else
			store = mask.mask;
		// TODO replace VolatileViews.wrapAsVolatile with method that returns Cache/Invalidate as well.
		final RandomAccessibleInterval<VolatileUnsignedLongType> vstore = VolatileViews.wrapAsVolatile(store, queue, new CacheHints(LoadingStrategy.VOLATILE, 0, true));

		setMasks(store, vstore, mask.info.level, mask.info.value, isPaintedForeground);

		synchronized (this)
		{
			this.currentMask = mask;
			this.isCreatingMask = false;
		}
	}

	public void setMask(
			final MaskInfo<UnsignedLongType> maskInfo,
			final RealRandomAccessible<UnsignedLongType> mask,
			final RealRandomAccessible<VolatileUnsignedLongType> vmask,
			final Invalidate<?> invalidate,
			final Invalidate<?> volatileInvalidate,
			final Runnable shutdown,
			final Predicate<UnsignedLongType> isPaintedForeground)
	throws MaskInUse
	{
		synchronized (this)
		{
			final boolean canSetMask = !isCreatingMask && currentMask == null && !isApplyingMask.get() && !isPersisting;
			LOG.debug("Can set mask? {}", canSetMask);
			if (!canSetMask)
			{
				LOG.error(
						"Currently processing, cannot set new mask: persisting? {} mask in use? {}",
						isPersisting,
						currentMask
				         );
				throw new MaskInUse("Busy, cannot set new mask.");
			}
			this.isCreatingMask = true;
		}

		setMasks(mask, vmask, maskInfo.level, maskInfo.value, isPaintedForeground);

		synchronized (this)
		{
			final RandomAccessibleInterval<UnsignedLongType> rasteredMask = Views.interval(Views.raster(mask), source.getSource(0, maskInfo.level));
			// TODO how to get invalidateVolatile here?
			this.currentMask = new Mask<>(maskInfo, rasteredMask, invalidate, volatileInvalidate, shutdown);
			this.isCreatingMask = false;
		}
	}

	public void applyMask(
			final Mask<UnsignedLongType> mask,
			final Interval paintedInterval,
			final Predicate<UnsignedLongType> acceptAsPainted)
	{
		if (mask == null)
			return;
		new Thread(() -> {
			Thread.currentThread().setName("apply mask");
			synchronized (this)
			{
				final boolean maskCanBeApplied = !this.isCreatingMask && this.currentMask == mask && !this.isApplyingMask.get() && !this.isPersisting;
				if (!maskCanBeApplied)
				{
					LOG.debug("Did not pass valid mask {}, will not do anything", mask);
					return;
				}
				this.isApplyingMask.set(true);
			}

			LOG.debug("Applying mask: {}", mask, paintedInterval);
			final MaskInfo<UnsignedLongType> maskInfo = mask.info;
			final CachedCellImg<UnsignedLongType, ?> canvas = dataCanvases[maskInfo.level];
			final CellGrid                           grid   = canvas.getCellGrid();

			final int[] blockSize = new int[grid.numDimensions()];
			grid.cellDimensions(blockSize);

			final TLongSet affectedBlocks = affectedBlocks(mask.mask, canvas.getCellGrid(), paintedInterval);

			paintAffectedPixels(
					affectedBlocks,
					Converters.convert(
							Views.extendZero(mask.mask),
							(s, t) -> t.set(acceptAsPainted.test(s)),
							new BitType()),
					canvas,
					maskInfo.value,
					canvas.getCellGrid(),
					paintedInterval);

			final Mask<UnsignedLongType> currentMaskBeforePropagation = this.currentMask;
			synchronized (this)
			{
				this.currentMask = null;
			}

			final TLongSet paintedBlocksAtHighestResolution = this.scaleBlocksToLevel(
					affectedBlocks,
					maskInfo.level,
					0);

			this.affectedBlocksByLabel[maskInfo.level].computeIfAbsent(
					maskInfo.value.getIntegerLong(),
					key -> new TLongHashSet()
			                                                          ).addAll(affectedBlocks);
			LOG.debug("Added affected block: {}", affectedBlocksByLabel[maskInfo.level]);
			this.affectedBlocks.addAll(paintedBlocksAtHighestResolution);

			propagationExecutor.submit(() -> {
				try {
					propagateMask(
							mask.mask,
							affectedBlocks,
							maskInfo.level,
							maskInfo.value,
							paintedInterval,
							acceptAsPainted);
					setMasksConstant();
					synchronized (this) {
						LOG.debug("Done applying mask!");
						this.isApplyingMask.set(false);
					}
				} finally {
					// free resources
					if (currentMaskBeforePropagation != null) {
						if (currentMaskBeforePropagation.shutdown != null)
							currentMaskBeforePropagation.shutdown.run();
						if (currentMaskBeforePropagation.invalidate != null)
							currentMaskBeforePropagation.invalidate.invalidateAll();
						if (currentMaskBeforePropagation.invalidateVolatile != null)
							currentMaskBeforePropagation.invalidateVolatile.invalidateAll();
					}
				}
			});

		}).start();

	}

	private void setMasksConstant()
	{
		for (int level = 0; level < getNumMipmapLevels(); ++level)
		{
			this.dMasks[level] = ConstantUtils.constantRealRandomAccessible(
					new UnsignedLongType(Label.INVALID),
					NUM_DIMENSIONS);
			this.tMasks[level] = ConstantUtils.constantRealRandomAccessible(
					new VolatileUnsignedLongType(Label.INVALID),
					NUM_DIMENSIONS);
		}
	}

	private Interval scaleIntervalToLevel(final Interval interval, final int intervalLevel, final int targetLevel)
	{
		if (intervalLevel == targetLevel) { return interval; }

		final double[] min = LongStream.of(Intervals.minAsLongArray(interval)).asDoubleStream().toArray();
		final double[] max = LongStream.of(Intervals.maxAsLongArray(interval)).asDoubleStream().map(d -> d + 1).toArray();
		final double[] relativeScale = DataSource.getRelativeScales(this, 0, targetLevel, intervalLevel);
		LOG.debug("Scaling interval {} {} {}", min, max, relativeScale);
		org.janelia.saalfeldlab.util.grids.Grids.scaleBoundingBox(min, max, min, max, relativeScale);
		return Intervals.smallestContainingInterval(new FinalRealInterval(min, max));

	}

	private TLongSet scaleBlocksToLevel(final TLongSet blocks, final int blocksLevel, final int targetLevel)
	{
		if (blocksLevel == targetLevel) { return blocks; }

		final CellGrid grid          = this.dataCanvases[blocksLevel].getCellGrid();
		final CellGrid targetGrid    = this.dataCanvases[targetLevel].getCellGrid();
		final double[] toTargetScale = DataSource.getRelativeScales(this, 0, blocksLevel, targetLevel);
		return org.janelia.saalfeldlab.util.grids.Grids.getRelevantBlocksInTargetGrid(
				blocks.toArray(),
				grid,
				targetGrid,
				toTargetScale
		);
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

	public void resetMasks() throws MaskInUse
	{
		synchronized (this)
		{
			final boolean canResetMask = !isCreatingMask && !isApplyingMask.get();
			LOG.debug("Can reset mask? {}", canResetMask);
			if (!canResetMask)
				throw new MaskInUse("Busy, cannot reset mask.");

			this.currentMask = null;
		}
		setMasksConstant();
	}

	public void forgetCanvases() throws CannotClearCanvas
	{
		synchronized (this)
		{
			if (this.isPersisting)
				throw new CannotClearCanvas("Currently persisting canvas -- try again later.");
			this.currentMask = null;
		}
		clearCanvases();
	}



	public void persistCanvas() throws CannotPersist {
		persistCanvas(true);
	}

	public void persistCanvas(final boolean clearCanvas) throws CannotPersist
	{
		synchronized (this)
		{
			final boolean canPersist = !this.isCreatingMask && this.currentMask == null && !this.isApplyingMask.get() && !this.isPersisting;
			if (!canPersist)
			{
				LOG.error(
						"Cannot persist canvas: is persisting? {} has mask? {} is creating mask? {} is applying mask? {}",
						this.isPersisting,
						this.currentMask != null,
						this.isCreatingMask,
						this.isApplyingMask
					);
				throw new CannotPersist("Can not persist canvas!");
			}
			this.isPersisting = true;
		}

		LOG.debug("Merging canvas into background for blocks {}", this.affectedBlocks);
		final CachedCellImg<UnsignedLongType, ?> canvas         = this.dataCanvases[0];
		final long[]                             affectedBlocks = this.affectedBlocks.toArray();
		this.affectedBlocks.clear();
		final BooleanProperty proxy = new SimpleBooleanProperty(this.isPersisting);
		final ObservableList<String> states = FXCollections.observableArrayList();
		final Runnable dialogHandler = () -> {
			LOG.warn("Creating commit status dialog.");
			final Alert isCommittingDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION);
			isCommittingDialog.setHeaderText("Committing canvas.");
			isCommittingDialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
			isCommittingDialog.initModality(Modality.NONE);
			states.addListener((ListChangeListener<? super String>) change -> InvokeOnJavaFXApplicationThread.invoke(() -> isCommittingDialog.getDialogPane().setContent(new VBox(asLabels(states)))));
			synchronized (this) {
				isCommittingDialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(proxy);
			}
			LOG.info("Will show dialog? {}", proxy.get());
			if(proxy.get()) isCommittingDialog.show();
		};
		// TODO do not use new thread for this but use existing executors
		new Thread(() -> {
			Exception caughtException = null;
			try
			{
				try {
					InvokeOnJavaFXApplicationThread.invokeAndWait(dialogHandler);
				}
				catch (final InterruptedException e)
				{
					throw new RuntimeException(e);
				}
				try {
					states.add("Persisting painted labels...");
					final List<TLongObjectMap<PersistCanvas.BlockDiff>> blockDiffs = this.persistCanvas.persistCanvas(canvas, affectedBlocks);
					states.set(states.size() - 1, "Persisting painted labels...   Done");
					if (this.persistCanvas.supportsLabelBlockLookupUpdate()) {
						states.add("Updating label-to-block lookup...");
						this.persistCanvas.updateLabelBlockLookup(blockDiffs);
						states.set(states.size() - 1, "Updating label-to-block lookup...   Done");
					}
					if (clearCanvas) {
						states.add("Clearing canvases...");
						clearCanvases();
						states.set(states.size() - 1, "Clearing canvases...   Done");
						this.source.invalidateAll();
					} else
						LOG.info("Not clearing canvas.");
				}
				catch (UnableToPersistCanvas | UnableToUpdateLabelBlockLookup e)
				{
					caughtException = e;
					throw new RuntimeException("Error while trying to persist.", e);
				}
				catch (final RuntimeException e)
				{
					caughtException = e;
					throw e;
				}
			}
			finally
			{
				synchronized (this)
				{
					this.isPersisting = false;
					proxy.set(false);
					if (caughtException == null)
						states.add("Successfully finished committing canvas.");
					else
						states.add("Unable to commit canvas: " + caughtException.getMessage());
				}
			}
		}).start();
	}

	@Override
	public boolean isPresent(final int t)
	{
		return source.isPresent(t);
	}

	@Override
	public RandomAccessibleInterval<T> getSource(final int t, final int level)
	{
		final RealRandomAccessible<T> interpolatedSource = getInterpolatedSource(t, level, null);
		return Views.interval(Views.raster(interpolatedSource), new FinalInterval(source.getSource(t, level)));
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method)
	{
		final RealRandomAccessible<T> sourceToExtend;

		// ignore interpolation method because we cannot use linear interpolation on LabelMultisetType
		final RealRandomAccessible<T> interpolatedSource = this.source.getInterpolatedSource(t, level, Interpolation.NEARESTNEIGHBOR);
		if (!this.showCanvasOverBackground.get() || this.affectedBlocks.size() == 0 && this.currentMask == null)
		{
			LOG.debug("Hide canvas or no mask/canvas data present -- delegate to underlying source");
			sourceToExtend = interpolatedSource;
		}
		else
		{
			final RealRandomAccessible<VolatileUnsignedLongType> canvas = interpolateNearestNeighbor(Views.extendValue(this.canvases[level].rai, new VolatileUnsignedLongType(Label.INVALID)));
			final RealRandomAccessible<VolatileUnsignedLongType> mask = this.tMasks[level];
			final RealRandomAccessibleTriple<T, VolatileUnsignedLongType, VolatileUnsignedLongType> composed = new
					RealRandomAccessibleTriple<>(
					interpolatedSource,
					canvas,
					mask
			);
			sourceToExtend = new PickOne<>(composed, pacT.copy());
		}

		// extend the interpolated source with the specified out of bounds value
		final RealInterval bounds = new FinalRealInterval(source.getSource(t, level));
		final RealRandomAccessibleRealInterval<T> boundedSource = new FinalRealRandomAccessibleRealInterval<>(sourceToExtend, bounds);
		return new ExtendedRealRandomAccessibleRealInterval<>(boundedSource, new RealOutOfBoundsConstantValueFactory<>(extensionT.copy()));
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
		final RealRandomAccessible<D> interpolatedDataSource = getInterpolatedDataSource(t, level, null);
		return Views.interval(Views.raster(interpolatedDataSource), new FinalInterval(source.getDataSource(t, level)));
	}

	@Override
	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method)
	{
		final RealRandomAccessible<D> dataSourceToExtend;

		// ignore interpolation method because we cannot use linear interpolation on LabelMultisetType
		final RealRandomAccessible<D> interpolatedDataSource = this.source.getInterpolatedDataSource(t, level, Interpolation.NEARESTNEIGHBOR);
		if (!this.showCanvasOverBackground.get() || this.affectedBlocks.size() == 0 && this.currentMask == null)
		{
			LOG.debug("Hide canvas or no mask/canvas data present -- delegate to underlying source");
			dataSourceToExtend = interpolatedDataSource;
		}
		else
		{
			final RealRandomAccessible<UnsignedLongType> dataCanvas = interpolateNearestNeighbor(Views.extendValue(this.dataCanvases[level], new UnsignedLongType(Label.INVALID)));
			final RealRandomAccessible<UnsignedLongType> dataMask = this.dMasks[level];
			final RealRandomAccessibleTriple<D, UnsignedLongType, UnsignedLongType> composed = new RealRandomAccessibleTriple<>(
					interpolatedDataSource,
					dataCanvas,
					dataMask
			);
			dataSourceToExtend = new PickOne<>(composed, pacD.copy());
		}

		// extend the interpolated source with the specified out of bounds value
		final RealInterval bounds = new FinalRealInterval(source.getDataSource(t, level));
		final RealRandomAccessibleRealInterval<D> boundedDataSource = new FinalRealRandomAccessibleRealInterval<>(dataSourceToExtend, bounds);
		return new ExtendedRealRandomAccessibleRealInterval<>(boundedDataSource, new RealOutOfBoundsConstantValueFactory<>(extensionD.copy()));
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

	/**
	 * Downsample affected blocks of img.
	 * @param source
	 * @param img
	 * @param affectedBlocks
	 * @param steps
	 * @param interval
	 */
	public static void downsampleBlocks(
			final RandomAccessible<UnsignedLongType> source,
			final CachedCellImg<UnsignedLongType, LongAccess> img,
			final TLongSet affectedBlocks,
			final int[] steps,
			final Interval interval)
	{
		final BlockSpec blockSpec = new BlockSpec(img.getCellGrid());

		final long[] intersectedCellMin = new long[blockSpec.grid.numDimensions()];
		final long[] intersectedCellMax = new long[blockSpec.grid.numDimensions()];

		LOG.debug("Initializing affected blocks: {}", affectedBlocks);
		for (final TLongIterator it = affectedBlocks.iterator(); it.hasNext(); )
		{
			final long blockId = it.next();
			blockSpec.fromLinearIndex(blockId);

			Arrays.setAll(intersectedCellMin, d -> blockSpec.min[d]);
			Arrays.setAll(intersectedCellMax, d -> blockSpec.max[d]);

			intersect(intersectedCellMin, intersectedCellMax, interval);

			if (isNonEmpty(intersectedCellMin, intersectedCellMax))
			{
				LOG.trace("Downsampling for intersected min/max: {} {}", intersectedCellMin, intersectedCellMax);
				downsample(source, Views.interval(img, intersectedCellMin, intersectedCellMax), steps);
			}
		}
	}

	/**
	 * @param source
	 * @param target
	 * @param steps
	 *
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
					level);
			final Interval                                    intervalAtHigherLevel = scaleIntervalToLevel(
					intervalAtPaintedScale,
					paintedLevel,
					levelAsFinal);

			LOG.debug("Downsampling level {} of {}", level, getNumMipmapLevels());

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
					level);
			LOG.debug("Affected blocks at level {}: {}", level, affectedBlocksAtHigherLevel);
			this.affectedBlocksByLabel[level].computeIfAbsent(label.getIntegerLong(), key -> new TLongHashSet())
					.addAll(
					affectedBlocksAtHigherLevel);

			LOG.debug("Interval at higher level: {} {}", Intervals.minAsLongArray(intervalAtHigherLevel), Intervals.maxAsLongArray(intervalAtHigherLevel));

			// downsample
			final int[] steps = DoubleStream.of(relativeScales).mapToInt(d -> (int) d).toArray();
			LOG.debug("Downsample step size: {}", steps);
			downsampleBlocks(
					Views.extendValue(atLowerLevel, new UnsignedLongType(Label.INVALID)),
					atHigherLevel,
					affectedBlocksAtHigherLevel,
					steps,
					intervalAtHigherLevel);
			LOG.debug("Downsampled level {}", level);
		}

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

			final RealRandomAccessible<UnsignedLongType> scaledMask = this.dMasks[level];

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

	@Override
	public void invalidate(Long key) {
		// TODO what to do with canvas?
		this.source.invalidate(key);
	}

	@Override
	public void invalidateIf(long parallelismThreshold, Predicate<Long> condition) {
		// TODO what to do with canvas?
		this.source.invalidateIf(parallelismThreshold, condition);
	}

	@Override
	public void invalidateIf(Predicate<Long> condition) {
		// TODO what to do with canvas?
		this.source.invalidateIf(condition);
	}

	@Override
	public void invalidateAll(long parallelismThreshold) {
		// TODO what to do with canvas?
		this.source.invalidateAll(parallelismThreshold);
	}

	@Override
	public void invalidateAll() {
		// TODO what to do with canvas?
		this.source.invalidateAll();
	}

	private static class CanvasBaseDirChangeListener implements ChangeListener<String>
	{

		private final SharedQueue queue;

		private final DiskCachedCellImg<UnsignedLongType, ?>[] dataCanvases;

		private final RaiWithInvalidate<VolatileUnsignedLongType>[] canvases;

		private final long[][] dimensions;

		private final int[][] blockSizes;

		public CanvasBaseDirChangeListener(
				final SharedQueue queue,
				final DiskCachedCellImg<UnsignedLongType, ?>[] dataCanvases,
				final RaiWithInvalidate<VolatileUnsignedLongType>[] canvases,
				final long[][] dimensions,
				final int[][] blockSizes)
		{
			super();
			this.queue = queue;
			this.dataCanvases = dataCanvases;
			this.canvases = canvases;
			this.dimensions = dimensions;
			this.blockSizes = blockSizes;
		}

		@Override
		public void changed(final ObservableValue<? extends String> observable, final String oldValue, final String newValue)
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
					final DiskCachedCellImgFactory<UnsignedLongType> f = new DiskCachedCellImgFactory<>(new UnsignedLongType(), o);
					final CellLoader<UnsignedLongType> loader = img -> img.forEach(t -> t.set(Label.INVALID));
					final DiskCachedCellImg<UnsignedLongType, ?> store  = f.create(dimensions[level], loader, o);
					final RaiWithInvalidate<VolatileUnsignedLongType> vstore = createVolatileCachedCellImgWithInvalidate((DiskCachedCellImg)store, queue, new CacheHints(LoadingStrategy.VOLATILE, canvases.length - 1 - level, true));

					if (dataCanvases[level] != null) {
						this.dataCanvases[level].shutdown();
						this.dataCanvases[level].getCache().invalidateAll();
						this.dataCanvases[level].getCache().invalidateAll();
					}
					// TODO how to invalidate volatile canvases?
					if (canvases[level] != null && canvases[level].invalidate != null)
						canvases[level].invalidate.invalidateAll();
					this.dataCanvases[level] = store;
					this.canvases[level] = vstore;
				}
			}
		}

	}

	public DataSource<D, T> underlyingSource()
	{
		return this.source;
	}

	public ReadOnlyStringProperty currentCanvasDirectoryProperty()
	{
		return this.cacheDirectory;
	}

	public String currentCanvasDirectory()
	{
		return this.cacheDirectory.get();
	}

	public PersistCanvas getPersister()
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

	private DiskCachedCellImgOptions getMaskDiskCachedCellImgOptions(final int level)
	{
		return DiskCachedCellImgOptions
				.options()
				.cacheDirectory(null)
				.deleteCacheDirectoryOnExit(true)
				.volatileAccesses(true)
				.dirtyAccesses(false)
				.cellDimensions(this.blockSizes[level]);
	}

	private DiskCachedCellImg<UnsignedLongType, ?> createMaskStore(
			final DiskCachedCellImgOptions maskOpts,
			final int level)
	{
		return new DiskCachedCellImgFactory<>(new UnsignedLongType(), maskOpts).create(source.getSource(0, level));
	}

	private Pair<DiskCachedCellImg<UnsignedLongType, ?>, RaiWithInvalidate<VolatileUnsignedLongType>> createMaskStoreWithVolatile(final int level)
	{
		final DiskCachedCellImgOptions maskOpts = getMaskDiskCachedCellImgOptions(level);
		final DiskCachedCellImg<UnsignedLongType, ?> store = createMaskStore(maskOpts, level);
		final RaiWithInvalidate<VolatileUnsignedLongType> vstore =
				createVolatileCachedCellImgWithInvalidate((CachedCellImg) store, queue, new CacheHints(LoadingStrategy.VOLATILE, 0, true));
		return new Pair<>(store, vstore);
	}

	private void setMasks(
			final RandomAccessibleInterval<UnsignedLongType> store,
			final RandomAccessibleInterval<VolatileUnsignedLongType> vstore,
			final int maskLevel,
			final UnsignedLongType value,
			final Predicate<UnsignedLongType> isPaintedForeground)
	{
		setAtMaskLevel(store, vstore, maskLevel, value, isPaintedForeground);
		LOG.debug("Created mask at scale level {}", maskLevel);
		setMaskScaleLevels(maskLevel);
	}

	private void setMasks(
			final RealRandomAccessible<UnsignedLongType> mask,
			final RealRandomAccessible<VolatileUnsignedLongType> vmask,
			final int maskLevel,
			final UnsignedLongType value,
			final Predicate<UnsignedLongType> isPaintedForeground)
	{
		setAtMaskLevel(mask, vmask, maskLevel, value, isPaintedForeground);
		LOG.debug("Created mask at scale level {}", maskLevel);
		setMaskScaleLevels(maskLevel);
	}

	private void setMaskScaleLevels(final int maskLevel)
	{
		final RealRandomAccessible<UnsignedLongType> dMask = this.dMasks[maskLevel];
		final RealRandomAccessible<VolatileUnsignedLongType> tMask = this.tMasks[maskLevel];

		// get mipmap transforms
		final AffineTransform3D[] levelToFullResTransform = new AffineTransform3D[getNumMipmapLevels()];
		final AffineTransform3D fullResTransform = new AffineTransform3D();
		getSourceTransform(0, 0, fullResTransform);
		for (int level = 0; level < getNumMipmapLevels(); ++level)
		{
			levelToFullResTransform[level] = new AffineTransform3D();
			getSourceTransform(0, level, levelToFullResTransform[level]);
			levelToFullResTransform[level].preConcatenate(fullResTransform.inverse());
		}

		for (int level = 0; level < getNumMipmapLevels(); ++level)
		{
			if (level != maskLevel)
			{
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
			final UnsignedLongType value,
			final Predicate<UnsignedLongType> isPaintedForeground)
	{
		setAtMaskLevel(
				interpolateNearestNeighbor(Views.extendZero(store)),
				interpolateNearestNeighbor(Views.extendZero(vstore)),
				maskLevel,
				value,
				isPaintedForeground);
	}

	private void setAtMaskLevel(
			final RealRandomAccessible<UnsignedLongType> mask,
			final RealRandomAccessible<VolatileUnsignedLongType> vmask,
			final int maskLevel,
			final UnsignedLongType value,
			final Predicate<UnsignedLongType> isPaintedForeground)
	{

		this.dMasks[maskLevel] = Converters.convert(
				mask,
				(input, output) -> output.set(isPaintedForeground.test(input) ? value : INVALID),
				new UnsignedLongType());

		this.tMasks[maskLevel] = Converters.convert(
				vmask,
				(input, output) -> {
					final boolean isValid = input.isValid();
					output.setValid(isValid);
					if (isValid)
					{
						output.get().set(input.get().get() > 0 ? value : INVALID);
					}
				},
				new VolatileUnsignedLongType());
	}

	private static <T> RealRandomAccessible<T> interpolateNearestNeighbor(final RandomAccessible<T> ra)
	{
		return Views.interpolate(ra, new NearestNeighborInterpolatorFactory<>());
	}

	private static javafx.scene.control.Label[] asLabels(final List<? extends String> strings)
	{
		return strings.stream().map(javafx.scene.control.Label::new).toArray(javafx.scene.control.Label[]::new);
	}

	private static <
			D extends NativeType<D>,
			T extends Volatile<D> & NativeType<T>,
			A extends VolatileArrayDataAccess<A>> RaiWithInvalidate<T>
	createVolatileCachedCellImgWithInvalidate(
			final CachedCellImg<D, A> cachedCellImg,
			final SharedQueue queue,
			final CacheHints hints) {
		final D dType = cachedCellImg.createLinkedType();
		final T tType = (T) VolatileTypeMatcher.getVolatileTypeForType(dType);
		final CellGrid grid = cachedCellImg.getCellGrid();
		final Cache<Long, Cell<A>> cache = cachedCellImg.getCache();

		final Set<AccessFlags> flags = AccessFlags.ofAccess(cachedCellImg.getAccessType());
		if (!flags.contains(VOLATILE))
			throw new IllegalArgumentException("underlying " + CachedCellImg.class.getSimpleName() + " must have volatile access type");
		final boolean dirty = flags.contains(DIRTY);

		final CreateInvalid<Long, Cell<A>> createInvalid = CreateInvalidVolatileCell.get(grid, tType, dirty);
		final VolatileCache<Long, Cell<A>> volatileCache = new WeakRefVolatileCache<>(cache, queue, createInvalid);
		final VolatileCachedCellImg<T, A> volatileImg = new VolatileCachedCellImg<>(grid, tType, hints, volatileCache.unchecked()::get);
		return new RaiWithInvalidate<>(volatileImg, volatileCache);
	}

}
