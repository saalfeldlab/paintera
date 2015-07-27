package bdv.labels.labelset;

import java.io.IOException;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;
import bdv.AbstractViewerSetupImgLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.dvid.Labels64DataInstance;
import bdv.util.JsonHelper;
import bdv.util.MipmapTransforms;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class DvidLabels64MultisetSetupImageLoader
	extends AbstractViewerSetupImgLoader< SuperVoxelMultisetType, VolatileSuperVoxelMultisetType >
{
	private final double[][] resolutions;

	private final long[] dimensions;

	private final int[] blockDimensions;

	private final AffineTransform3D[] mipmapTransforms;

	private VolatileGlobalCellCache cache;

	private final VolatileSuperVoxelMultisetArrayLoader loader;

	private final DownscalingVolatileSuperVoxelMultisetArrayLoader downscaleLoader;

	private final int setupId;

	private final int numMipmapLevels;

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
	public DvidLabels64MultisetSetupImageLoader(
			final int setupId,
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( SuperVoxelMultisetType.type, VolatileSuperVoxelMultisetType.type );
		this.setupId = setupId;

		final Labels64DataInstance dataInstance =
				JsonHelper.fetch(
						apiUrl + "/node/" + nodeId + "/" + dataInstanceId + "/info",
						Labels64DataInstance.class );

		dimensions = new long[]{
				dataInstance.Extended.MaxPoint[ 0 ] - dataInstance.Extended.MinPoint[ 0 ],
				dataInstance.Extended.MaxPoint[ 1 ] - dataInstance.Extended.MinPoint[ 1 ],
				dataInstance.Extended.MaxPoint[ 2 ] - dataInstance.Extended.MinPoint[ 2 ] };

		resolutions = new double[][]{
				{ 1, 1, 1 },
				{ 2, 2, 2 },
				{ 4, 4, 4 },
				{ 8, 8, 8 }
			};
		mipmapTransforms = new AffineTransform3D[ resolutions.length ];
		for ( int level = 0; level < resolutions.length; level++ )
			mipmapTransforms[ level ] = MipmapTransforms.getMipmapTransformDefault( resolutions[ level ] );
		numMipmapLevels = resolutions.length;

		blockDimensions = dataInstance.Extended.BlockSize;

		loader = new VolatileSuperVoxelMultisetArrayLoader( apiUrl, nodeId, dataInstanceId, blockDimensions );
		downscaleLoader = new DownscalingVolatileSuperVoxelMultisetArrayLoader( new MultisetSource( this ) );
	}

	@Override
	public RandomAccessibleInterval< SuperVoxelMultisetType > getImage( final int timepointId, final int level )
	{
		final CachedCellImg< SuperVoxelMultisetType, VolatileSuperVoxelMultisetArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final SuperVoxelMultisetType linkedType = new SuperVoxelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileSuperVoxelMultisetType > getVolatileImage( final int timepointId, final int level )
	{
		final CachedCellImg< VolatileSuperVoxelMultisetType, VolatileSuperVoxelMultisetArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final VolatileSuperVoxelMultisetType linkedType = new VolatileSuperVoxelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return resolutions;
	}

	@Override
	public int numMipmapLevels()
	{
		return numMipmapLevels;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileSuperVoxelMultisetArray > prepareCachedImage(
			final int timepointId,
			final int setupId,
			final int level,
			final LoadingStrategy loadingStrategy )
	{
		final int priority = numMipmapLevels - level - 1;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final VolatileCellCache< VolatileSuperVoxelMultisetArray > c = cache.new VolatileCellCache< VolatileSuperVoxelMultisetArray >(
				timepointId, setupId, level, cacheHints, level == 0 ? loader : downscaleLoader );
		final VolatileImgCells< VolatileSuperVoxelMultisetArray > cells = new VolatileImgCells< VolatileSuperVoxelMultisetArray >( c, new Fraction(), dimensions, blockDimensions );
		final CachedCellImg< T, VolatileSuperVoxelMultisetArray > img = new CachedCellImg< T, VolatileSuperVoxelMultisetArray >( cells );
		return img;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}

	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	public long[] getDimensions()
	{
		return dimensions;
	}

	public int[] getBlockDimensions()
	{
		return blockDimensions;
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

		private void loadTimepoint( final int timepointIndex )
		{
			currentTimePointIndex = timepointIndex;
			for ( int level = 0; level < currentSources.length; level++ )
				currentSources[ level ] = multisetImageLoader.getImage( timepointIndex, level );
		}

		public synchronized RandomAccessibleInterval< SuperVoxelMultisetType > getSource( final int t, final int level )
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
