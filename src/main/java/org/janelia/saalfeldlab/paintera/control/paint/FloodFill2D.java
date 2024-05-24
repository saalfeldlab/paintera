package org.janelia.saalfeldlab.paintera.control.paint;

import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import javafx.animation.AnimationTimer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import org.janelia.saalfeldlab.net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.util.Intervals;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.UtilityTask;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class FloodFill2D<T extends IntegerType<T>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final Predicate<Long> FOREGROUND_CHECK = Label::isForeground;

	private static ExecutorService floodFillExector = newFloodFillExecutor();

	private static ExecutorService newFloodFillExecutor() {
		return Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors() - 1, 1), new NamedThreadFactory("flood-fill-2d", true, 8));
	}

	private final ObservableValue<ViewerPanelFX> activeViewerProperty;

	private final MaskedSource<T, ?> source;
	private final BooleanSupplier isVisible;

	private final SimpleDoubleProperty fillDepth = new SimpleDoubleProperty(2.0);


	private ViewerMask viewerMask;

	private final ReadOnlyObjectWrapper<Interval> maskIntervalProperty = new ReadOnlyObjectWrapper<>(null);

	public FloodFill2D(
			final ObservableValue<ViewerPanelFX> activeViewerProperty,
			final MaskedSource<T, ?> source,
			final BooleanSupplier isVisible) {

		super();
		Objects.requireNonNull(activeViewerProperty);
		Objects.requireNonNull(source);
		Objects.requireNonNull(isVisible);

		this.activeViewerProperty = activeViewerProperty;
		this.source = source;
		this.isVisible = isVisible;
	}

	public void provideMask(@NotNull ViewerMask mask) {

		this.viewerMask = mask;
	}

	public ViewerMask getMask() {
		return viewerMask;
	}

	public void release() {

		this.viewerMask = null;
		this.maskIntervalProperty.set(null);
	}

	public ReadOnlyObjectProperty<Interval> getMaskIntervalProperty() {

		return this.maskIntervalProperty.getReadOnlyProperty();
	}

	public Interval getMaskInterval() {

		return this.maskIntervalProperty.get();
	}

	public @Nullable UtilityTask<?> fillViewerAt(final double viewerSeedX, final double viewerSeedY, Long fill, FragmentSegmentAssignment assignment) {

		if (fill == null) {
			LOG.info("Received invalid label {} -- will not fill.", fill);
			return null;
		}

		if (!isVisible.getAsBoolean()) {
			LOG.info("Selected source is not visible -- will not fill");
			return null;
		}

		final ViewerPanelFX viewer = activeViewerProperty.getValue();

		final ViewerMask mask;
		if (this.viewerMask == null) {
			final int level = viewer.getState().getBestMipMapLevel();
			final int time = viewer.getState().getTimepoint();
			final MaskInfo maskInfo = new MaskInfo(time, level);
			mask = ViewerMask.createViewerMask(source, maskInfo, viewer, this.fillDepth.get());
		} else {
			mask = viewerMask;
		}
		final var maskPos = mask.displayPointToMask(viewerSeedX, viewerSeedY, true);
		final var filter = getBackgorundLabelMaskForAssignment(maskPos, mask, assignment, fill);
		if (filter == null)
			return null;
		final UtilityTask<?> floodFillTask = fillMaskAt(maskPos, mask, fill, filter);
		if (this.viewerMask == null) {
			floodFillTask.onCancelled(true, (state, task) -> {
				try {
					mask.getSource().resetMasks();
					mask.requestRepaint();
				} catch (final MaskInUse e) {
					e.printStackTrace();
				}
			});
		}
		floodFillTask.submit(floodFillExector);
		return floodFillTask;
	}

	public UtilityTask<?> fillViewerAt(final double viewerSeedX, final double viewerSeedY, Long fill, RandomAccessibleInterval<BoolType> filter) {

		if (fill == null) {
			LOG.info("Received invalid label {} -- will not fill.", fill);
			return null;
		}

		if (!isVisible.getAsBoolean()) {
			LOG.info("Selected source is not visible -- will not fill");
			return null;
		}

		final ViewerPanelFX viewer = activeViewerProperty.getValue();

		final ViewerMask mask;
		if (this.viewerMask == null) {
			final int level = viewer.getState().getBestMipMapLevel();
			final int time = viewer.getState().getTimepoint();
			final MaskInfo maskInfo = new MaskInfo(time, level);
			mask = ViewerMask.createViewerMask(source, maskInfo, viewer, this.fillDepth.get());
		} else {
			mask = viewerMask;
		}
		final var maskPos = mask.displayPointToMask(viewerSeedX, viewerSeedY, true);
		final UtilityTask<?> floodFillTask = fillMaskAt(maskPos, mask, fill, filter);
		if (this.viewerMask == null) {
			floodFillTask.onCancelled(true, (state, task) -> {
				try {
					mask.getSource().resetMasks();
					mask.requestRepaint();
				} catch (final MaskInUse e) {
					e.printStackTrace();
				}
			});
		}
		floodFillTask.submit(floodFillExector);
		return floodFillTask;
	}


	@NotNull
	private UtilityTask<?> fillMaskAt(Point maskPos, ViewerMask mask, Long fill, RandomAccessibleInterval<BoolType> filter) {

		final Interval screenInterval = mask.getScreenInterval();

		final AtomicBoolean triggerRefresh = new AtomicBoolean(false);
		final RandomAccessibleInterval<UnsignedLongType> writableViewerImg = mask.getViewerImg().getWritableSource();
		final var sourceAccessTracker = new AccessBoxRandomAccessibleOnGet<>(Views.extendValue(writableViewerImg, new UnsignedLongType(fill))) {
			final Point position = new Point(sourceAccess.numDimensions());

			@Override
			public UnsignedLongType get() {
				if (Thread.currentThread().isInterrupted())
					throw new RuntimeException("Flood Fill Interrupted");
				synchronized (this) {
					updateAccessBox();
				}
				sourceAccess.localize(position);
				if (!triggerRefresh.get() && Intervals.contains(screenInterval, position)) {
					triggerRefresh.set(true);
				}
				return sourceAccess.get();
			}
		};
		sourceAccessTracker.initAccessBox();


		final var floodFillTask = createViewerFloodFillTask(
				maskPos,
				mask,
				filter,
				sourceAccessTracker,
				fill
		);

		final var refreshAnimation = new AnimationTimer() {

			final static long delay = 2_000_000_000; // 2 second delay before refreshes start
			final static long REFRESH_RATE = 1_000_000_000;
			final long start = System.nanoTime();
			long before = start;

			@Override
			public void handle(long now) {
				if (now - start < delay || now - before < REFRESH_RATE) return;
				if (!floodFillTask.isCancelled()) {
					if (triggerRefresh.get()) {
						mask.requestRepaint(sourceAccessTracker.createAccessInterval());
						before = now;
						triggerRefresh.set(false);
					}
				}
			}
		};


		if (floodFillExector.isShutdown()) {
			floodFillExector = newFloodFillExecutor();
		}

		floodFillTask.onEnd((task) -> {
			refreshAnimation.stop();
			/*manually trigger repaint after stop to ensure full interval has been repainted*/
			mask.requestRepaint(sourceAccessTracker.createAccessInterval());
		});
		floodFillTask.onSuccess((state, task) -> {
			maskIntervalProperty.set(sourceAccessTracker.createAccessInterval());
		});

		refreshAnimation.start();
		return floodFillTask;
	}

	public static UtilityTask<?> createViewerFloodFillTask(
			Point maskPos,
			ViewerMask mask,
			RandomAccessibleInterval<BoolType> floodFillFilter,
			RandomAccessible<UnsignedLongType> target,
			long fill) {

		return Tasks.createTask(task -> {

			if (floodFillFilter == null) {
				try {
					mask.getSource().resetMasks(true);
				} catch (MaskInUse e) {
					Exceptions.alert(e, Paintera.getPaintera().getBaseView().getNode().getScene().getWindow());
				}
			} else {
				fillAt(maskPos, target, floodFillFilter, fill);
			}
		});
	}

	/**
	 * Flood-fills the given mask starting at the specified 2D location in the viewer.
	 * Returns the affected interval in source coordinates.
	 *
	 * @param x
	 * @param y
	 * @param viewer
	 * @param mask
	 * @param source
	 * @param assignment
	 * @param fillValue
	 * @param fillDepth
	 * @return affected interval
	 */
	public static <T extends IntegerType<T>> Interval fillMaskAt(
			final double x,
			final double y,
			final ViewerPanelFX viewer,
			final SourceMask mask,
			final MaskedSource<T, ?> source,
			final FragmentSegmentAssignment assignment,
			final long fillValue,
			final double fillDepth) {

		final AffineTransform3D labelTransform = source.getSourceTransformForMask(mask.getInfo());
		final RandomAccessibleInterval<T> background = source.getDataSourceForMask(mask.getInfo());

		final RandomAccess<T> access = background.randomAccess();
		final RealPoint posInLabelSpace = new RealPoint(access.numDimensions());
		viewer.displayToSourceCoordinates(x, y, labelTransform, posInLabelSpace);
		for (int d = 0; d < access.numDimensions(); ++d) {
			access.setPosition(Math.round(posInLabelSpace.getDoublePosition(d)), d);
		}
		final long seedLabel = assignment != null ? assignment.getSegment(access.get().getIntegerLong()) : access.get().getIntegerLong();
		LOG.debug("Got seed label {}", seedLabel);
		final RandomAccessibleInterval<BoolType> relevantBackground = Converters.convert(
				background,
				(src, tgt) -> tgt.set((assignment != null ? assignment.getSegment(src.getIntegerLong()) : src.getIntegerLong()) == seedLabel),
				new BoolType()
		);

		return fillMaskAt(x, y, viewer, mask, relevantBackground, labelTransform, fillValue, fillDepth);
	}

	/**
	 * Flood-fills the given mask starting at the specified 2D location in the viewer
	 * based on the given boolean filter.
	 * Returns the affected interval in source coordinates.
	 *
	 * @param x
	 * @param y
	 * @param viewer
	 * @param mask
	 * @param filter
	 * @param labelTransform
	 * @param fillValue
	 * @param fillDepth
	 * @return affected interval
	 */
	public static <T extends IntegerType<T>> Interval fillMaskAt(
			final double x,
			final double y,
			final ViewerPanelFX viewer,
			final SourceMask mask,
			final RandomAccessibleInterval<BoolType> filter,
			final AffineTransform3D labelTransform,
			final long fillValue,
			final double fillDepth) {

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewer.getState().getViewerTransform(viewerTransform);
		final AffineTransform3D labelToViewerTransform = viewerTransform.copy().concatenate(labelTransform);

		final RealPoint pos = new RealPoint(labelTransform.numDimensions());
		viewer.displayToSourceCoordinates(x, y, labelTransform, pos);

		final RandomAccessible<BoolType> extendedFilter = Views.extendValue(filter, new BoolType(false));

		final int fillNormalAxisInLabelCoordinateSystem = PaintUtils.labelAxisCorrespondingToViewerAxis(labelTransform, viewerTransform, 2);
		final AccessBoxRandomAccessibleOnGet<UnsignedLongType> accessTracker = new
				AccessBoxRandomAccessibleOnGet<>(
				Views.extendValue(mask.getRai(), new UnsignedLongType(fillValue)));
		accessTracker.initAccessBox();

		if (fillNormalAxisInLabelCoordinateSystem < 0) {
			FloodFillTransformedPlane.fill(
					labelToViewerTransform,
					(fillDepth - 0.5) * PaintUtils.maximumVoxelDiagonalLengthPerDimension(
							labelTransform,
							viewerTransform
					)[2],
					extendedFilter.randomAccess(),
					accessTracker.randomAccess(),
					new RealPoint(x, y, 0),
					fillValue
			);
		} else {
			LOG.debug(
					"Flood filling axis aligned. Corressponding viewer axis={}",
					fillNormalAxisInLabelCoordinateSystem
			);
			final long slicePos = Math.round(pos.getDoublePosition(fillNormalAxisInLabelCoordinateSystem));
			final long numSlices = Math.max((long) Math.ceil(fillDepth) - 1, 0);
			if (numSlices == 0) {
				// fill only within the given slice, run 2D flood-fill
				final long[] seed2D = {
						Math.round(pos.getDoublePosition(fillNormalAxisInLabelCoordinateSystem == 0 ? 1 : 0)),
						Math.round(pos.getDoublePosition(fillNormalAxisInLabelCoordinateSystem != 2 ? 2 : 1))
				};
				final MixedTransformView<BoolType> relevantBackgroundSlice = Views.hyperSlice(
						extendedFilter,
						fillNormalAxisInLabelCoordinateSystem,
						slicePos
				);
				final MixedTransformView<UnsignedLongType> relevantAccessTracker = Views.hyperSlice(
						accessTracker,
						fillNormalAxisInLabelCoordinateSystem,
						slicePos
				);
				FloodFill.fill(
						relevantBackgroundSlice,
						relevantAccessTracker,
						new Point(seed2D),
						new UnsignedLongType(fillValue),
						new DiamondShape(1)
				);
			} else {
				// fill a range around the given slice, run 3D flood-fill restricted by this range
				final long[] seed3D = new long[3];
				Arrays.setAll(seed3D, d -> Math.round(pos.getDoublePosition(d)));

				final long[] rangeMin = Intervals.minAsLongArray(filter);
				final long[] rangeMax = Intervals.maxAsLongArray(filter);
				rangeMin[fillNormalAxisInLabelCoordinateSystem] = slicePos - numSlices;
				rangeMax[fillNormalAxisInLabelCoordinateSystem] = slicePos + numSlices;
				final Interval range = new FinalInterval(rangeMin, rangeMax);

				final RandomAccessible<BoolType> extendedBackgroundRange = Views.extendValue(
						Views.interval(extendedFilter, range),
						new BoolType(false)
				);
				FloodFill.fill(
						extendedBackgroundRange,
						accessTracker,
						new Point(seed3D),
						new UnsignedLongType(fillValue),
						new DiamondShape(1)
				);
			}
		}

		return new FinalInterval(accessTracker.getMin(), accessTracker.getMax());
	}

	public DoubleProperty fillDepthProperty() {

		return this.fillDepth;
	}

	@Nullable
	public static RandomAccessibleInterval<BoolType> getBackgorundLabelMaskForAssignment(Point initialSeed, ViewerMask mask,
	                                                                                     FragmentSegmentAssignment assignment, long fillValue) {

		final var backgroundViewerRai = ViewerMask.getSourceDataInInitialMaskSpace(mask);

		final var id = (long) backgroundViewerRai.getAt(initialSeed).getRealDouble();
		final long seedLabel = assignment != null ? assignment.getSegment(id) : id;
		LOG.debug("Got seed label {}", seedLabel);

		if (seedLabel == fillValue) {
			return null;
		}

		return Converters.convert(
				backgroundViewerRai,
				(src, target) -> {
					long segmentId = (long) src.getRealDouble();
					if (assignment != null) {
						segmentId = assignment.getSegment(segmentId);
					}
					target.set(segmentId == seedLabel);
				},
				new BoolType()
		);
	}

	public static void fillViewerMaskAt(
			final Point initialSeed,
			final RandomAccessible<UnsignedLongType> source,
			final RandomAccessibleInterval<BoolType> filter,
			final long fillValue) {

		final RandomAccessible<BoolType> extendedFilter = Views.extendValue(filter, new BoolType(false));


		// fill only within the given slice, run 2D flood-fill
		LOG.debug("Flood filling into viewer source ");

		final RandomAccessible<BoolType> backgroundSlice;
		if (extendedFilter.numDimensions() == 3) {
			backgroundSlice = Views.hyperSlice(extendedFilter, 2, 0);
		} else {
			backgroundSlice = extendedFilter;
		}
		final RandomAccessible<UnsignedLongType> viewerFillImg = Views.hyperSlice(source, 2, 0);

		FloodFill.fill(
				backgroundSlice,
				viewerFillImg,
				initialSeed,
				new UnsignedLongType(fillValue),
				new DiamondShape(1)
		);
	}

	public static void fillAt(
			final Point initialSeed,
			final RandomAccessible<UnsignedLongType> target,
			final RandomAccessibleInterval<BoolType> filter,
			final long fillValue) {

		final RandomAccessible<BoolType> extendedFilter = Views.extendValue(filter, new BoolType(false));

		// fill only within the given slice, run 2D flood-fill
		LOG.debug("Flood filling ");

		final RandomAccessible<BoolType> backgroundSlice;
		if (extendedFilter.numDimensions() == 3) {
			backgroundSlice = Views.hyperSlice(extendedFilter, 2, 0);
		} else {
			backgroundSlice = extendedFilter;
		}
		final RandomAccessible<UnsignedLongType> viewerFillImg = Views.hyperSlice(target, 2, 0);

		FloodFill.fill(
				backgroundSlice,
				viewerFillImg,
				initialSeed,
				new UnsignedLongType(fillValue),
				new DiamondShape(1)
		);
	}

	public static Interval fillViewerMaskAt(
			final Point initialSeed,
			RandomAccessibleInterval<UnsignedLongType> viewerImg, final RandomAccessibleInterval<BoolType> filter,
			final long fillValue) {

		final RandomAccessible<BoolType> extendedFilter = Views.extendValue(filter, new BoolType(false));

		final AccessBoxRandomAccessibleOnGet<UnsignedLongType> sourceAccessTracker = new
				AccessBoxRandomAccessibleOnGet<>(
				Views.extendValue(viewerImg, new UnsignedLongType(fillValue)));
		sourceAccessTracker.initAccessBox();

		// fill only within the given slice, run 2D flood-fill
		LOG.debug("Flood filling into viewer mask ");

		final RandomAccessible<BoolType> backgroundSlice;
		if (extendedFilter.numDimensions() == 3) {
			backgroundSlice = Views.hyperSlice(extendedFilter, 2, 0);
		} else {
			backgroundSlice = extendedFilter;
		}
		final RandomAccessible<UnsignedLongType> viewerFillImg = Views.hyperSlice(sourceAccessTracker, 2, 0);

		FloodFill.fill(
				backgroundSlice,
				viewerFillImg,
				initialSeed,
				new UnsignedLongType(fillValue),
				new DiamondShape(1)
		);

		return new FinalInterval(sourceAccessTracker.getMin(), sourceAccessTracker.getMax());
	}
}
