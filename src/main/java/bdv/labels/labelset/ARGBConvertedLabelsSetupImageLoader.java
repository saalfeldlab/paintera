package bdv.labels.labelset;

import java.io.IOException;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Fraction;
import bdv.AbstractViewerSetupImgLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class ARGBConvertedLabelsSetupImageLoader
	extends AbstractViewerSetupImgLoader< ARGBType, VolatileARGBType >
{
	private VolatileGlobalCellCache cache;

	private final ARGBConvertedLabelsArrayLoader loader;

	private final int setupId;

	private final DvidLabels64MultisetSetupImageLoader multisetImageLoader;

	/**
	 * http://hackathon.janelia.org/api/help/grayscale8
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "bodies"
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public ARGBConvertedLabelsSetupImageLoader(
			final DvidLabels64MultisetSetupImageLoader multisetImageLoader,
			final int setupId )
	{
		super( new ARGBType(), new VolatileARGBType() );
		this.setupId = setupId;
		this.multisetImageLoader = multisetImageLoader;
		loader = new ARGBConvertedLabelsArrayLoader( new MultisetSource( multisetImageLoader ) );
	}

	@Override
	public RandomAccessibleInterval< ARGBType > getImage( final int timepointId, final int level )
	{
		final CachedCellImg< ARGBType, VolatileIntArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final ARGBType linkedType = new ARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileARGBType > getVolatileImage( final int timepointId, final int level )
	{
		final CachedCellImg< VolatileARGBType, VolatileIntArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileARGBType linkedType = new VolatileARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileIntArray > prepareCachedImage( final int timepointId,  final int setupId, final int level, final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final long[] dimensions = multisetImageLoader.getDimensions();
		final int[] blockDimensions = multisetImageLoader.getBlockDimensions();
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

	static class MultisetSource
	{
		private final RandomAccessibleInterval< SuperVoxelMultisetType >[] currentSources;

		private final DvidLabels64MultisetSetupImageLoader multisetImageLoader;

		private int currentTimePointIndex;

		@SuppressWarnings( "unchecked" )
		public MultisetSource( final DvidLabels64MultisetSetupImageLoader multisetImageLoader )
		{
			this.multisetImageLoader = multisetImageLoader;
			final int numMipmapLevels = multisetImageLoader.numMipmapLevels();
			currentSources = new RandomAccessibleInterval[ numMipmapLevels ];
			currentTimePointIndex = -1;
		}

		private synchronized void loadTimepoint( final int timepointIndex )
		{
			currentTimePointIndex = timepointIndex;
			for ( int level = 0; level < currentSources.length; level++ )
				currentSources[ level ] = multisetImageLoader.getImage( timepointIndex, level );
		}

		public RandomAccessibleInterval< SuperVoxelMultisetType > getSource( final int t, final int level )
		{
			if ( t != currentTimePointIndex )
				loadTimepoint( t );
			return currentSources[ level ];
		}
	};
}
