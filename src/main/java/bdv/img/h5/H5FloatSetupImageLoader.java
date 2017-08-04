package bdv.img.h5;

import java.io.IOException;

import bdv.ViewerImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;

/**
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class H5FloatSetupImageLoader
		extends AbstractH5SetupImageLoader< FloatType, VolatileFloatType, VolatileFloatArray >
		implements ViewerImgLoader
{
	public H5FloatSetupImageLoader(
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
				new FloatType(),
				new VolatileFloatType(),
				new H5FloatArrayLoader( reader, dataset ),
				cache );
	}

	public H5FloatSetupImageLoader(
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
				new FloatType(),
				new VolatileFloatType(),
				new H5FloatArrayLoader( reader, dataset ),
				cache );
	}
}
