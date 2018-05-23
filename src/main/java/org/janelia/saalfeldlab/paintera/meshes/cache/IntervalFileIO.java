package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import tmp.net.imglib2.cache.FileIO;

public class IntervalFileIO implements FileIO< Interval[] >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Override
	public Interval[] fromFile( final String filename ) throws Exception
	{
		try ( final FileInputStream filestream = new FileInputStream( new File( filename ) ) )
		{
			final DataInputStream dstream = new DataInputStream( filestream );
			final int numIntervals = dstream.readInt();
			final long[] min = new long[ 3 ];
			final long[] max = new long[ 3 ];
			final Interval[] intervals = new Interval[ numIntervals ];
			for ( int n = 0; n < numIntervals; ++n )
			{
				min[ 0 ] = dstream.readLong();
				min[ 1 ] = dstream.readLong();
				min[ 2 ] = dstream.readLong();
				min[ 0 ] = dstream.readLong();
				max[ 1 ] = dstream.readLong();
				max[ 2 ] = dstream.readLong();
				intervals[ n ] = new FinalInterval( min, max );
			}
			return intervals;
		}
	}

	@Override
	public void toFile( final String filename, final Interval[] value )
	{
		if ( value == null ) {
			return;
		}
		LOG.warn( "Writing {} to file {}", value, filename );
		final byte[] data = new byte[ 1 * Integer.BYTES + 6 * value.length * Long.BYTES ];
		LOG.warn( "data.length={} value.length={}", data.length, value.length );
		final ByteBuffer bb = ByteBuffer.wrap( data );
		bb.putInt( value.length );
		for ( final Interval interval : value )
		{
			bb.putLong( interval.min( 0 ) );
			bb.putLong( interval.min( 1 ) );
			bb.putLong( interval.min( 2 ) );
			bb.putLong( interval.max( 0 ) );
			bb.putLong( interval.max( 1 ) );
			bb.putLong( interval.max( 2 ) );
		}
		try
		{
			LOG.warn( "Attempting to write to file {}", filename );
			Files.write( data, new File( filename ) );
			LOG.warn( "Successfully wrote to file {}", filename );
		}
		catch ( final IOException e )
		{
			LOG.warn( "Unable to write: {}", e );
		}
	}

}
