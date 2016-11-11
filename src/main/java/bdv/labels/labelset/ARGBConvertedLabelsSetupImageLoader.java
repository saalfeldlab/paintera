package bdv.labels.labelset;

import bdv.AbstractViewerSetupImgLoader;
import bdv.cache.CacheHints;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.dvid.LabelblkMultisetSetupImageLoader;
import bdv.img.dvid.LabelblkMultisetSetupImageLoader.MultisetSource;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Fraction;

public class ARGBConvertedLabelsSetupImageLoader
	extends AbstractViewerSetupImgLoader< ARGBType, VolatileARGBType >
{
	private VolatileGlobalCellCache cache;

	private final ARGBConvertedLabelsArrayLoader loader;

	private final int setupId;

	private final LabelblkMultisetSetupImageLoader multisetImageLoader;

	public ARGBConvertedLabelsSetupImageLoader(
			final int setupId,
			final LabelblkMultisetSetupImageLoader multisetImageLoader )
	{
		super( new ARGBType(), new VolatileARGBType() );
		this.setupId = setupId;
		this.multisetImageLoader = multisetImageLoader;
		loader = new ARGBConvertedLabelsArrayLoader( new MultisetSource( multisetImageLoader ) );
	}

	@Override
	public RandomAccessibleInterval< ARGBType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< ARGBType, VolatileIntArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final ARGBType linkedType = new ARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileARGBType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileARGBType, VolatileIntArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileARGBType linkedType = new VolatileARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileIntArray > prepareCachedImage( final int timepointId, final int setupId, final int level, final LoadingStrategy loadingStrategy )
	{
		final int priority = numMipmapLevels() - level - 1;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final long[] dimensions = multisetImageLoader.getDimensions( level );
		final int[] blockDimensions = multisetImageLoader.getBlockDimensions( level );
		final VolatileCellCache< VolatileIntArray > c = cache.new VolatileCellCache< VolatileIntArray >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< VolatileIntArray > cells = new VolatileImgCells< VolatileIntArray >( c, new Fraction(), dimensions, blockDimensions );
		final CachedCellImg< T, VolatileIntArray > img = new CachedCellImg< T, VolatileIntArray >( cells );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return multisetImageLoader.getMipmapResolutions();
	}

	@Override
	public int numMipmapLevels()
	{
		return multisetImageLoader.numMipmapLevels();
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return multisetImageLoader.getMipmapTransforms();
	}

	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	public int getSetupId()
	{
		return setupId;
	}
}
