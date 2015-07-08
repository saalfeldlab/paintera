package bdv.img.dvid;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import bdv.img.cache.CacheArrayLoader;

public class DvidGrayscale8VolatileArrayLoader implements CacheArrayLoader< VolatileByteArray >
{
	private VolatileByteArray theEmptyArray;

	private final String apiUrl;
	private final String nodeId;
	private final String dataInstanceId;

	public DvidGrayscale8VolatileArrayLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int[] blockDimensions )
	{
		theEmptyArray = new VolatileByteArray( 1, false );
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
			final byte[] data ) throws IOException
	{
			final URL url = new URL( urlString );
			final InputStream in = url.openStream();
			in.skip( 4 );
			int off = 0;
			for (
					int l = in.read( data, off, data.length );
					l > 0 || off + l < data.length;
					off += l, l = in.read( data, off, data.length - off ) );
			in.close();
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
	public VolatileByteArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		final byte[] data = new byte[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];

		try
		{	
			final String urlString = makeUrl( min, dimensions );
			readBlock( urlString, data );
			System.out.println( urlString + " " + data.length );
		}
		catch (final IOException e)
		{
			System.out.println(
					"failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );
		}
		return new VolatileByteArray( data, true );
	}

	@Override
	public VolatileByteArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileByteArray( numEntities, false );
		return theEmptyArray;
	}
}
