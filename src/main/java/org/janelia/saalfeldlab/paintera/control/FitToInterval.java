package org.janelia.saalfeldlab.paintera.control;

import bdv.viewer.Source;
import javafx.collections.ListChangeListener;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;

import java.util.Arrays;
import java.util.function.DoubleSupplier;
import java.util.stream.IntStream;

public class FitToInterval {

	private final GlobalTransformManager manager;

	private final DoubleSupplier width;

	public FitToInterval(final GlobalTransformManager manager, final DoubleSupplier width) {

		super();
		this.manager = manager;
		this.width = width;
	}

	public void fit(final Interval interval) {

		final double[] translation = IntStream
				.range(0, interval.numDimensions())
				.mapToDouble(d -> -0.5 * (interval.min(d) + interval.max(d)))
				.toArray();
		final AffineTransform3D tf = new AffineTransform3D();
		tf.translate(translation);
		tf.scale(width.getAsDouble() / (interval.dimension(0)));
		manager.setTransform(tf);
	}

	public static ListChangeListener<Source<?>> fitToIntervalWhenSourceAddedListener(
			final GlobalTransformManager manager,
			final DoubleSupplier width) {

		return fitToIntervalWhenSourceAddedListener(new FitToInterval(manager, width));
	}

	public static ListChangeListener<Source<?>> fitToIntervalWhenSourceAddedListener(final FitToInterval fitToInterval) {

		return new FitToIntervalWhenSourceAddedListener(fitToInterval);
	}

	public static class FitToIntervalWhenSourceAddedListener implements ListChangeListener<Source<?>> {

		private final FitToInterval fitToInterval;

		public FitToIntervalWhenSourceAddedListener(final FitToInterval fitToInterval) {

			super();
			this.fitToInterval = fitToInterval;
		}

		@Override
		public void onChanged(final Change<? extends Source<?>> change) {

			while (change.next()) {
				if (change.wasAdded() && change.getList().size() == 1) {
					final Source<?> addedSource = change.getAddedSubList().get(0);
					final RandomAccessibleInterval<?> source = addedSource.getSource( 0, 0 );
					final double[] min = Arrays.stream(Intervals.minAsLongArray(source)).asDoubleStream().toArray();
					final double[] max = Arrays.stream(Intervals.maxAsLongArray(source)).asDoubleStream().toArray();
					final AffineTransform3D tf = new AffineTransform3D();
					addedSource.getSourceTransform(0, 0, tf);
					tf.apply(min, min);
					tf.apply(max, max);
					fitToInterval.fit(Intervals.smallestContainingInterval(new FinalRealInterval(min, max)));
				}
			}

		}

	}

}
