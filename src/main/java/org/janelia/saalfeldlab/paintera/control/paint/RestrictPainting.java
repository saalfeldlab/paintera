package org.janelia.saalfeldlab.paintera.control.paint;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessible;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.Predicate;

public class RestrictPainting {

  // TODO restrict to fragment only or to segment?
  // currently fragment only

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int CLEANUP_THRESHOLD = (int)1e5;

  private static final class ForegroundCheck implements Predicate<UnsignedLongType> {

	@Override
	public boolean test(final UnsignedLongType t) {

	  return t.getIntegerLong() == 1;
	}

  }

  private static final ForegroundCheck FOREGROUND_CHECK = new ForegroundCheck();

  private final ViewerPanelFX viewer;

  private final SourceInfo sourceInfo;

  private final Runnable requestRepaint;

  private final LongFunction<Converter<?, BoolType>> maskForLabel;

  public RestrictPainting(
		  final ViewerPanelFX viewer,
		  final SourceInfo sourceInfo,
		  final Runnable requestRepaint,
		  final LongFunction<Converter<?, BoolType>> maskForLabel) {

	super();
	this.viewer = viewer;
	this.sourceInfo = sourceInfo;
	this.requestRepaint = requestRepaint;
	this.maskForLabel = maskForLabel;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void restrictTo(final double x, final double y) {

	final Source<?> currentSource = sourceInfo.currentSourceProperty().get();
	final ViewerState viewerState = viewer.getState();
	if (currentSource == null) {
	  LOG.info("No current source selected -- will not fill");
	  return;
	}

	final SourceState<?, ?> currentSourceState = sourceInfo.getState(currentSource);

	if (!currentSourceState.isVisibleProperty().get()) {
	  LOG.info("Selected source is not visible -- will not fill");
	  return;
	}

	if (!(currentSource instanceof MaskedSource<?, ?>)) {
	  LOG.info("Selected source is not painting-enabled -- will not fill");
	  return;
	}

	if (maskForLabel == null) {
	  LOG.info("Cannot generate boolean mask for this source -- will not fill");
	  return;
	}

	final MaskedSource<?, ?> source = (MaskedSource<?, ?>)currentSource;

	final Type<?> t = source.getDataType();

	if (!(t instanceof IntegerType<?>)) {
	  LOG.info("Data type is not integer type or LabelMultisetType -- will not fill");
	  return;
	}

	final AffineTransform3D screenScaleTransform = new AffineTransform3D();
	viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
	final int level, time;
	synchronized (viewerState) {
	  level = viewerState.getBestMipMapLevel(screenScaleTransform, sourceInfo.currentSourceIndexInVisibleSources().get());
	  time = viewerState.getTimepoint();
	}
	final AffineTransform3D labelTransform = new AffineTransform3D();
	source.getSourceTransform(time, level, labelTransform);

	final RealPoint realPointInLabelSpace = setCoordinates(x, y, viewer, labelTransform);
	final Point pointInLabelSpace = new Point(realPointInLabelSpace.numDimensions());
	for (int d = 0; d < pointInLabelSpace.numDimensions(); ++d) {
	  pointInLabelSpace.setPosition(Math.round(realPointInLabelSpace.getDoublePosition(d)), d);
	}

	try {
	  if (source.getDataType() instanceof LabelMultisetType) {
		restrictToLabelMultisetType((MaskedSource)source, time, level, pointInLabelSpace, requestRepaint);
	  } else {
		restrictTo((MaskedSource)source, time, level, pointInLabelSpace, requestRepaint);
	  }
	} catch (final MaskInUse e) {
	  LOG.info("Mask already in use -- will not paint: {}", e.getMessage());
	}

  }

  private static RealPoint setCoordinates(
		  final double x,
		  final double y,
		  final ViewerPanelFX viewer,
		  final AffineTransform3D labelTransform) {

	return setCoordinates(x, y, new RealPoint(labelTransform.numDimensions()), viewer, labelTransform);
  }

  private static <P extends RealLocalizable & RealPositionable> P setCoordinates(
		  final double x,
		  final double y,
		  final P location,
		  final ViewerPanelFX viewer,
		  final AffineTransform3D labelTransform) {

	location.setPosition(x, 0);
	location.setPosition(y, 1);
	location.setPosition(0, 2);

	viewer.displayToGlobalCoordinates(location);
	labelTransform.applyInverse(location, location);

	return location;
  }

  private static <T extends RealType<T>> void restrictTo(
		  final MaskedSource<T, ?> source,
		  final int time,
		  final int level,
		  final Localizable seed,
		  final Runnable requestRepaint) throws MaskInUse {

	final RandomAccessibleInterval<UnsignedLongType> canvas = source.getReadOnlyDataCanvas(time, level);
	final RandomAccess<UnsignedLongType> canvasAccess = canvas.randomAccess();
	canvasAccess.setPosition(seed);
	final UnsignedLongType paintedLabel = canvasAccess.get();

	if (paintedLabel.valueEquals(new UnsignedLongType(Label.INVALID))) {
	  return;
	}

	final RandomAccessibleInterval<T> background = source.getReadOnlyDataBackground(time,
			level);
	final MaskInfo maskInfo = new MaskInfo(
			time,
			level,
			new UnsignedLongType(Label.TRANSPARENT)
	);

	final SourceMask mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
	final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views
			.extendValue(mask.getRai(), new UnsignedLongType(1)));

	final RandomAccess<T> backgroundAccess = background.randomAccess();
	backgroundAccess.setPosition(seed);
	final T backgroundSeed = backgroundAccess.get();

	final RandomAccessible<Pair<T, UnsignedLongType>> paired = Views.pair(
			Views.extendBorder(background),
			Views.extendValue(canvas, new UnsignedLongType(Label.INVALID))
	);

	restrictTo(
			paired,
			accessTracker,
			seed,
			new DiamondShape(1),
			bg -> bg.valueEquals(backgroundSeed),
			cv -> cv.valueEquals(paintedLabel)
	);

	requestRepaint.run();

	source.applyMask(mask, accessTracker.createAccessInterval(), FOREGROUND_CHECK);

  }

  private static void restrictToLabelMultisetType(
		  final MaskedSource<LabelMultisetType, ?> source,
		  final int time,
		  final int level,
		  final Localizable seed,
		  final Runnable requestRepaint) throws MaskInUse {

	final RandomAccessibleInterval<UnsignedLongType> canvas = source.getReadOnlyDataCanvas(time, level);

	final RandomAccess<UnsignedLongType> canvasAccess = canvas.randomAccess();
	canvasAccess.setPosition(seed);
	final UnsignedLongType paintedLabel = canvasAccess.get();

	if (paintedLabel.valueEquals(new UnsignedLongType(Label.INVALID))) {
	  /* Return if there is nothing to flood fill into */
	  return;
	}

	final RandomAccessibleInterval<LabelMultisetType> background = source.getReadOnlyDataBackground(time, level);
	final MaskInfo maskInfo = new MaskInfo(time, level, new UnsignedLongType(Label.TRANSPARENT));
	final SourceMask mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
	final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views
			.extendValue(mask.getRai(), new UnsignedLongType(1)));

	final RandomAccess<LabelMultisetType> backgroundAccess = background.randomAccess();
	backgroundAccess.setPosition(seed);
	final LabelMultisetType backgroundSeed = backgroundAccess.get();
	final long backgroundSeedLabel = getBackgroundSeedLabel(backgroundSeed);

	if (paintedLabel.valueEquals(new UnsignedLongType(backgroundSeedLabel))) {
	  /* discard the mask */
	  source.resetMasks();
	  /* return if the label we are flood filling into is the same label we are restricting against. */
	  return;
	}

	final RandomAccessible<Pair<LabelMultisetType, UnsignedLongType>> paired = Views.pair(
			Views.extendValue(background, new LabelMultisetType()),
			Views.extendValue(canvas, new UnsignedLongType(Label.INVALID))
	);

	restrictTo(
			paired,
			accessTracker,
			seed,
			new DiamondShape(1),
			bg -> bg.contains(backgroundSeedLabel, new LabelMultisetEntry()),
			cv -> cv.valueEquals(paintedLabel)
	);

	requestRepaint.run();

	source.applyMask(mask, accessTracker.createAccessInterval(), FOREGROUND_CHECK);

  }

  private static Long getBackgroundSeedLabel(LabelMultisetType backgroundSeed) {

	boolean seen = false;
	LabelMultisetType.Entry<Label> best = null;
	Comparator<LabelMultisetType.Entry<Label>> comparator = Comparator.comparingLong(LabelMultisetType.Entry::getCount);

	for (LabelMultisetType.Entry<Label> labelEntry : backgroundSeed.entrySet()) {
	  if (!seen || comparator.compare(labelEntry, best) > 0) {
		seen = true;
		best = labelEntry;
	  }
	}

	return (seen ? Optional.of(best) : Optional.<LabelMultisetType.Entry<Label>>empty())
			.map(e -> e.getElement().id())
			.orElse(Label.INVALID);
  }

  private static <T, U> void restrictTo(
		  final RandomAccessible<Pair<T, U>> backgroundCanvasPair,
		  final RandomAccessible<UnsignedLongType> mask,
		  final Localizable seed,
		  final Shape shape,
		  final Predicate<T> backgroundFilter,
		  final Predicate<U> canvasFilter) {

	final int n = backgroundCanvasPair.numDimensions();

	final RandomAccessible<Pair<Pair<T, U>, UnsignedLongType>> backgroundCanvasMaskPairs = Views.pair(backgroundCanvasPair, mask);

	final TLongList[] coordinates = new TLongList[n];
	for (int d = 0; d < n; ++d) {
	  coordinates[d] = new TLongArrayList();
	  coordinates[d].add(seed.getLongPosition(d));
	}

	final var neighborhood = shape.neighborhoodsRandomAccessible(backgroundCanvasMaskPairs);
	final var neighborhoodAccess = neighborhood.randomAccess();

	final RandomAccess<UnsignedLongType> targetAccess = mask.randomAccess();
	targetAccess.setPosition(seed);
	targetAccess.get().set(2);

	final UnsignedLongType unvisited = new UnsignedLongType(0);
	final UnsignedLongType unrestricted = new UnsignedLongType(1);
	final UnsignedLongType restricted = new UnsignedLongType(2);

	for (int i = 0; i < coordinates[0].size(); ++i) {
	  for (int d = 0; d < n; ++d) {
		neighborhoodAccess.setPosition(coordinates[d].get(i), d);
	  }

	  final Cursor<Pair<Pair<T, U>, UnsignedLongType>> neighborhoodCursor = neighborhoodAccess.get().cursor();

	  while (neighborhoodCursor.hasNext()) {
		final Pair<Pair<T, U>, UnsignedLongType> p = neighborhoodCursor.next();
		final UnsignedLongType maskVal = p.getB();
		final Pair<T, U> backgroundAndCanvas = p.getA();
		final U canvasVal = backgroundAndCanvas.getB();
		if (maskVal.valueEquals(unvisited) && canvasFilter.test(canvasVal)) {
		  // If background is same as at seed, mark mask with two (restricted), else with one (unrestricted).
		  final T backgroundVal = backgroundAndCanvas.getA();
		  maskVal.set(backgroundFilter.test(backgroundVal) ? restricted : unrestricted);
		  for (int d = 0; d < n; ++d) {
			coordinates[d].add(neighborhoodCursor.getLongPosition(d));
		  }
		}

	  }

	  if (i > CLEANUP_THRESHOLD) {
		for (int d = 0; d < coordinates.length; ++d) {
		  final TLongList c = coordinates[d];
		  coordinates[d] = c.subList(i, c.size());
		}
		i = 0;
	  }

	}
  }

}
