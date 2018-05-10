package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.meta.RawMeta;
import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.ValueTriple;

public interface N5RawMeta< T extends NativeType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > > extends N5Meta< T >, RawMeta< T, V >
{
	public static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static < T extends NativeType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > > N5RawMeta< T, V >
	forReader( N5Reader n5, String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		if ( n5 instanceof N5FSReader )
			return new N5FSRawMeta<>( ( N5FSReader ) n5, dataset );

		if ( n5 instanceof N5HDF5Reader )
			return new N5HDF5RawMeta<>( ( N5HDF5Reader ) n5, dataset );

		return null;
	}

	@Override
	public default RawSourceState< T, V > asSource(
			final SharedQueue sharedQueue,
			final int priority,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > viewerInterpolation,
			final AffineTransform3D transform,
			final SourceState< ?, ? >... dependsOn ) throws SourceCreationFailed
	{
		try
		{
			final boolean isMultiscale = isMultiscale();
			final boolean isLabelMultisetType = isLabelMultisetType( isMultiscale );

			LOG.warn( "{}: Is label multiset type? {} -- Is multi scale? {}", dataset(), isLabelMultisetType, isMultiscale );

			final ValueTriple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > data;
			if ( isLabelMultisetType )
			{
				data = isMultiscale
						? ( ValueTriple ) N5Helpers.openLabelMultisetMultiscale( reader(), dataset(), transform, sharedQueue, priority )
						: ( ValueTriple ) N5Helpers.asArrayTriple( N5Helpers.openLabelMutliset( reader(), dataset(), transform, sharedQueue, priority ) );
			}
			else
			{
				data = isMultiscale
						? N5Helpers.openRawMultiscale( reader(), dataset(), transform, sharedQueue, priority )
						: N5Helpers.asArrayTriple( N5Helpers.openRaw( reader(), dataset(), transform, sharedQueue, priority ) );
			}

			final DataSource< T, V > source = new RandomAccessibleIntervalDataSource<>(
					data.getA(),
					data.getB(),
					data.getC(),
					dataInterpolation,
					viewerInterpolation,
					dataset() );
			return new RawSourceState< T, V >(
					source,
					new ARGBColorConverter.Imp1<>( 0, 255 ),
					new CompositeCopy<>(),
					source.getName(),
					this,
					dependsOn );
		}
		catch ( final IOException e )
		{
			throw new SourceCreationFailed( "IOException in N5 access: " + e.getMessage(), e );
		}

	}

	public static < T extends NumericType< T > > Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > defaultInterpolations()
	{
		return i -> i.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>();
	}

}
