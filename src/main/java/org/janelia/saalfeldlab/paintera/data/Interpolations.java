package org.janelia.saalfeldlab.paintera.data;

import java.util.function.Function;

import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.numeric.NumericType;

public class Interpolations
{

	public static <T extends NumericType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	nearestNeighborOrNLinear()
	{
		return new NeaerestNeighborOrNLinear<>();
	}

	public static <T> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> nearestNeighbor()
	{
		return new NearestNeighborOnly<>();
	}

	public static class NeaerestNeighborOrNLinear<T extends NumericType<T>>
			implements Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	{

		@Override
		public InterpolatorFactory<T, RandomAccessible<T>> apply(final Interpolation t)
		{
			return t.equals(Interpolation.NLINEAR)
			       ? new NLinearInterpolatorFactory<>()
			       : new NearestNeighborInterpolatorFactory<>();
		}

	}

	public static class NearestNeighborOnly<T>
			implements Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	{

		@Override
		public InterpolatorFactory<T, RandomAccessible<T>> apply(final Interpolation t)
		{
			return new NearestNeighborInterpolatorFactory<>();
		}

	}

}
