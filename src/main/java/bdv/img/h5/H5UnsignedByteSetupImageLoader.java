package bdv.img.h5;

import java.io.IOException;

import bdv.img.cache.VolatileGlobalCellCache;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;

/**
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class H5UnsignedByteSetupImageLoader
		extends AbstractH5SetupImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray >
{
	public H5UnsignedByteSetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final double[] resolution,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		super(
				reader,
				dataset,
				setupId,
				blockDimension,
				resolution,
				readOffset( reader, dataset ),
				new UnsignedByteType(),
				new VolatileUnsignedByteType(),
				new H5ByteArrayLoader( reader, dataset ),
				cache );
	}

	public H5UnsignedByteSetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		super(
				reader,
				dataset,
				setupId,
				blockDimension,
				new UnsignedByteType(),
				new VolatileUnsignedByteType(),
				new H5ByteArrayLoader( reader, dataset ),
				cache );
	}
}
