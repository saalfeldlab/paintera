package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessible;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.mask.Mask;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FloodFill
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Runnable requestRepaint;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private static final class ForegroundCheck implements Predicate<UnsignedLongType>
	{

		@Override
		public boolean test(final UnsignedLongType t)
		{
			return t.getIntegerLong() == 1;
		}

	}

	private static final ForegroundCheck FOREGROUND_CHECK = new ForegroundCheck();

	public FloodFill(final ViewerPanelFX viewer, final SourceInfo sourceInfo, final Runnable requestRepaint)
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

	@SuppressWarnings({"rawtypes", "unchecked"})
	public void fillAt(final double x, final double y, final long fill)
	{
		final Source<?>   currentSource = sourceInfo.currentSourceProperty().get();
		final ViewerState viewerState   = viewer.getState();
		if (currentSource == null)
		{
			LOG.info("No current source selected -- will not fill");
			return;
		}

		final SourceState<?, ?> currentSourceState = sourceInfo.getState(currentSource);

		if (!(currentSourceState instanceof LabelSourceState<?, ?>))
		{
			LOG.info("Selected source is not a label source -- will not fill");
			return;
		}

		final LabelSourceState<?, ?> state = (LabelSourceState<?, ?>) currentSourceState;

		if (!state.isVisibleProperty().get())
		{
			LOG.info("Selected source is not visible -- will not fill");
			return;
		}

		if (!(currentSource instanceof MaskedSource<?, ?>))
		{
			LOG.info("Selected source is not painting-enabled -- will not fill");
			return;
		}

		final LongFunction<?> maskForLabel = state.maskForLabel();
		if (maskForLabel == null)
		{
			LOG.info("Cannot generate boolean mask for this source -- will not fill");
			return;
		}

		final MaskedSource<?, ?> source = (MaskedSource<?, ?>) currentSource;

		final Type<?> t = source.getDataType();

		if (!(t instanceof RealType<?>) && !(t instanceof LabelMultisetType))
		{
			LOG.info("Data type is not real or LabelMultisetType type -- will not fill");
			return;
		}

		final int               level          = 0;
		final AffineTransform3D labelTransform = new AffineTransform3D();
		final int               time           = viewerState.timepointProperty().get();
		source.getSourceTransform(time, level, labelTransform);

		final RealPoint rp = setCoordinates(x, y, viewer, labelTransform);
		final Point     p  = new Point(rp.numDimensions());
		for (int d = 0; d < p.numDimensions(); ++d)
		{
			p.setPosition(Math.round(rp.getDoublePosition(d)), d);
		}

		LOG.debug("Filling source {} with label {} at {}", source, fill, p);
		final Scene  scene          = viewer.getScene();
		final Cursor previousCursor = scene.getCursor();
		try
		{
			if (t instanceof LabelMultisetType)
			{
				fillMultiset(
						(MaskedSource) source,
						time,
						level,
						fill,
						p,
						new RunAll(requestRepaint, () -> scene.setCursor(Cursor.WAIT)),
						new RunAll(requestRepaint, () -> scene.setCursor(previousCursor))
				            );
			}
			else
			{
				fill(
						(MaskedSource) source,
						time,
						level,
						fill,
						p,
						new RunAll(requestRepaint, () -> scene.setCursor(Cursor.WAIT)),
						new RunAll(requestRepaint, () -> scene.setCursor(previousCursor))
				    );
			}
		} catch (final MaskInUse e)
		{
			LOG.info(e.getMessage());
			return;
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

	private static <T extends RealType<T>> void fill(
			final MaskedSource<T, ?> source,
			final int time,
			final int level,
			final long fill,
			final Localizable seed,
			final Runnable doWhileFilling,
			final Runnable doWhenDone) throws MaskInUse
	{
		final MaskInfo<UnsignedLongType>                  maskInfo      = new MaskInfo<>(
				time,
				level,
				new UnsignedLongType(fill)
		);
		final Mask<UnsignedLongType> mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
		final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views
				.extendValue(mask.mask, new UnsignedLongType(1)));
		final Thread t = new Thread(() -> {
			net.imglib2.algorithm.fill.FloodFill.fill(
					source.getDataSource(time, level),
					accessTracker,
					seed,
					new UnsignedLongType(1),
					new DiamondShape(1)
			                                         );
			final Interval interval = accessTracker.createAccessInterval();
			LOG.debug(
					"Applying mask for interval {} {}",
					Arrays.toString(Intervals.minAsLongArray(interval)),
					Arrays.toString(Intervals.maxAsLongArray(interval))
			         );
		});
		t.start();
		new Thread(() -> {
			while (t.isAlive() && !Thread.interrupted())
			{
				try
				{
					Thread.sleep(100);
				} catch (final InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOG.debug("Updating current view!");
				doWhileFilling.run();
			}
			doWhenDone.run();
			if (!Thread.interrupted())
			{
				source.applyMask(mask, accessTracker.createAccessInterval(), FOREGROUND_CHECK);
			}
		}).start();
	}

	private static void fillMultiset(
			final MaskedSource<LabelMultisetType, ?> source,
			final int time,
			final int level,
			final long fill,
			final Localizable seed,
			final Runnable doWhileFilling,
			final Runnable doWhenDone) throws MaskInUse
	{

		final RandomAccessibleInterval<LabelMultisetType> data       = source.getDataSource(time, level);
		final RandomAccess<LabelMultisetType>             dataAccess = data.randomAccess();
		dataAccess.setPosition(seed);
		final long seedLabel = getArgMaxLabel(dataAccess.get());
		if (!Label.regular(seedLabel))
		{
			LOG.info("Trying to fill at irregular label: {} ({})", seedLabel, new Point(seed));
			return;
		}

		final MaskInfo<UnsignedLongType>                  maskInfo      = new MaskInfo<>(
				time,
				level,
				new UnsignedLongType(fill)
		);
		final Mask<UnsignedLongType>  mask = source.generateMask(maskInfo, FOREGROUND_CHECK);
		final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views
				.extendValue(mask.mask, new UnsignedLongType(1)));
		final Thread t = new Thread(() -> {
			net.imglib2.algorithm.fill.FloodFill.fill(
					Views.extendValue(data, new LabelMultisetType()),
					accessTracker,
					seed,
					new UnsignedLongType(1),
					new DiamondShape(1),
					makePredicateMultiset(seedLabel)
			                                         );
			final Interval interval = accessTracker.createAccessInterval();
			LOG.debug(
					"Applying mask for interval {} {}",
					Arrays.toString(Intervals.minAsLongArray(interval)),
					Arrays.toString(Intervals.maxAsLongArray(interval))
			         );
		});
		t.start();
		new Thread(() -> {
			while (t.isAlive() && !Thread.interrupted())
			{
				try
				{
					Thread.sleep(100);
				} catch (final InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOG.debug("Updating current view!");
				doWhileFilling.run();
			}
			doWhenDone.run();
			if (!Thread.interrupted())
			{
				source.applyMask(mask, accessTracker.createAccessInterval(), FOREGROUND_CHECK);
			}
		}).start();
	}

	private static BiPredicate<LabelMultisetType, UnsignedLongType> makePredicateMultiset(final long id)
	{
		final UnsignedLongType zero = new UnsignedLongType(0);
		return (l, u) -> zero.valueEquals(u) && l.contains(id);
	}

	public static class RunAll implements Runnable
	{

		private final List<Runnable> runnables;

		public RunAll(final Runnable... runnables)
		{
			this(Arrays.asList(runnables));
		}

		public RunAll(final Collection<Runnable> runnables)
		{
			super();
			this.runnables = new ArrayList<>(runnables);
		}

		@Override
		public void run()
		{
			this.runnables.forEach(Runnable::run);
		}

	}

	public static long getArgMaxLabel(final LabelMultisetType t)
	{
		long argmax = Label.INVALID;
		long max    = 0;
		for (final Entry<net.imglib2.type.label.Label> e : t.entrySet())
		{
			final int count = e.getCount();
			if (count > max)
			{
				max = count;
				argmax = e.getElement().id();
			}
		}
		return argmax;
	}

}
