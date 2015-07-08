package bdv.img.dvid;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import bdv.img.cache.CacheArrayLoader;
import bdv.util.ColorStream;

public class DvidLabels64VolatileArrayLoader implements CacheArrayLoader< VolatileIntArray >
{
	private VolatileIntArray theEmptyArray;

	private final String apiUrl;
	private final String nodeId;
	private final String dataInstanceId;

	public DvidLabels64VolatileArrayLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int[] blockDimensions )
	{
		theEmptyArray = new VolatileIntArray( 1, false );
		this.apiUrl = apiUrl;
		this.nodeId = nodeId;
		this.dataInstanceId = dataInstanceId;
	}

	@Override
	public int getBytesPerElement()
	{
		return 1;
	}
	
	static private void readBlock(
			final String urlString,
			final int[] data ) throws IOException
	{
		final byte[] bytes = new byte[ data.length * 8 ];
		final URL url = new URL( urlString );
		final InputStream in = url.openStream();
		final byte[] header = new byte[1];
		in.read( header, 0, 1 );
		if ( header[ 0 ] == 0 )
			return;
		
		in.skip( 3 );
		int off = 0;
		for (
				int l = in.read( bytes, off, bytes.length );
				l > 0 || off + l < bytes.length;
				off += l, l = in.read( bytes, off, bytes.length - off ) );
		in.close();
		
		for ( int i = 0, j = -1; i < data.length; ++i )
		{
			final long index =
					( long )bytes[ ++j ] |
					( ( long )bytes[ ++j ] << 8 ) |
					( ( long )bytes[ ++j ] << 16 ) |
					( ( long )bytes[ ++j ] << 24 ) |
					( ( long )bytes[ ++j ] << 32 ) |
					( ( long )bytes[ ++j ] << 40 ) |
					( ( long )bytes[ ++j ] << 48 ) |
					( ( long )bytes[ ++j ] << 56 );
			data[ i ] = ColorStream.get( index );
		}
	}
	
	private String makeUrl(
			final long[] min,
			final int[] dimensions )
	{
		final StringBuffer buf = new StringBuffer( apiUrl );
		
		// <api URL>/node/3f8c/mymultiscale2d/tile/xy/0/10_10_20
		
//		buf.append( "/node/" );
//		buf.append( nodeId );
//		buf.append( "/" );
//		buf.append( dataInstanceId );
//		buf.append( "/raw/0_1_2/" );
//		buf.append( dimensions[ 0 ] );
//		buf.append( "_" );
//		buf.append( dimensions[ 1 ] );
//		buf.append( "_" );
//		buf.append( dimensions[ 2 ] );
//		buf.append( "/" );
//		buf.append( min[ 0 ] );
//		buf.append( "_" );
//		buf.append( min[ 1 ] );
//		buf.append( "_" );
//		buf.append( min[ 2 ] );
		
		buf.append( "/node/" );
		buf.append( nodeId );
		buf.append( "/" );
		buf.append( dataInstanceId );
		buf.append( "/blocks/" );
		buf.append( min[ 0 ] / dimensions[ 0 ] );
		buf.append( "_" );
		buf.append( min[ 1 ] / dimensions[ 1 ] );
		buf.append( "_" );
		buf.append( min[ 2 ] / dimensions[ 2 ] );
		buf.append( "/1" );
		
		return buf.toString();
	}
	

	@Override
	public VolatileIntArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		final int[] data = new int[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];

		try
		{	
			final String urlString = makeUrl( min, dimensions );
			System.out.println( urlString + " " + data.length );
			readBlock( urlString, data );
		}
		catch (final IOException e)
		{
			System.out.println(
					"failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );
		}
		return new VolatileIntArray( data, true );
	}

	@Override
	public VolatileIntArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileIntArray( numEntities, false );
		return theEmptyArray;
	}
}
