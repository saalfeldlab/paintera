package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.meta.Meta;
import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.ValueTriple;

public interface N5Meta extends Meta
{
	public static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public N5Reader reader() throws IOException;

	public N5Writer writer() throws IOException;

	public String dataset();

	public default < T extends NativeType< T > > RandomAccessibleInterval< T > open() throws IOException
	{
		return N5Utils.< T >open( reader(), dataset() );
	}

	public default boolean isMultiscale() throws IOException
	{
		return N5Helpers.isMultiScale( reader(), dataset() );
	}

	public default boolean isLabelMultisetType( final boolean isMultiscale ) throws IOException
	{
		return N5Helpers.isLabelMultisetType( reader(), dataset(), isMultiscale );
	}

	@Override
	public default < T extends NativeType< T >, V extends Volatile< T > & NativeType< V > > DataSource< T, V > asSource(
			final SharedQueue sharedQueue,
			final int priority,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > viewerInterpolation,
			final AffineTransform3D transform,
			final String name,
			final SourceState< ?, ? >... dependsOn ) throws SourceCreationFailed
	{
		try
		{
			final boolean isMultiscale = isMultiscale();
			final boolean isLabelMultisetType = isLabelMultisetType( isMultiscale );

			LOG.warn( "{}: Is label multiset type? {} -- Is multi scale? {}", name, isLabelMultisetType, isMultiscale );

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
					name );
			return source;
		}
		catch ( final IOException e )
		{
			throw new SourceCreationFailed( "IOException in N5 access: " + e.getMessage(), e );
		}

	}

}
