package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.state.HasFragmentSegmentAssignments;
import org.janelia.saalfeldlab.paintera.state.HasMaskForLabel;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
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
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.util.Intervals;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;

public class FloodFill2D
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Runnable requestRepaint;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final SimpleDoubleProperty fillDepth = new SimpleDoubleProperty(1.0);

	private static final long FILL_VALUE = 1l;

	private static final class ForegroundCheck implements Predicate<UnsignedLongType>
	{
		@Override
		public boolean test(final UnsignedLongType t)
		{
			return t.getIntegerLong() == FILL_VALUE;
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
			LOG.info("No current source selected -- will not fill");
			return;
		}
		final Long fill = fillSupplier.get();
		if (fill == null)
		{
			LOG.info("Received invalid label {} -- will not fill.", fill);
			return;
		}
		fillAt(x, y, fill);
	}

	@SuppressWarnings("unchecked")
	public <T extends IntegerType<T>> void fillAt(final double x, final double y, final long fill)
	{
		final Source<?>   currentSource = sourceInfo.currentSourceProperty().get();
		final ViewerState viewerState   = viewer.getState();
		if (currentSource == null)
		{
			LOG.info("No current source selected -- will not fill");
			return;
		}

		final SourceState<T, ?> currentSourceState = (SourceState<T, ?>) sourceInfo
				.getState(
				currentSource);

		if (!(currentSourceState instanceof HasMaskForLabel<?>))
		{
			LOG.info("Selected source cannot provide mask for label -- will not fill");
			return;
		}

		final HasMaskForLabel<T> hasMaskForLabel = (HasMaskForLabel<T>) currentSourceState;

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

		final LongFunction<Converter<T, BoolType>> maskForLabel = hasMaskForLabel.maskForLabel();
		if (maskForLabel == null)
		{
			LOG.info("Cannot generate boolean mask for this source -- will not fill");
			return;
		}

		final FragmentSegmentAssignment assignment;
		if (currentSourceState instanceof HasFragmentSegmentAssignments)
		{
			LOG.info("Selected source has a fragment-segment assignment that will be used for filling");
			assignment = ((HasFragmentSegmentAssignments) currentSourceState).assignment();
		}
		else
		{
			assignment = null;
		}

		final MaskedSource<T, ?> source = (MaskedSource<T, ?>) currentSource;

		final T t = source.getDataType();

		if (t == null)
		{
			LOG.debug("Data type is null -- will not fill");
			return;
		}

		final int level = 0;
		final int time = viewerState.getTimepoint();
		final MaskInfo<UnsignedLongType> maskInfo = new MaskInfo<>(time, level, new UnsignedLongType(fill));

		final Scene  scene          = viewer.getScene();
		final Cursor previousCursor = scene.getCursor();
		scene.setCursor(Cursor.WAIT);
		try
		{
			final Mask<UnsignedLongType> mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
			final Interval affectedInterval = fillMaskAt(x, y, this.viewer, mask, source, assignment, FILL_VALUE, this.fillDepth.get());
			requestRepaint.run();
			source.applyMask(mask, affectedInterval, FOREGROUND_CHECK);
		} catch (final MaskInUse e)
		{
			LOG.debug(e.getMessage());
			return;
		} finally
		{
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
			final Mask<UnsignedLongType> mask,
			final MaskedSource<T, ?> source,
			final FragmentSegmentAssignment assignment,
			final long fillValue,
			final double fillDepth)
	{
		final int time = mask.info.t;
		final int level = mask.info.level;

		final AffineTransform3D labelTransform = new AffineTransform3D();
		source.getSourceTransform(time, level, labelTransform);

		final RandomAccessibleInterval<T> background = source.getDataSource(time, level);
		final RandomAccess<T> access = background.randomAccess();
		final RealPoint pos = new RealPoint(access.numDimensions());
		viewer.displayToSourceCoordinates(x, y, labelTransform, pos);
		for (int d = 0; d < access.numDimensions(); ++d)
			access.setPosition(Math.round(pos.getDoublePosition(d)), d);
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
			final Mask<UnsignedLongType> mask,
			final RandomAccessibleInterval<BoolType> filter,
			final AffineTransform3D labelTransform,
			final long fillValue,
			final double fillDepth)
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewer.getState().getViewerTransform(viewerTransform);
		final AffineTransform3D labelToViewerTransform = viewerTransform.copy().concatenate(labelTransform);

		final RealPoint pos = new RealPoint(labelTransform.numDimensions());
		viewer.displayToSourceCoordinates(x, y, labelTransform, pos);

		final RandomAccessible<BoolType> extendedFilter = Views.extendValue(filter, new BoolType(false));

		final int fillNormalAxisInLabelCoordinateSystem = PaintUtils.labelAxisCorrespondingToViewerAxis(labelTransform, viewerTransform, 2);
		final AccessBoxRandomAccessibleOnGet<UnsignedLongType> accessTracker = new
				AccessBoxRandomAccessibleOnGet<>(
				Views.extendValue(mask.mask, new UnsignedLongType(fillValue)));
		accessTracker.initAccessBox();

		if (fillNormalAxisInLabelCoordinateSystem < 0)
		{
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
		}
		else
		{
			LOG.debug(
					"Flood filling axis aligned. Corressponding viewer axis={}",
					fillNormalAxisInLabelCoordinateSystem
			         );
			final long slicePos  = Math.round(pos.getDoublePosition(fillNormalAxisInLabelCoordinateSystem));
			final long numSlices = Math.max((long) Math.ceil(fillDepth) - 1, 0);
			if (numSlices == 0)
			{
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
			}
			else
			{
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

		final Interval affectedInterval = new FinalInterval(accessTracker.getMin(), accessTracker.getMax());
		return affectedInterval;
	}

	public DoubleProperty fillDepthProperty()
	{
		return this.fillDepth;
	}

}
