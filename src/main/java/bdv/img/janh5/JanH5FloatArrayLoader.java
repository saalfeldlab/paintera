package bdv.img.janh5;

import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.hdf5.IHDF5FloatReader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class JanH5FloatArrayLoader implements CacheArrayLoader< VolatileFloatArray >
{
	private VolatileFloatArray theEmptyArray;

	private IHDF5FloatReader reader;
	private String dataset;

	public JanH5FloatArrayLoader(
			final IHDF5Reader reader,
			final String dataset )
	{
		theEmptyArray = new VolatileFloatArray( 1, false );
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
				"/raw",
				new int[]{ dimensions[ 2 ], dimensions[ 1 ], dimensions[ 0 ] },
				new long[]{ min[ 2 ], min[ 1 ], min[ 0 ] } );

		data = slice.getAsFlatArray();

		if ( data == null )
		{
			System.out.println(
					"JanH5 array loader failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );

			data = new float[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		}

		return new VolatileFloatArray( data, true );
	}

	/**
	 * Reuses the existing empty array if it already has the desired size.
	 */
	@Override
	public VolatileFloatArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileFloatArray( numEntities, false );
		return theEmptyArray;
	}
}
