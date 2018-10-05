package org.janelia.saalfeldlab.paintera.data;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;

import java.util.Arrays;

/**
 * {@link Source} that includes a type {@code D} representation that is used for data processing (in contrast to
 * {@code T}that is used for visualization).
 */
public interface DataSource<D, T> extends Source<T>, InvalidateAll
{
	RandomAccessibleInterval<D> getDataSource(int t, int level);

	RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method);

	D getDataType();

	/**
	 * Convenience method to extract the scale of a {@link Source} from the diagonal of the {@link AffineTransform3D} at
	 * {@code t} and {@code level}.
	 * @param source Extract scale from this source
	 * @param t Extract scale at this time points
	 * @param level Extract scale at this mipmap level
	 * @return diagonal of transform of {@code source} at time {@code t} and level {@code level}.
	 */
	static double[] getScale(final Source<?> source, final int t, final int level)
	{
		final AffineTransform3D transform = new AffineTransform3D();
		source.getSourceTransform(t, level, transform);
		return new double[] {transform.get(0, 0), transform.get(1, 1), transform.get(2, 2)};
	}

	/**
	 * Convenience method to extract the relative scale of two levels of a {@link Source} at time {@code t}.
	 * @param source Extract relative scale from this source
	 * @param t Extract relative scale at this time points
	 * @param level source level
	 * @param targetLevel target level
	 * @return ratio of diagonals of transforms at levels {@code targetLevel} and {@code level} for {@code source} at time {@code t}:
	 * scale[targetLevel] / scale[level]
	 */
	static double[] getRelativeScales(
			final Source<?> source,
			final int t,
			final int level,
			final int targetLevel)
	{
		final double[] scale       = getScale(source, t, level);
		final double[] targetScale = getScale(source, t, targetLevel);
		Arrays.setAll(targetScale, d -> targetScale[d] / scale[d]);
		return targetScale;
	}
}
