package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import bdv.img.h5.H5Utils;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

/**
 *
 * {@link Source} that includes a type <code>D</code> representation that is
 * used for data processing (in contrast to <code>T</code> that is used for
 * visualization).
 *
 */
public interface DataSource< D, T > extends Source< T >
{
	public RandomAccessibleInterval< D > getDataSource( int t, int level );

	public RealRandomAccessible< D > getInterpolatedDataSource( final int t, final int level, final Interpolation method );

	public D getDataType();

	/**
	 * Create a primitive single scale level source without visualization
	 * conversion from an H5 dataset.
	 *
	 * @param name
	 * @param rawFile
	 * @param rawDataset
	 * @param rawCellSize
	 * @param resolution
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createH5RawSource(
			final String name,
			final String rawFile,
			final String rawDataset,
			final int[] rawCellSize,
			final double[] resolution,
			final SharedQueue sharedQueue,
			final int priority,
			final Supplier< T > typeSupplier,
			final Supplier< V > volatileTypeSupplier ) throws IOException
	{
		final RandomAccessibleInterval< T > raw = H5Utils.open( HDF5Factory.openForReading( rawFile ), rawDataset, rawCellSize );
		final AffineTransform3D rawTransform = new AffineTransform3D();
		rawTransform.set(
				resolution[ 0 ], 0, 0, 0,
				0, resolution[ 1 ], 0, 0,
				0, 0, resolution[ 2 ], 0 );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleIntervalDataSource< T, V > rawSource =
				new RandomAccessibleIntervalDataSource< T, V >(
						( RandomAccessibleInterval< T >[] ) new RandomAccessibleInterval[] { raw },
						( RandomAccessibleInterval< V >[] ) new RandomAccessibleInterval[] {
								VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) ) },
						new AffineTransform3D[] { rawTransform },
						( interpolation ) -> {
							switch ( ( Interpolation ) interpolation )
							{
							case NLINEAR:
								return new NLinearInterpolatorFactory< T >();
							default:
								return new NearestNeighborInterpolatorFactory< T >();
							}
						},
						( interpolation ) -> {
							switch ( ( Interpolation ) interpolation )
							{
							case NLINEAR:
								return new NLinearInterpolatorFactory< V >();
							default:
								return new NearestNeighborInterpolatorFactory< V >();
							}
						},
						typeSupplier,
						volatileTypeSupplier,
						name );
		return rawSource;
	}
}
