package bdv.img.knossos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import bdv.img.cache.CacheArrayLoader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;

public class KnossosUnsignedByteVolatileArrayLoader implements CacheArrayLoader< VolatileByteArray >
{
	final private String urlFormat;

	public KnossosUnsignedByteVolatileArrayLoader( final String baseUrl, final String urlFormat, final String experiment, final String format )
	{
		this.urlFormat = baseUrl + urlFormat.replace( "%5$s", experiment );
	}

	@Override
	public int getBytesPerElement()
	{
		return 1;
	}

	@Override
	public VolatileByteArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		/* ignore timepoint, setup and dimensions that are constant */
		try
		{
			return tryLoadArray( level, min );
		}
		catch ( final OutOfMemoryError e )
		{
			System.gc();
			return tryLoadArray( level, min );
		}
	}

	public VolatileByteArray tryLoadArray(
			final int level,
			final long[] min ) throws InterruptedException
	{
		final int mag = 1 << level;

		final String url = String.format(
				urlFormat,
				mag,
				min[ 0 ] / 128,
				min[ 1 ] / 128,
				min[ 2 ] / 128 );

		System.out.println( url );

		byte[] data;

		try
		{
			final URL file = new URL( url );
			final InputStream in = file.openStream();
			final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			final byte[] chunk = new byte[ 4096 ];
			int l;
			for ( l = in.read( chunk ); l > 0; l = in.read( chunk ) )
			    byteStream.write( chunk, 0, l );

			data = byteStream.toByteArray();
			byteStream.close();
		}
		catch ( final IOException e )
		{
			data = new byte[ 128 * 128 * 128 ];
			System.out.println( "failed loading x=" + min[ 0 ] + " y=" + min[ 1 ] + " z=" + min[ 2 ] + " url(" + url.toString() + ")" );
		}

		return new VolatileByteArray( data, true );
	}
}
