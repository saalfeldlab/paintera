package bdv.img.knossos;

import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.cache.CacheHints;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Fraction;
/**
 * Loader for uint8 volumes stored in the KNOSSOS format
 * 
 * http://knossostool.org/
 * 
 * Volumes are stored as 128<sup>3</sup> 8bit datacubes either
 * jpeg-compressed or raw in a nested file structure.
 * 
 * Files names follow a convention that is typically
 * <baseURL>/mag%d/x%04d/y%04d/z%04d/<experiment>_x%04d_y%04d_z%04d_mag%d.raw


x y z are %04d block coordinates
mag in {1, 2, 4, 8, 16, ...}



example

experiment name "070317_e1088";
scale x 22.0;
scale y 22.0;
scale z 30.0;
boundary x 2048;
boundary y 1792;
boundary z 2048;
magnification 1;



#optional

compression_ratio 1000; # means jpg
ftp_mode <server> <root_directory> <http_user> <http_passwd> 30000
                                                             timeout ?



 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 *
 */
public class KnossosUnsignedByteImageLoader extends AbstractKnossosImageLoader< UnsignedByteType, VolatileUnsignedByteType >
{
	private final KnossosUnsignedByteVolatileArrayLoader loader;

	public KnossosUnsignedByteImageLoader( final String configUrl, final String urlFormat )
	{
		super( new UnsignedByteType(), new VolatileUnsignedByteType(), configUrl, urlFormat );
		
		loader = new KnossosUnsignedByteVolatileArrayLoader(
				config.baseUrl,
				urlFormat,
				config.experimentName,
				config.format );
	}

	@Override
	public RandomAccessibleInterval< UnsignedByteType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< UnsignedByteType, VolatileByteArray > img = prepareCachedImage( timepointId, 0, level, LoadingStrategy.BLOCKING );
		final UnsignedByteType linkedType = new UnsignedByteType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedByteType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileUnsignedByteType, VolatileByteArray > img = prepareCachedImage( timepointId, 0, level, LoadingStrategy.VOLATILE );
		final VolatileUnsignedByteType linkedType = new VolatileUnsignedByteType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	/**
	 * (Almost) create a {@link CachedCellImg} backed by the cache. The created image
	 * needs a {@link NativeImg#setLinkedType(net.imglib2.type.Type) linked
	 * type} before it can be used. The type should be either {@link ARGBType}
	 * and {@link VolatileARGBType}.
	 */
	protected < T extends NativeType< T > > CachedCellImg< T, VolatileByteArray > prepareCachedImage( final int timepointId, final int setupId, final int level, final LoadingStrategy loadingStrategy )
	{
		final long[] dimensions = imageDimensions[ level ];

		final int priority = config.numScales - 1 - level;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< VolatileByteArray > c = cache.new VolatileCellCache< VolatileByteArray >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< VolatileByteArray > cells = new VolatileImgCells< VolatileByteArray >( c, new Fraction(), dimensions, new int[]{ 128, 128, 128 } );
		final CachedCellImg< T, VolatileByteArray > img = new CachedCellImg< T, VolatileByteArray >( cells );
		return img;
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cache;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}
}
