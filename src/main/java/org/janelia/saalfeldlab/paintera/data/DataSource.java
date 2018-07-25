package org.janelia.saalfeldlab.paintera.data;

import java.util.Arrays;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * {@link Source} that includes a type <code>D</code> representation that is used for data processing (in contrast to
 * <code>T</code> that is used for visualization).
 */
public interface DataSource<D, T> extends Source<T>
{
	public RandomAccessibleInterval<D> getDataSource(int t, int level);

	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method);

	public D getDataType();

	default public int tMin()
	{
		return 0;
	}

	default public int tMax()
	{
		return 0;
	}

	public static double[] getScale(final Source<?> source, final int t, final int level)
	{
		final AffineTransform3D transform = new AffineTransform3D();
		source.getSourceTransform(t, level, transform);
		return new double[] {transform.get(0, 0), transform.get(1, 1), transform.get(2, 2)};
	}

	public static double[] getRelativeScales(final Source<?> source, final int t, final int level, final int
			targetLevel)
	{
		final double[] scale       = getScale(source, t, level);
		final double[] targetScale = getScale(source, t, targetLevel);
		Arrays.setAll(targetScale, d -> targetScale[d] / scale[d]);
		return targetScale;
	}
}
