package bdv.labels.labelset;

import bdv.AbstractViewerSetupImgLoader;
import bdv.cache.CacheHints;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.knossos.AbstractKnossosImageLoader;
import bdv.img.knossos.AbstractKnossosImageLoader.KnossosConfig;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

/**
 * Loader for uint8 volumes stored in the KNOSSOS format
 * 
 * http://knossostool.org/
 * 
 * Blocks of 128^3 voxels (fill with zero if smaller) uint64 voxels in network
 * byte order from left top front to right bottom rear,
 * index = z 128<sup>2</sup> + y 128 + x
 * naming convention
 * 
 * x%1$d/y%2$d/z%3$d/%4$s_x%1$d_y%2$d_z%3$d.raw
 * with arguments
 * 
 * (1) x / 128
 * (2) y / 128
 * (3) z / 128
 * (4) name
 * 
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class KnossosLabelMultisetSetupImageLoader
	extends AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType >
{
	final protected KnossosConfig config;
	
	final protected long[] dimension;
	
	final protected double[] resolution;

	final protected AffineTransform3D mipmapTransform;

	protected VolatileGlobalCellCache cache;

	private final KnossosVolatileLabelsMultisetArrayLoader loader;

	private final int setupId;

	public KnossosLabelMultisetSetupImageLoader(
			final int setupId,
			final String configUrl,
			final String urlFormat )
	{
		super( LabelMultisetType.type, VolatileLabelMultisetType.type );
		this.setupId = setupId;
		
		config = AbstractKnossosImageLoader.tryFetchConfig( configUrl, 20 );

		dimension = new long[] { config.width, config.height, config.depth };
		
		resolution = new double[]{ config.scaleX, config.scaleY, config.scaleZ };
		
		mipmapTransform = new AffineTransform3D();

		mipmapTransform.set( config.scaleX, 0, 0 );
		mipmapTransform.set( config.scaleY, 1, 1 );
		mipmapTransform.set( config.scaleZ, 2, 2 );
		
		loader = new KnossosVolatileLabelsMultisetArrayLoader(
				config.baseUrl,
				urlFormat,
				config.experimentName,
				config.format );
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( LoadingStrategy.BLOCKING );
		final LabelMultisetType linkedType = new LabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileLabelMultisetType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileLabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( LoadingStrategy.BLOCKING );
		final VolatileLabelMultisetType linkedType = new VolatileLabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return new double[][]{ resolution };
	}

	@Override
	public int numMipmapLevels()
	{
		return 1;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileLabelMultisetArray > prepareCachedImage(
			final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final VolatileCellCache< VolatileLabelMultisetArray > c =
				cache.new VolatileCellCache< VolatileLabelMultisetArray >(
						0,
						setupId,
						0,
						cacheHints,
						loader );
		final VolatileImgCells< VolatileLabelMultisetArray > cells = new VolatileImgCells< VolatileLabelMultisetArray >( c, new Fraction(), dimension, new int[]{ 128, 128, 128 });
		final CachedCellImg< T, VolatileLabelMultisetArray > img = new CachedCellImg< T, VolatileLabelMultisetArray >( cells );
		return img;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return new AffineTransform3D[]{ mipmapTransform };
	}

	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	static public class MultisetSource
	{
		private final RandomAccessibleInterval< LabelMultisetType >[] currentSources;

		private final KnossosLabelMultisetSetupImageLoader multisetImageLoader;

		private int currentTimePointIndex;

		@SuppressWarnings( "unchecked" )
		public MultisetSource( final KnossosLabelMultisetSetupImageLoader multisetImageLoader )
		{
			this.multisetImageLoader = multisetImageLoader;
			final int numMipmapLevels = multisetImageLoader.numMipmapLevels();
			currentSources = new RandomAccessibleInterval[ numMipmapLevels ];
			currentTimePointIndex = -1;
		}

		private void loadTimepoint( final int timepointIndex )
		{
			currentTimePointIndex = timepointIndex;
			for ( int level = 0; level < currentSources.length; level++ )
				currentSources[ level ] = multisetImageLoader.getImage( timepointIndex, level );
		}

		public synchronized RandomAccessibleInterval< LabelMultisetType > getSource( final int t, final int level )
		{
			if ( t != currentTimePointIndex )
				loadTimepoint( t );
			return currentSources[ level ];
		}
	}

	public int getSetupId()
	{
		return setupId;
	};
}
