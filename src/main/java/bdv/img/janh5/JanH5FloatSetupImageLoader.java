package bdv.img.janh5;

import java.io.IOException;

import bdv.ViewerSetupImgLoader;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;

/**
 * {@link ViewerSetupImgLoader} for
 * Jan Funke's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class JanH5FloatSetupImageLoader
	extends AbstractJanH5SetupImageLoader< FloatType, VolatileFloatType, VolatileFloatArray >
{
	public JanH5FloatSetupImageLoader(
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
				new JanH5FloatArrayLoader( reader, dataset ) );
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
}
