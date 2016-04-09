package bdv.img.h5;

import java.io.IOException;

import bdv.ViewerSetupImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.labels.labelset.VolatileLabelMultisetType;
import ch.systemsx.cisd.hdf5.HDF5ObjectType;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import net.imglib2.RandomAccessibleInterval;

/**
 * {@link ViewerSetupImgLoader} for labels stored in simple HDF5 files
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class H5LabelMultisetSetupImageLoader
	extends AbstractH5SetupImageLoader< LabelMultisetType, VolatileLabelMultisetType, VolatileLabelMultisetArray >
{
	static private CacheArrayLoader<VolatileLabelMultisetArray> typedLoader(
			final IHDF5Reader reader,
			final String dataset )
	{
		final HDF5ObjectType type = reader.object().getObjectType( dataset );
		System.out.println( type.toString() );
		return new H5IntLabelMultisetArrayLoader( reader, dataset );
	}

	public H5LabelMultisetSetupImageLoader(
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
				new LabelMultisetType(),
				new VolatileLabelMultisetType(),
				typedLoader( reader, dataset ) );
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final LabelMultisetType linkedType = new LabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileLabelMultisetType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		boolean blocking = false;
		if ( hints.length > 0 )
			for ( final ImgLoaderHint hint : hints )
				if ( hint == ImgLoaderHints.LOAD_COMPLETELY )
				{
					blocking = true;
					break;
				}
		final CachedCellImg< VolatileLabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( timepointId, setupId, level, blocking ? LoadingStrategy.BLOCKING : LoadingStrategy.VOLATILE );
		final VolatileLabelMultisetType linkedType = new VolatileLabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}
}
