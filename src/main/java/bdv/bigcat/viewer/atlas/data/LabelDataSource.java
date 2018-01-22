package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.img.h5.H5Utils;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

public interface LabelDataSource< D, T > extends DataSource< D, T >
{

	public FragmentSegmentAssignmentState< ? > getAssignment();

	/**
	 * Create a primitive single scale level label source from an H5 dataset.
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
	 * @param assignment
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createH5LabelSource(
			final String name,
			final String rawFile,
			final String rawDataset,
			final int[] rawCellSize,
			final double[] resolution,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		return createH5LabelSource( name, rawFile, rawDataset, rawCellSize, resolution, new double[ resolution.length ], sharedQueue, priority, assignment );
	}

	/**
	 * Create a primitive single scale level label source from an H5 dataset.
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
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createH5LabelSource(
			final String name,
			final String rawFile,
			final String rawDataset,
			final int[] rawCellSize,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		final RandomAccessibleInterval< T > data = H5Utils.open( HDF5Factory.openForReading( rawFile ), rawDataset, rawCellSize );
		final T t = Util.getTypeFromInterval( data );
		@SuppressWarnings( "unchecked" )
		final V v = ( V ) VolatileTypeMatcher.getVolatileTypeForType( t );

		final AffineTransform3D sourceTransform = new AffineTransform3D();
		sourceTransform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleIntervalDataSource< T, V > source =
				new RandomAccessibleIntervalDataSource< T, V >(
						new RandomAccessibleInterval[] { data },
						new RandomAccessibleInterval[] { VolatileViews.wrapAsVolatile( data, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) ) },
						new AffineTransform3D[] { sourceTransform },
						interpolation -> new NearestNeighborInterpolatorFactory<>(),
						interpolation -> new NearestNeighborInterpolatorFactory<>(),
						t::createVariable,
						v::createVariable,
						name );
		return new LabelDataSourceFromDelegates<>( source, assignment );
	}

	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createN5LabelSource(
			final String name,
			final N5Reader n5,
			final String dataset,
			final AffineGet sourceTransform,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		return new LabelDataSourceFromDelegates< T, V >(
				DataSource.createN5Source( name, n5, dataset, sourceTransform, sharedQueue, priority ),
				assignment );
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
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createN5LabelSource(
			final String name,
			final N5Reader n5,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		return new LabelDataSourceFromDelegates< T, V >(
				DataSource.createN5Source( name, n5, dataset, resolution, offset, sharedQueue, priority ),
				assignment );
	}

	/**
	 * Create a primitive single scale level source without visualization
	 * conversion from an N5 dataset.
	 *
	 * @param name
	 * @param n5
	 * @param rawDataset
	 * @param resolution
	 * @param sharedQueue
	 * @param priority
	 * @param typeSupplier
	 * @param volatileTypeSupplier
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createN5LabelSource(
			final String name,
			final N5Reader n5,
			final String dataset,
			final double[] resolution,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		return createN5LabelSource( name, n5, dataset, resolution, new double[ resolution.length ], sharedQueue, priority, assignment );
	}
}
