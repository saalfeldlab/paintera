package bdv.img.h5;

import java.io.IOException;

import bdv.img.cache.VolatileGlobalCellCache;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

/**
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class H5UnsignedShortSetupImageLoader
		extends AbstractH5SetupImageLoader< UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray >
{
	public H5UnsignedShortSetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] cellDimension,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		super(
				reader,
				dataset,
				setupId,
				cellDimension,
				new UnsignedShortType(),
				new VolatileUnsignedShortType(),
				new H5ShortArrayLoader( reader, dataset ),
				cache );
	}

	public H5UnsignedShortSetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] cellDimension,
			final double[] resolution,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		super(
				reader,
				dataset,
				setupId,
				cellDimension,
				resolution,
				new double[ 3 ],
				new UnsignedShortType(),
				new VolatileUnsignedShortType(),
				new H5ShortArrayLoader( reader, dataset ),
				cache );
	}
}
