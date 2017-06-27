package bdv.img.h5;

import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.hdf5.IHDF5ByteReader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class H5ByteArrayLoader implements CacheArrayLoader< VolatileByteArray >
{
	final private IHDF5ByteReader reader;

	final private String dataset;

	public H5ByteArrayLoader(
			final IHDF5Reader reader,
			final String dataset )
	{
		this.reader = reader.uint8();
		this.dataset = dataset;
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
		byte[] data = null;
		final MDByteArray slice = reader.readMDArrayBlockWithOffset(
				dataset,
				new int[]{ dimensions[ 2 ], dimensions[ 1 ], dimensions[ 0 ] },
				new long[]{ min[ 2 ], min[ 1 ], min[ 0 ] } );

		data = slice.getAsFlatArray();

		if ( data == null )
		{
			System.out.println(
					"H5 byte array loader failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );

			data = new byte[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		}

		return new VolatileByteArray( data, true );
	}
}
