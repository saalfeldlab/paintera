package bdv.img.dvid;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import bdv.util.ColorStream;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;

/**
 * {@link CacheArrayLoader} for
 * <a href= "http://emdata.janelia.org/api/help/labels64">DVID's labels64 type</a>.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class LabelblkVolatileArrayLoader implements CacheArrayLoader< VolatileIntArray >
{
	private final String apiUrl;
	private final String nodeId;
	private final String dataInstanceId;
	private final int argbMask;

	public LabelblkVolatileArrayLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int[] blockDimensions,
			final int argbMask )
	{
		this.apiUrl = apiUrl;
		this.nodeId = nodeId;
		this.dataInstanceId = dataInstanceId;
		this.argbMask = argbMask;
	}

	public LabelblkVolatileArrayLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int[] blockDimensions )
	{
		this( apiUrl, nodeId, dataInstanceId, blockDimensions, 0xffffffff );;
	}

	@Override
	public int getBytesPerElement()
	{
		return 1;
	}

	private void readBlock(
			final String urlString,
			final int[] data ) throws IOException
	{
		final byte[] bytes = new byte[ data.length * 8 ];
		final URL url = new URL( urlString );
		final InputStream in = url.openStream();

		int off = 0, l = 0;
		do
		{
			l = in.read( bytes, off, bytes.length - off );
			off += l;
		}
		while ( l > 0 && off < bytes.length );

		in.close();

		for ( int i = 0, j = -1; i < data.length; ++i )
		{
			final long index =
					( 0xffl & bytes[ ++j ] ) |
					( ( 0xffl & bytes[ ++j ] ) << 8 ) |
					( ( 0xffl & bytes[ ++j ] ) << 16 ) |
					( ( 0xffl & bytes[ ++j ] ) << 24 ) |
					( ( 0xffl & bytes[ ++j ] ) << 32 ) |
					( ( 0xffl & bytes[ ++j ] ) << 40 ) |
					( ( 0xffl & bytes[ ++j ] ) << 48 ) |
					( ( 0xffl & bytes[ ++j ] ) << 56 );
			data[ i ] = ColorStream.get( index ) & argbMask;
		}
	}

	private String makeUrl(
			final long[] min,
			final int[] dimensions )
	{
		final StringBuffer buf = new StringBuffer( apiUrl );

		// <api URL>/node/3f8c/mymultiscale2d/tile/xy/0/10_10_20

		buf.append( "/node/" );
		buf.append( nodeId );
		buf.append( "/" );
		buf.append( dataInstanceId );

//		buf.append( "/blocks/" );
//		buf.append( min[ 0 ] / dimensions[ 0 ] );
//		buf.append( "_" );
//		buf.append( min[ 1 ] / dimensions[ 1 ] );
//		buf.append( "_" );
//		buf.append( min[ 2 ] / dimensions[ 2 ] );
//		buf.append( "/1" );

		buf.append( "/raw/0_1_2/" );
		buf.append( dimensions[ 0 ] );
		buf.append( "_" );
		buf.append( dimensions[ 1 ] );
		buf.append( "_" );
		buf.append( dimensions[ 2 ] );
		buf.append( "/" );
		buf.append( min[ 0 ] );
		buf.append( "_" );
		buf.append( min[ 1 ] );
		buf.append( "_" );
		buf.append( min[ 2 ] );

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
//			System.out.println( urlString + " " + data.length );
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
}
