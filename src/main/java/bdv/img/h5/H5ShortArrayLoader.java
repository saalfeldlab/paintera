package bdv.img.h5;

import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5ShortReader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class H5ShortArrayLoader implements CacheArrayLoader< VolatileShortArray >
{
	final private IHDF5ShortReader reader;

	final private String dataset;

	public H5ShortArrayLoader(
			final IHDF5Reader reader,
			final String dataset )
	{
		this.reader = reader.int16();
		this.dataset = dataset;
	}

	@Override
	public int getBytesPerElement()
	{
		return 2;
	}


	@Override
	public VolatileShortArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		short[] data = null;
		final MDShortArray slice = reader.readMDArrayBlockWithOffset(
				dataset,
				new int[]{ dimensions[ 2 ], dimensions[ 1 ], dimensions[ 0 ] },
				new long[]{ min[ 2 ], min[ 1 ], min[ 0 ] } );

		data = slice.getAsFlatArray();

		if ( data == null )
		{
			System.out.println(
					"H5 short array loader failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );

			data = new short[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		}

		return new VolatileShortArray( data, true );
	}
}
