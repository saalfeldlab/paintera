package org.janelia.saalfeldlab.paintera.control.paint;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import net.imglib2.*;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.util.Intervals;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.UtilityTask;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class FloodFill2D<T extends IntegerType<T>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ObservableValue<ViewerPanelFX> activeViewerProperty;

	private final MaskedSource<T, ?> source;

	private final FragmentSegmentAssignment assignment;

	private final BooleanSupplier isVisible;

	private final SimpleDoubleProperty fillDepth = new SimpleDoubleProperty(2.0);

	private static final Predicate<UnsignedLongType> FOREGROUND_CHECK = it -> Label.isForeground(it.get());

	private ViewerMask viewerMask;

	private final ReadOnlyObjectWrapper<Interval> maskIntervalProperty = new ReadOnlyObjectWrapper<>(null);

	public FloodFill2D(
			final ObservableValue<ViewerPanelFX> activeViewerProperty,
			final MaskedSource<T, ?> source,
			final FragmentSegmentAssignment assignment,
			final BooleanSupplier isVisible) {

		super();
		Objects.requireNonNull(activeViewerProperty);
		Objects.requireNonNull(source);
		Objects.requireNonNull(assignment);
		Objects.requireNonNull(isVisible);

		this.activeViewerProperty = activeViewerProperty;
		this.source = source;
		this.assignment = assignment;
		this.isVisible = isVisible;
	}

	public void provideMask(@NotNull ViewerMask mask) {

		this.viewerMask = mask;
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

	public UtilityTask<Interval> fillAt(final double currentViewerX, final double currentViewerY, Long fill) {

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
			final MaskInfo maskInfo = new MaskInfo(time, level, new UnsignedLongType(fill));
			mask = ViewerMask.setNewViewerMask(source, maskInfo, viewer, this.fillDepth.get());
		} else {
			mask = viewerMask;
		}
		final var maskPos = mask.displayPointToInitialMaskPoint(currentViewerX, currentViewerY);
		final var floodFillTask = createViewerFloodFillTask(
				maskPos,
				mask,
				assignment,
				fill,
				this.viewerMask == null
		);

		InvokeOnJavaFXApplicationThread.invoke(() -> {
			floodFillTask.valueProperty().addListener((obs, oldv, fillIntervalInMask) -> {
				final Interval curMaskInterval = maskIntervalProperty.get();
				if (fillIntervalInMask != null) {
					if (curMaskInterval == null) {
						maskIntervalProperty.set(fillIntervalInMask);
					} else {
						maskIntervalProperty.set(Intervals.union(fillIntervalInMask, curMaskInterval));
					}
				}

			});
		});

		floodFillTask.submit();


		refreshDuringFloodFill(floodFillTask).start();
		return floodFillTask;
	}

	public static UtilityTask<Interval> createViewerFloodFillTask(
			Point maskPos,
			ViewerMask mask,
			FragmentSegmentAssignment assignment,
			long fill,
			Boolean apply) {
		return Tasks.<Interval>createTask(task -> {

			final var backgroundLabelMaskInViewer = getBackgorundLabelMaskForAssignment(maskPos, mask, assignment, fill);
			if (backgroundLabelMaskInViewer == null) {
				if (apply) {
					try {
						mask.getSource().resetMasks(true);
					} catch (MaskInUse e) {
						Exceptions.alert(e, Paintera.getPaintera().getBaseView().getNode().getScene().getWindow());
					}
				}
				return new FinalInterval(0, 0, 0);
			} else {
				return viewerMaskFloodFill(maskPos, mask, backgroundLabelMaskInViewer, fill, apply);
			}
		}).onCancelled((state, task) -> {
			try {
				mask.getSource().resetMasks();
			} catch (final MaskInUse e) {
				e.printStackTrace();
			}
		});
	}

	public static Interval viewerMaskFloodFill(
			final Point maskPos,
			final ViewerMask mask,
			final RandomAccessibleInterval<BoolType> filter,
			final long fill,
			final Boolean apply) {
		final var fillIntervalInMask = fillViewerMaskAt(maskPos, mask, filter, fill);

		final Interval affectedSourceInterval = Intervals.smallestContainingInterval(mask.getCurrentMaskToSourceWithDepthTransform().estimateBounds(fillIntervalInMask));

		if (apply) {
			final MaskedSource<? extends RealType<?>, ?> source = mask.getSource();
			source.applyMask(source.getCurrentMask(), affectedSourceInterval, FOREGROUND_CHECK);
		}
		Paintera.getPaintera().getBaseView().orthogonalViews().requestRepaint();
		return fillIntervalInMask;
	}

	public static Thread refreshDuringFloodFill() {
		return refreshDuringFloodFill(null);
	}

	public static Thread refreshDuringFloodFill(Task<?> task) {
		return new Thread(() -> {
			while (task == null || !task.isDone()) {
				try {
					Thread.sleep(100);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt(); // restore interrupted status
				}

				if (Thread.currentThread().isInterrupted())
					break;

				LOG.trace("Updating View for FloodFill2D");
				Paintera.getPaintera().getBaseView().orthogonalViews().requestRepaint();
			}

			if (Thread.interrupted() && task != null) {
				task.cancel();
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
	public static RandomAccessibleInterval<BoolType> getBackgorundLabelMaskForAssignment(Point initialSeed, ViewerMask mask, FragmentSegmentAssignment assignment, long fillValue) {
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

	public static Interval fillViewerMaskAt(
			final Point initialSeed,
			final ViewerMask mask,
			final RandomAccessibleInterval<BoolType> filter,
			final long fillValue) {

		final RandomAccessible<BoolType> extendedFilter = Views.extendValue(filter, new BoolType(false));

		final AccessBoxRandomAccessibleOnGet<UnsignedLongType> sourceAccessTracker = new
				AccessBoxRandomAccessibleOnGet<>(
				Views.extendValue(mask.getViewerImg(), new UnsignedLongType(fillValue)));
		sourceAccessTracker.initAccessBox();

		// fill only within the given slice, run 2D flood-fill
		LOG.debug("Flood filling into viewer mask ");

		final RandomAccessible<BoolType> backgroundSlice = Views.hyperSlice(extendedFilter, 2, 0);
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
