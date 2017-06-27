package bdv.img.dvid;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;

/**
 * {@link CacheArrayLoader} for
 * <a href= "http://emdata.janelia.org/api/help/grayscale8">DVID's grayscale8 type</a>.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class Uint8blkVolatileArrayLoader implements CacheArrayLoader< VolatileByteArray >
{
	private final String apiUrl;
	private final String nodeId;
	private final String dataInstanceId;

	public Uint8blkVolatileArrayLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int[] blockDimensions )
	{
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
			int off = 0, l = 0;
			do
			{
				l = in.read( data, off, data.length - off );
				off += l;
			}
			while ( l > 0 && off < data.length );

			in.close();
	}

	private String makeUrl(
			final long[] min,
			final int[] dimensions )
	{
		final StringBuffer buf = new StringBuffer( apiUrl );

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
//			System.out.println( urlString + " " + data.length );
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
}
