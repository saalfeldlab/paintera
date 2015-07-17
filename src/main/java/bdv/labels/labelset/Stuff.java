package bdv.labels.labelset;

import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;

public class Stuff
{
	VolatileGlobalCellCache cache;
	CacheArrayLoader<VolatileSuperVoxelMultisetArray> loader;
	long[] dimensions;
	int[] blockDimensions;
	protected < T extends NativeType< T > > CachedCellImg< T, VolatileSuperVoxelMultisetArray > prepareCachedImage(
			final int timepointId,
			final int setupId,
			final int level,
			final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final VolatileCellCache< VolatileSuperVoxelMultisetArray > c = cache.new VolatileCellCache< VolatileSuperVoxelMultisetArray >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< VolatileSuperVoxelMultisetArray > cells = new VolatileImgCells< VolatileSuperVoxelMultisetArray >( c, new Fraction(), dimensions, blockDimensions );
		final CachedCellImg< T, VolatileSuperVoxelMultisetArray > img = new CachedCellImg< T, VolatileSuperVoxelMultisetArray >( cells );
		return img;
	}
}
