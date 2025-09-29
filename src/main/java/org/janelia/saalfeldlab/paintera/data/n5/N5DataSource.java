package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.cache.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState;

import java.io.IOException;
import java.util.function.Function;

public class N5DataSource<D extends NativeType<D>, T extends Volatile<D> & NativeType<T>> extends RandomAccessibleIntervalDataSource<D, T> {

	private final MetadataState metadataState;

	public N5DataSource(
			final MetadataState metadataState,
			final String name,
			final SharedQueue queue,
			final int priority) throws IOException {

		this(
				metadataState,
				name,
				queue,
				priority,
				interpolation(metadataState),
				interpolation(metadataState));
	}

	public N5DataSource(
			final MetadataState metadataState,
			final String name,
			final SharedQueue queue,
			final int priority,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation) throws
			IOException {

		super(
				RandomAccessibleIntervalDataSource.asDataWithInvalidate(metadataState.<D, T>getData(queue, priority)),
				dataInterpolation,
				interpolation,
				name);

		this.metadataState = metadataState;
	}

	@Override public RandomAccessibleInterval<T> getSource(int t, int level) {

		final Interval virtualCrop = getVirtualCrop(t, level);
		if (virtualCrop == null) {
			return super.getSource(t, level);
		}
		return Views.interval(super.getSource(t, level), virtualCrop);
	}

	@Override public RandomAccessibleInterval<D> getDataSource(int t, int level) {

		final Interval virtualCrop = getVirtualCrop(t, level);
		if (virtualCrop == null) {
			return super.getDataSource(t, level);
		}
		return Views.interval(super.getDataSource(t, level), virtualCrop);
	}

	private Interval getVirtualCrop(int t, int level) {

		var virtualCrop = metadataState.getVirtualCrop();
		if (virtualCrop == null) return null;
		else if (level == 0) return virtualCrop;

		MultiScaleMetadataState state = (MultiScaleMetadataState)metadataState;
		final AffineTransform3D[] transforms = state.getScaleTransforms();

		final FinalRealInterval realVirtualCropForLevel = transforms[0].copy().concatenate(transforms[level].inverse()).estimateBounds(virtualCrop);
		return Intervals.smallestContainingInterval(realVirtualCropForLevel);
	}

	public MetadataState getMetadataState() {

		return metadataState;
	}

	public N5Reader reader() throws IOException {

		return metadataState.getReader();
	}

	public N5Writer writer() throws IOException {

		return metadataState.getWriter();
	}

	public String dataset() {

		return metadataState.getGroup();
	}

	static <T extends NativeType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	interpolation(MetadataState metadataState) {

		if (metadataState.isLabel() || metadataState.isLabelMultiset() )
			return 	i -> new NearestNeighborInterpolatorFactory<>();
		else
			return (Function)realTypeInterpolation();
	}

	private static <T extends RealType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	realTypeInterpolation() {

		return i -> i.equals(Interpolation.NLINEAR)
				? new NLinearInterpolatorFactory<>()
				: new NearestNeighborInterpolatorFactory<>();
	}
}
