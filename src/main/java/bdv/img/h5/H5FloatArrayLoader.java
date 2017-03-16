package bdv.img.h5;

import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.hdf5.IHDF5FloatReader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class H5FloatArrayLoader implements CacheArrayLoader< VolatileFloatArray >
{
	final private IHDF5FloatReader reader;

	final private String dataset;

	public H5FloatArrayLoader(
			final IHDF5Reader reader,
			final String dataset )
	{
		this.reader = reader.float32();
		this.dataset = dataset;
	}

	@Override
	public int getBytesPerElement()
	{
		return 4;
	}


	@Override
	public VolatileFloatArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		float[] data = null;
		final MDFloatArray slice = reader.readMDArrayBlockWithOffset(
				dataset,
				new int[]{ dimensions[ 2 ], dimensions[ 1 ], dimensions[ 0 ] },
				new long[]{ min[ 2 ], min[ 1 ], min[ 0 ] } );

		data = slice.getAsFlatArray();

		if ( data == null )
		{
			System.out.println(
					"H5 float array loader failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );

			data = new float[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		}

		return new VolatileFloatArray( data, true );
	}
}
