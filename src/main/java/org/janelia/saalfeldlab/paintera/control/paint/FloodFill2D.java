package org.janelia.saalfeldlab.paintera.control.paint;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Cursor;
import javafx.scene.Scene;
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
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.util.Intervals;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask;
import org.janelia.saalfeldlab.util.Imglib2ExtensionsKt;
import org.jetbrains.annotations.NotNull;
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

  private final SimpleDoubleProperty fillDepth = new SimpleDoubleProperty(1.0);

  private static final Predicate<UnsignedLongType> FOREGROUND_CHECK = it -> Label.isForeground(it.get());

  private ViewerMask viewerMask;

  private Interval viewerInterval;

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
	this.viewerInterval = null;
  }

  public Interval getViewerInterval() {

	return this.viewerInterval;
  }

  public void fillAt(final double currentViewerX, final double currentViewerY, Long fill) {

	if (fill == null) {
	  LOG.info("Received invalid label {} -- will not fill.", fill);
	  return;
	}

	if (!isVisible.getAsBoolean()) {
	  LOG.info("Selected source is not visible -- will not fill");
	  return;
	}

	final ViewerPanelFX viewer = activeViewerProperty.getValue();

	final int level = viewer.getState().getBestMipMapLevel();
	final int time = viewer.getState().getTimepoint();
	final Scene scene = viewer.getScene();
	final Cursor previousCursor = scene.getCursor();
	scene.setCursor(Cursor.WAIT);
	try {
	  final ViewerMask mask;
	  if (this.viewerMask == null) {
		final MaskInfo maskInfo = new MaskInfo(time, level, new UnsignedLongType(fill));
		mask = ViewerMask.setNewViewerMask(source, maskInfo, viewer, this.fillDepth.get());
	  } else {
		mask = viewerMask;
	  }
	  final var initialPos = mask.currentToInitialPoint(currentViewerX, currentViewerY);
	  final Interval affectedInitialViewerInterval = fillViewerMaskAt(initialPos, mask, assignment, fill);

	  final var fillIntervalInCurrentViewer = Intervals.smallestContainingInterval(
			  mask.getInitialToCurrentViewerTransform().estimateBounds(affectedInitialViewerInterval)
	  );

	  if (viewerInterval == null) {
		viewerInterval = fillIntervalInCurrentViewer;
	  } else {
		viewerInterval = Intervals.union(fillIntervalInCurrentViewer, viewerInterval);
	  }

	  final Interval affectedSourceInterval = Intervals.smallestContainingInterval(mask.getInitialSourceToViewerTransform().inverse().estimateBounds(affectedInitialViewerInterval));
	  /* If the mask was NOT provided, we apply it to the source. Otherwise,n we leave it up to the implementor to decide when to apply the mask */
	  if (this.viewerMask == null) {
		source.applyMask(source.getCurrentMask(), affectedSourceInterval, FOREGROUND_CHECK);
	  }
	  final Interval affectedGlobalInterval = Intervals.smallestContainingInterval(mask.getInitialSourceToGlobalTransform().estimateBounds(affectedSourceInterval));
	  Paintera.getPaintera().getBaseView().orthogonalViews().requestRepaint(affectedGlobalInterval);
	} finally {
	  scene.setCursor(previousCursor);
	}
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
	  final long numSlices = Math.max((long)Math.ceil(fillDepth) - 1, 0);
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

  public static Interval fillViewerMaskAt(
		  final RealPoint initialSeed,
		  final ViewerMask mask,
		  final FragmentSegmentAssignment assignment,
		  final long fillValue) {

	final var backgroundViewerRai = ViewerMask.getSourceDataInInitialViewerSpace(mask);
	final var id = (long)backgroundViewerRai.getAt(Imglib2ExtensionsKt.toPoint(initialSeed)).getRealDouble();
	final long seedLabel = assignment != null ? assignment.getSegment(id) : id;
	LOG.debug("Got seed label {}", seedLabel);

	if (seedLabel == fillValue) {
	  return new FinalInterval(0, 0, 0);
	}

	final var backgroundLabelMaskInViewer = Converters.convert(
			backgroundViewerRai,
			(src, target) -> {
			  long segmentId = (long)src.getRealDouble();
			  if (assignment != null) {
				segmentId = assignment.getSegment(segmentId);
			  }
			  target.set(segmentId == seedLabel);
			},
			new BoolType()
	);
	return fillViewerMaskAt(initialSeed, mask, backgroundLabelMaskInViewer, fillValue);
  }

  public static Interval fillViewerMaskAt(
		  final RealPoint initialSeed,
		  final ViewerMask mask,
		  final RandomAccessibleInterval<BoolType> filter,
		  final long fillValue) {

	final RandomAccessible<BoolType> extendedFilter = Views.extendValue(filter, new BoolType(false));

	final AccessBoxRandomAccessibleOnGet<UnsignedLongType> sourceAccessTracker = new
			AccessBoxRandomAccessibleOnGet<>(
			Views.extendValue(mask.getViewerRai(), new UnsignedLongType(fillValue)));
	sourceAccessTracker.initAccessBox();

	// fill only within the given slice, run 2D flood-fill
	LOG.debug("Flood filling into viewer mask ");

	final RandomAccessible<BoolType> backgroundSlice = Views.hyperSlice(extendedFilter, 2, 0);
	final RandomAccessible<UnsignedLongType> viewerFillImg = Views.hyperSlice(sourceAccessTracker, 2, 0);

	FloodFill.fill(
			backgroundSlice,
			viewerFillImg,
			Imglib2ExtensionsKt.toPoint(initialSeed),
			new UnsignedLongType(fillValue),
			new DiamondShape(1)
	);

	return new FinalInterval(sourceAccessTracker.getMin(), sourceAccessTracker.getMax());
  }
}
