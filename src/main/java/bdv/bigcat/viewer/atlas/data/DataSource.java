package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

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

	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createDataSource(
			final String name,
			final RandomAccessibleInterval< T > data,
			final AffineGet rawTransform,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final T t = Util.getTypeFromInterval( data );
		@SuppressWarnings( "unchecked" )
		final V v = ( V ) VolatileTypeMatcher.getVolatileTypeForType( t );

		final AffineTransform3D transform = new AffineTransform3D();
		transform.set( rawTransform.getRowPackedCopy() );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleIntervalDataSource< T, V > source =
				new RandomAccessibleIntervalDataSource< T, V >(
						new RandomAccessibleInterval[] { data },
						new RandomAccessibleInterval[] {
								VolatileViews.wrapAsVolatile( data, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) ) },
						new AffineTransform3D[] { transform },
						( interpolation ) -> {
							switch ( interpolation )
							{
							case NLINEAR:
								return new NLinearInterpolatorFactory<>();
							default:
								return new NearestNeighborInterpolatorFactory<>();
							}
						},
						( interpolation ) -> {
							switch ( interpolation )
							{
							case NLINEAR:
								return new NLinearInterpolatorFactory<>();
							default:
								return new NearestNeighborInterpolatorFactory<>();
							}
						},
						t::createVariable,
						v::createVariable,
						name );
		return source;
	}

	/**
	 * Create a primitive single scale level source without visualization
	 * conversion from a {@link RandomAccessibleInterval}
	 *
	 * @param name
	 * @param data
	 * @param resolution
	 * @param offset
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createDataSource(
			final String name,
			final RandomAccessibleInterval< T > data,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final AffineTransform3D rawTransform = new AffineTransform3D();
		rawTransform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );
		return createDataSource( name, data, rawTransform, sharedQueue, priority );
	}

	/**
	 * Create a primitive single scale level source without visualization
	 * conversion from a {@link RandomAccessibleInterval}
	 *
	 * @param name
	 * @param data
	 * @param resolution
	 * @param offset
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createDataSource(
			final String name,
			final RandomAccessibleInterval< T > data,
			final double[] resolution,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return createDataSource( name, data, resolution, new double[ resolution.length ], sharedQueue, priority );
	}

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
			final int priority ) throws IOException
	{
		return createH5RawSource( name, rawFile, rawDataset, rawCellSize, resolution, new double[ resolution.length ], sharedQueue, priority );
	}

	/**
	 * Create a primitive single scale level source without visualization
	 * conversion from an H5 dataset.
	 *
	 * @param name
	 * @param rawFile
	 * @param rawDataset
	 * @param rawCellSize
	 * @param resolution
	 * @param offset
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
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		// we pass a cell size, so we want to override
		return createDataSource(
				name,
				( RandomAccessibleInterval< T > ) N5Utils.openVolatile( new N5HDF5Reader( rawFile, true, rawCellSize ), rawDataset ),
				resolution,
				offset,
				sharedQueue, priority );
	}

	/**
	 * Create a primitive single scale level source without visualization
	 * conversion from an N5 dataset.
	 *
	 * @param name
	 * @param n5
	 * @param rawDataset
	 * @param resolution
	 * @param offset
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createN5Source(
			final String name,
			final N5Reader n5,
			final String dataset,
			final double[] resolution,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return createN5Source( name, n5, dataset, resolution, new double[ resolution.length ], sharedQueue, priority );
	}

	/**
	 * Create a primitive single scale level source without visualization
	 * conversion from an N5 dataset.
	 *
	 * @param name
	 * @param n5
	 * @param rawDataset
	 * @param resolution
	 * @param offset
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createN5Source(
			final String name,
			final N5Reader n5,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final AffineTransform3D rawTransform = new AffineTransform3D();
		rawTransform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );
		return createN5Source( name, n5, dataset, rawTransform, sharedQueue, priority );
	}

	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createN5Source(
			final String name,
			final N5Reader n5,
			final String dataset,
			final AffineGet rawTransform,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return createDataSource(
				name,
				( RandomAccessibleInterval< T > ) N5Utils.openVolatile( n5, dataset ),
				rawTransform,
				sharedQueue,
				priority );
	}

	/**
	 * Create a primitive multi scale level source without visualization
	 * conversion from an N5 multi scale group.
	 *
	 * @param name
	 * @param n5
	 * @param group
	 * @param resolution
	 * @param offset
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings( "unchecked" )
	public static < T extends NativeType< T > & NumericType< T >, V extends Volatile< T > & NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createN5MipmapSource(
			final String name,
			final N5Reader n5,
			final String group,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final Supplier< T > typeSupplier,
			final Supplier< V > volatileTypeSupplier ) throws IOException
	{
		final AffineTransform3D rawTransform = new AffineTransform3D();
		rawTransform.set(
				resolution[ 0 ], 0, 0, 0,
				0, resolution[ 1 ], 0, 0,
				0, 0, resolution[ 2 ], 0 );

		final ArrayList< RandomAccessibleInterval< T > > mipmaps = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< V > > volatileMipmaps = new ArrayList<>();
		final ArrayList< AffineTransform3D > mipmapTransforms = new ArrayList<>();

		final String[] sortedMipmapDatasets =
				Arrays.stream( n5.list( group ) )
						.filter( dataset -> dataset.matches( "s\\d+" ) )
						.sorted()
						.toArray( String[]::new );

		for ( int i = 0; i < sortedMipmapDatasets.length; ++i )
		{
			final RandomAccessibleInterval< T > mipmap = N5Utils.openVolatile( n5, group + "/" + sortedMipmapDatasets[ i ] );
			mipmaps.add( mipmap );
			volatileMipmaps.add( VolatileViews.wrapAsVolatile( mipmap, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, i, true ) ) );
			final long[] downsampleFactors = n5.getAttribute( group + "/" + sortedMipmapDatasets[ i ], "downsamplingFactors", long[].class );
			final AffineTransform3D mipmapTransform = rawTransform.copy();
			if ( downsampleFactors != null )
				mipmapTransform.set(
						resolution[ 0 ] * downsampleFactors[ 0 ], 0, 0, 0.5 * ( downsampleFactors[ 0 ] - 1 + offset[ 0 ] ) * resolution[ 0 ],
						0, resolution[ 1 ] * downsampleFactors[ 1 ], 0, 0.5 * ( downsampleFactors[ 1 ] - 1 + offset[ 1 ] ) * resolution[ 1 ],
						0, 0, resolution[ 2 ] * downsampleFactors[ 2 ], 0.5 * ( downsampleFactors[ 2 ] - 1 + offset[ 2 ] ) * resolution[ 2 ] );
			mipmapTransforms.add( mipmapTransform );
		}

		final RandomAccessibleIntervalDataSource< T, V > source =
				new RandomAccessibleIntervalDataSource<>(
						mipmaps.toArray( new RandomAccessibleInterval[ 0 ] ),
						volatileMipmaps.toArray( new RandomAccessibleInterval[ 0 ] ),
						mipmapTransforms.toArray( new AffineTransform3D[ 0 ] ),
						( interpolation ) -> {
							switch ( interpolation )
							{
							case NLINEAR:
								return new NLinearInterpolatorFactory<>();
							default:
								return new NearestNeighborInterpolatorFactory<>();
							}
						},
						( interpolation ) -> {
							switch ( interpolation )
							{
							case NLINEAR:
								return new NLinearInterpolatorFactory<>();
							default:
								return new NearestNeighborInterpolatorFactory<>();
							}
						},
						typeSupplier,
						volatileTypeSupplier,
						name );
		return source;
	}

	/**
	 * Create a primitive multi scale level source without visualization
	 * conversion from an N5 multi scale group.
	 *
	 * @param name
	 * @param n5
	 * @param group
	 * @param resolution
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings( "unchecked" )
	public static < T extends NativeType< T > & NumericType< T >, V extends Volatile< T > & NumericType< V > > RandomAccessibleIntervalDataSource< T, V > createN5MipmapSource(
			final String name,
			final N5Reader n5,
			final String group,
			final double[] resolution,
			final SharedQueue sharedQueue,
			final Supplier< T > typeSupplier,
			final Supplier< V > volatileTypeSupplier ) throws IOException
	{
		return createN5MipmapSource( name, n5, group, resolution, new double[ resolution.length ], sharedQueue, typeSupplier, volatileTypeSupplier );
	}

	default public int tMin()
	{
		return 0;
	}

	default public int tMax()
	{
		return 0;
	}
}
