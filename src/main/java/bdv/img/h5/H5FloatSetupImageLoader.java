package bdv.img.h5;

import java.io.IOException;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CachedCellImg;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;

/**
 * {@link ViewerSetupImgLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class H5FloatSetupImageLoader
	extends AbstractH5SetupImageLoader< FloatType, VolatileFloatType, VolatileFloatArray >
	implements ViewerImgLoader
{
	public H5FloatSetupImageLoader(
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
				new FloatType(),
				new VolatileFloatType(),
				new H5FloatArrayLoader( reader, dataset ) );
	}

	@Override
	public RandomAccessibleInterval< FloatType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< FloatType, VolatileFloatArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final FloatType linkedType = new FloatType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileFloatType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileFloatType, VolatileFloatArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileFloatType linkedType = new VolatileFloatType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cache;
	}
}
