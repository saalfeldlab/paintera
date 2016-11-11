package bdv.img.h5;

import java.io.IOException;

import bdv.ViewerSetupImgLoader;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CachedCellImg;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

/**
 * {@link ViewerSetupImgLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class H5UnsignedShortSetupImageLoader
	extends AbstractH5SetupImageLoader< UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray >
{
	public H5UnsignedShortSetupImageLoader(
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
				new UnsignedShortType(),
				new VolatileUnsignedShortType(),
				new H5ShortArrayLoader( reader, dataset ) );
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< UnsignedShortType, VolatileShortArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final UnsignedShortType linkedType = new UnsignedShortType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedShortType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileUnsignedShortType, VolatileShortArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileUnsignedShortType linkedType = new VolatileUnsignedShortType( img );
		img.setLinkedType( linkedType );
		return img;
	}
}
