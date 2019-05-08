package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.function.LongFunction;
import java.util.function.Predicate;

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
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessible;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.state.HasMaskForLabel;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestrictPainting
{

	// TODO restrict to fragment only or to segment?
	// currently fragment only

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int CLEANUP_THRESHOLD = (int) 1e5;

	private static final class ForegroundCheck implements Predicate<UnsignedLongType>
	{

		@Override
		public boolean test(final UnsignedLongType t)
		{
			return t.getIntegerLong() == 1;
		}

	}

	private static final ForegroundCheck FOREGROUND_CHECK = new ForegroundCheck();

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Runnable requestRepaint;

	public RestrictPainting(final ViewerPanelFX viewer, final SourceInfo sourceInfo, final Runnable requestRepaint)
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.requestRepaint = requestRepaint;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void restrictTo(final double x, final double y)
	{
		final Source<?>   currentSource = sourceInfo.currentSourceProperty().get();
		final ViewerState viewerState   = viewer.getState();
		if (currentSource == null)
		{
			LOG.info("No current source selected -- will not fill");
			return;
		}

		final SourceState<?, ?> currentSourceState = sourceInfo.getState(currentSource);

		if (!(currentSourceState instanceof HasMaskForLabel<?>))
		{
			LOG.info("Selected source cannot provide mask for label -- will not fill");
			return;
		}

		final HasMaskForLabel<?> hasMaskForLabel = (HasMaskForLabel<?>) currentSourceState;

		if (!currentSourceState.isVisibleProperty().get())
		{
			LOG.info("Selected source is not visible -- will not fill");
			return;
		}

		if (!(currentSource instanceof MaskedSource<?, ?>))
		{
			LOG.info("Selected source is not painting-enabled -- will not fill");
			return;
		}

		LongFunction<? extends Converter<?, BoolType>> maskGenerator = hasMaskForLabel.maskForLabel();
		if (maskGenerator == null)
		{
			LOG.info("Cannot generate boolean mask for this source -- will not fill");
			return;
		}

		final MaskedSource<?, ?> source = (MaskedSource<?, ?>) currentSource;

		final Type<?> t = source.getDataType();

		if (!(t instanceof LabelMultisetType) && !(t instanceof IntegerType<?>))
		{
			LOG.info("Data type is not integer type or LabelMultisetType -- will not fill");
			return;
		}

		final AffineTransform3D screenScaleTransform = new AffineTransform3D();
		viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
		final int level, time;
		synchronized (viewerState)
		{
			level = viewerState.getBestMipMapLevel(screenScaleTransform, sourceInfo.currentSourceIndexInVisibleSources().get());
			time = viewerState.timepointProperty().get();
		}
		final AffineTransform3D labelTransform = new AffineTransform3D();
		source.getSourceTransform(time, level, labelTransform);

		final RealPoint rp = setCoordinates(x, y, viewer, labelTransform);
		final Point     p  = new Point(rp.numDimensions());
		for (int d = 0; d < p.numDimensions(); ++d)
		{
			p.setPosition(Math.round(rp.getDoublePosition(d)), d);
		}

		try
		{
			if (source.getDataType() instanceof LabelMultisetType)
			{
				restrictToLabelMultisetType((MaskedSource) source, time, level, p, requestRepaint);
			}
			else
			{
				restrictTo((MaskedSource) source, time, level, p, requestRepaint);
			}
		} catch (final MaskInUse e)
		{
			LOG.info("Mask already in use -- will not paint: {}", e.getMessage());
		}

	}

	private static RealPoint setCoordinates(
			final double x,
			final double y,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform)
	{
		return setCoordinates(x, y, new RealPoint(labelTransform.numDimensions()), viewer, labelTransform);
	}

	private static <P extends RealLocalizable & RealPositionable> P setCoordinates(
			final double x,
			final double y,
			final P location,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform)
	{
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
			final Runnable requestRepaint) throws MaskInUse
	{
		final RandomAccessibleInterval<UnsignedLongType>  canvas        = source.getReadOnlyDataCanvas(time, level);
		final RandomAccessibleInterval<T>                 background    = source.getReadOnlyDataBackground(time,
				level);
		final MaskInfo<UnsignedLongType>                  maskInfo      = new MaskInfo<>(
				time,
				level,
				new UnsignedLongType(Label.TRANSPARENT)
		);
		final Mask<UnsignedLongType> mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
		final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views
				.extendValue(mask.mask, new UnsignedLongType(1)));

		final RandomAccess<UnsignedLongType> canvasAccess = canvas.randomAccess();
		canvasAccess.setPosition(seed);
		final UnsignedLongType paintedLabel     = canvasAccess.get();
		final RandomAccess<T>  backgroundAccess = background.randomAccess();
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
			final Runnable requestRepaint) throws MaskInUse
	{
		final RandomAccessibleInterval<UnsignedLongType>  canvas        = source.getReadOnlyDataCanvas(time, level);
		final RandomAccessibleInterval<LabelMultisetType> background    = source.getReadOnlyDataBackground(time,
				level);
		final MaskInfo<UnsignedLongType>                  maskInfo      = new MaskInfo<>(
				time,
				level,
				new UnsignedLongType(Label.TRANSPARENT)
		);
		final Mask<UnsignedLongType> mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
		final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views
				.extendValue(mask.mask, new UnsignedLongType(1)));

		final RandomAccess<UnsignedLongType> canvasAccess = canvas.randomAccess();
		canvasAccess.setPosition(seed);
		final UnsignedLongType                paintedLabel     = canvasAccess.get();
		final RandomAccess<LabelMultisetType> backgroundAccess = background.randomAccess();
		backgroundAccess.setPosition(seed);
		final LabelMultisetType backgroundSeed      = backgroundAccess.get();
		final long              backgroundSeedLabel = backgroundSeed.entrySet().stream().max((e1, e2) -> Long.compare(
				e1.getCount(),
				e2.getCount()
		                                                                                                             )
		                                                                                    ).map(
				e -> e.getElement().id()).orElse(Label.INVALID);

		final RandomAccessible<Pair<LabelMultisetType, UnsignedLongType>> paired = Views.pair(
				Views.extendValue(
						background,
						new LabelMultisetType()
				                 ),
				Views.extendValue(canvas, new UnsignedLongType(Label.INVALID))
		                                                                                     );

		restrictTo(
				paired,
				accessTracker,
				seed,
				new DiamondShape(1),
				bg -> bg.contains(backgroundSeedLabel),
				cv -> cv.valueEquals(paintedLabel)
		          );

		requestRepaint.run();

		source.applyMask(mask, accessTracker.createAccessInterval(), FOREGROUND_CHECK);

	}

	private static <T, U> void restrictTo(
			final RandomAccessible<Pair<T, U>> source,
			final RandomAccessible<UnsignedLongType> mask,
			final Localizable seed,
			final Shape shape,
			final Predicate<T> backgroundFilter,
			final Predicate<U> canvasFilter)
	{
		final int n = source.numDimensions();

		final RandomAccessible<Pair<Pair<T, U>, UnsignedLongType>> paired = Views.pair(source, mask);

		final TLongList[] coordinates = new TLongList[n];
		for (int d = 0; d < n; ++d)
		{
			coordinates[d] = new TLongArrayList();
			coordinates[d].add(seed.getLongPosition(d));
		}

		final RandomAccessible<Neighborhood<Pair<Pair<T, U>, UnsignedLongType>>> neighborhood       = shape
				.neighborhoodsRandomAccessible(
				paired);
		final RandomAccess<Neighborhood<Pair<Pair<T, U>, UnsignedLongType>>>     neighborhoodAccess = neighborhood
				.randomAccess();

		final RandomAccess<UnsignedLongType> targetAccess = mask.randomAccess();
		targetAccess.setPosition(seed);
		targetAccess.get().set(1);

		final UnsignedLongType zero = new UnsignedLongType(0);
		final UnsignedLongType one  = new UnsignedLongType(1);
		final UnsignedLongType two  = new UnsignedLongType(2);

		for (int i = 0; i < coordinates[0].size(); ++i)
		{
			for (int d = 0; d < n; ++d)
			{
				neighborhoodAccess.setPosition(coordinates[d].get(i), d);
			}

			final Cursor<Pair<Pair<T, U>, UnsignedLongType>> neighborhoodCursor = neighborhoodAccess.get().cursor();

			while (neighborhoodCursor.hasNext())
			{
				final Pair<Pair<T, U>, UnsignedLongType> p                   = neighborhoodCursor.next();
				final UnsignedLongType                   m                   = p.getB();
				final Pair<T, U>                         backgroundAndCanvas = p.getA();
				if (m.valueEquals(zero) && canvasFilter.test(backgroundAndCanvas.getB()))
				{
					// If background is same as at seed, mark mask with two
					// (==not active), else with one (==active).
					m.set(backgroundFilter.test(backgroundAndCanvas.getA()) ? two : one);
					for (int d = 0; d < n; ++d)
					{
						coordinates[d].add(neighborhoodCursor.getLongPosition(d));
					}
				}

			}

			if (i > CLEANUP_THRESHOLD)
			{
				for (int d = 0; d < coordinates.length; ++d)
				{
					final TLongList c = coordinates[d];
					coordinates[d] = c.subList(i, c.size());
				}
				i = 0;
			}

		}
	}

}
