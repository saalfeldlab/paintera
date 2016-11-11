package bdv.img.h5;

import java.io.IOException;

import bdv.ViewerSetupImgLoader;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CachedCellImg;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;

/**
 * {@link ViewerSetupImgLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class H5UnsignedByteSetupImageLoader
	extends AbstractH5SetupImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray >
{
	public H5UnsignedByteSetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final double[] resolution ) throws IOException
	{
		super(
				reader,
				dataset,
				setupId,
				blockDimension,
				resolution,
				new UnsignedByteType(),
				new VolatileUnsignedByteType(),
				new H5ByteArrayLoader( reader, dataset ) );
	}

	public H5UnsignedByteSetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension ) throws IOException
	{
		super(
				reader,
				dataset,
				setupId,
				blockDimension,
				new UnsignedByteType(),
				new VolatileUnsignedByteType(),
				new H5ByteArrayLoader( reader, dataset ) );
	}

	@Override
	public RandomAccessibleInterval< UnsignedByteType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< UnsignedByteType, VolatileByteArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final UnsignedByteType linkedType = new UnsignedByteType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedByteType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileUnsignedByteType, VolatileByteArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileUnsignedByteType linkedType = new VolatileUnsignedByteType( img );
		img.setLinkedType( linkedType );
		return img;
	}
}
