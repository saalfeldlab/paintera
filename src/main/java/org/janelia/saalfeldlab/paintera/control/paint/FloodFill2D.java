package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import net.imglib2.FinalInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInUse;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FloodFill2D
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Runnable requestRepaint;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final SimpleDoubleProperty fillDepth = new SimpleDoubleProperty(1.0);

	private static final class ForegroundCheck implements Predicate<UnsignedLongType>
	{

		@Override
		public boolean test(final UnsignedLongType t)
		{
			return t.getIntegerLong() == 1;
		}

	}

	private static final ForegroundCheck FOREGROUND_CHECK = new ForegroundCheck();

	public FloodFill2D(final ViewerPanelFX viewer, final SourceInfo sourceInfo, final Runnable requestRepaint)
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.requestRepaint = requestRepaint;
		viewer.addTransformListener(t -> viewerTransform.set(t));
	}

	public void fillAt(final double x, final double y, final Supplier<Long> fillSupplier)
	{
		if (sourceInfo.currentSourceProperty().get() == null)
		{
			LOG.warn("No current source selected -- will not fill");
			return;
		}
		final Long fill = fillSupplier.get();
		if (fill == null)
		{
			LOG.warn("Received invalid label {} -- will not fill.", fill);
			return;
		}
		fillAt(x, y, fill);
	}

	public <T extends IntegerType<T>> void fillAt(final double x, final double y, final long fill)
	{
		final Source<?>   currentSource = sourceInfo.currentSourceProperty().get();
		final ViewerState viewerState   = viewer.getState();
		if (currentSource == null)
		{
			LOG.warn("No current source selected -- will not fill");
			return;
		}

		@SuppressWarnings("unchecked") final SourceState<T, ?> currentSourceState = (SourceState<T, ?>) sourceInfo
				.getState(
				currentSource);

		if (!(currentSourceState instanceof LabelSourceState<?, ?>))
		{
			LOG.warn("Selected source is not a label source -- will not fill");
			return;
		}

		final LabelSourceState<T, ?> state = (LabelSourceState<T, ?>) currentSourceState;

		if (!state.isVisibleProperty().get())
		{
			LOG.warn("Selected source is not visible -- will not fill");
			return;
		}

		if (!(currentSource instanceof MaskedSource<?, ?>))
		{
			LOG.warn("Selected source is not painting-enabled -- will not fill");
			return;
		}

		final LongFunction<Converter<T, BoolType>> maskForLabel = state.maskForLabel();
		if (maskForLabel == null)
		{
			LOG.warn("Cannot generate boolean mask for this source -- will not fill");
			return;
		}

		@SuppressWarnings("unchecked") final MaskedSource<T, ?> source = (MaskedSource<T, ?>) currentSource;

		final T t = source.getDataType();

		if (!(t instanceof RealType<?>) && !(t instanceof LabelMultisetType))
		{
			LOG.warn("Data type is not real or LabelMultisetType type -- will not fill");
			return;
		}

		final int               level          = 0;
		final AffineTransform3D labelTransform = new AffineTransform3D();
		final int               time           = viewerState.timepointProperty().get();
		source.getSourceTransform(time, level, labelTransform);
		final AffineTransform3D viewerTransform        = this.viewerTransform.copy();
		final AffineTransform3D labelToViewerTransform = this.viewerTransform.copy().concatenate(labelTransform);

		final RealPoint                   rp         = setCoordinates(x, y, viewer, labelTransform);
		final RandomAccessibleInterval<T> background = source.getDataSource(time, level);
		final RandomAccess<T>             access     = background.randomAccess();
		for (int d = 0; d < access.numDimensions(); ++d)
		{
			access.setPosition(Math.round(rp.getDoublePosition(d)), d);
		}

		final MaskInfo<UnsignedLongType> maskInfo = new MaskInfo<>(time, level, new UnsignedLongType(fill));

		final Scene  scene          = viewer.getScene();
		final Cursor previousCursor = scene.getCursor();
		scene.setCursor(Cursor.WAIT);
		try
		{
			final RandomAccessibleInterval<UnsignedLongType> mask      = source.generateMask(
					maskInfo,
					FOREGROUND_CHECK
			                                                                                );
			final long                                       seedLabel = access.get().getIntegerLong();
			LOG.warn("Got seed label {}", seedLabel);
			final RandomAccessibleInterval<BoolType> relevantBackground = Converters.convert(
					background,
					(src, tgt) -> tgt.set(src.getIntegerLong() == seedLabel),
					new BoolType()
			                                                                                );
			final RandomAccessible<BoolType> extended = Views.extendValue(relevantBackground, new BoolType(false));

			final int fillNormalAxisInLabelCoordinateSystem = PaintUtils.labelAxisCorrespondingToViewerAxis(
					labelTransform,
					viewerTransform,
					2
			                                                                                               );
			final AccessBoxRandomAccessibleOnGet<UnsignedLongType> accessTracker = new
					AccessBoxRandomAccessibleOnGet<>(
					Views.extendValue(mask, new UnsignedLongType(1l)));
			accessTracker.initAccessBox();

			if (fillNormalAxisInLabelCoordinateSystem < 0)

			{

				FloodFillTransformedPlane.fill(
						labelToViewerTransform,
						(0.5 + this.fillDepth.get() - 1.0) * PaintUtils.maximumVoxelDiagonalLengthPerDimension(
								labelTransform,
								viewerTransform
						                                                                                      )[2],
						extended.randomAccess(),
						accessTracker.randomAccess(),
						new RealPoint(x, y, 0),
						1l
				                              );

				requestRepaint.run();
				source.applyMask(
						mask,
						new FinalInterval(accessTracker.getMin(), accessTracker.getMax()),
						FOREGROUND_CHECK
				                );
			}

			else
			{
				LOG.debug(
						"Flood filling axis aligned. Corressponding viewer axis={}",
						fillNormalAxisInLabelCoordinateSystem
				         );
				final long slicePos  = access.getLongPosition(fillNormalAxisInLabelCoordinateSystem);
				final long numSlices = Math.max((long) Math.ceil(this.fillDepth.get()) - 1, 0);
				final long[] seed2D = {
						access.getLongPosition(fillNormalAxisInLabelCoordinateSystem == 0 ? 1 : 0),
						access.getLongPosition(fillNormalAxisInLabelCoordinateSystem != 2 ? 2 : 1)
				};
				accessTracker.initAccessBox();
				final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
				final long[] max = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
				for (long i = slicePos - numSlices; i <= slicePos + numSlices; ++i)
				{
					final MixedTransformView<BoolType> relevantBackgroundSlice = Views.hyperSlice(
							extended,
							fillNormalAxisInLabelCoordinateSystem,
							i
					                                                                             );
					final MixedTransformView<UnsignedLongType> relevantAccessTracker = Views.hyperSlice(
							accessTracker,
							fillNormalAxisInLabelCoordinateSystem,
							i
					                                                                                   );

					FloodFill.fill(
							relevantBackgroundSlice,
							relevantAccessTracker,
							new Point(seed2D),
							new UnsignedLongType(1l),
							new DiamondShape(1)
					              );
					Arrays.setAll(min, d -> Math.min(accessTracker.getMin()[d], min[d]));
					Arrays.setAll(max, d -> Math.max(accessTracker.getMax()[d], max[d]));
				}

				requestRepaint.run();
				source.applyMask(mask, new FinalInterval(min, max), FOREGROUND_CHECK);

			}

		} catch (final MaskInUse e)
		{
			LOG.warn(e.getMessage());
			return;
		} finally
		{
			scene.setCursor(previousCursor);
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

	public DoubleProperty fillDepthProperty()
	{
		return this.fillDepth;
	}

}
