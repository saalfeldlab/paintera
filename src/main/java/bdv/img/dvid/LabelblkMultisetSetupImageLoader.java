package bdv.img.dvid;

import java.io.IOException;
import java.util.Arrays;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.AbstractViewerSetupImgLoader;
import bdv.cache.CacheHints;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.JsonHelper;
import bdv.util.MipmapTransforms;
import bdv.util.dvid.DatasetKeyValue;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

public class LabelblkMultisetSetupImageLoader
	extends AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType >
{
	final protected double[][] resolutions;

	final protected long[][] dimensions;

	final protected int[][] blockDimensions;

	final protected AffineTransform3D[] mipmapTransforms;

	protected VolatileGlobalCellCache cache;

	private final CacheArrayLoader< VolatileLabelMultisetArray >[] loaders;

	private final int setupId;

	/**
	 * http://emrecon100.janelia.priv/api/help/labels64
	 *
	 * @param apiUrl e.g. "http://emrecon100.janelia.priv/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "bodies"
	 * @param resolutions
	 * @param dvidStores Array of {@link DatasetKeyValue} to manage
	 * load/write of {@link VolatileLabelMultisetArray} from dvid store.
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	@SuppressWarnings( "unchecked" )
	public LabelblkMultisetSetupImageLoader(
			final int setupId,
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final double[][] resolutions,
			final DatasetKeyValue[] dvidStores ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( LabelMultisetType.type, VolatileLabelMultisetType.type );
		this.setupId = setupId;

		final LabelblkDataInstance dataInstance =
				JsonHelper.fetch(
						apiUrl + "/node/" + nodeId + "/" + dataInstanceId + "/info",
						LabelblkDataInstance.class );

		this.dimensions = new long[ resolutions.length ][];
		this.resolutions = new double[ resolutions.length ][];
		for ( int i =0; i < resolutions.length; ++i )
		{
			this.resolutions[ i ] = Arrays.copyOf( resolutions[ i ], resolutions[ i ].length );
			this.dimensions[ i ] = new long[]{
					( long )Math.ceil( ( dataInstance.Extended.MaxPoint[ 0 ] + 1 - dataInstance.Extended.MinPoint[ 0 ] ) * resolutions[ i ][ 0 ] ),
					( long )Math.ceil( ( dataInstance.Extended.MaxPoint[ 1 ] + 1 - dataInstance.Extended.MinPoint[ 1 ] ) * resolutions[ i ][ 1 ] ),
					( long )Math.ceil( ( dataInstance.Extended.MaxPoint[ 2 ] + 1 - dataInstance.Extended.MinPoint[ 2 ] ) * resolutions[ i ][ 2 ] ) };
		}


		mipmapTransforms = new AffineTransform3D[ resolutions.length ];
		for ( int level = 0; level < resolutions.length; level++ )
			mipmapTransforms[ level ] = MipmapTransforms.getMipmapTransformDefault( resolutions[ level ] );

		final int numMipmapLevels = resolutions.length;

		blockDimensions = new int[ numMipmapLevels ][];
		loaders = new CacheArrayLoader[ numMipmapLevels ];

		/* first loader is a labels64 source */
		blockDimensions[ 0 ] = dataInstance.Extended.BlockSize;
		loaders[ 0 ] = new LabelblkMultisetVolatileArrayLoader( apiUrl, nodeId, dataInstanceId, blockDimensions[ 0 ] );

		/* subsequent loaders are key value stores */
		for ( int i = 0; i < dvidStores.length; ++i ) {
			loaders[ i + 1 ] =
				new DvidLabelMultisetArrayLoader( dvidStores[ i ] );
			blockDimensions[ i + 1 ] = blockDimensions[ 0 ];
		}
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > img = prepareCachedImage( timepointId, level, LoadingStrategy.BLOCKING );
		final LabelMultisetType linkedType = new LabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileLabelMultisetType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileLabelMultisetType, VolatileLabelMultisetArray > img = prepareCachedImage( timepointId, level, LoadingStrategy.VOLATILE );
		final VolatileLabelMultisetType linkedType = new VolatileLabelMultisetType( img );
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
		return resolutions.length;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileLabelMultisetArray > prepareCachedImage(
			final int timepointId,
			final int level,
			final LoadingStrategy loadingStrategy )
	{
		final int priority = resolutions.length - level - 1;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final VolatileCellCache< VolatileLabelMultisetArray > c =
				cache.new VolatileCellCache< VolatileLabelMultisetArray >(
						timepointId,
						setupId,
						level,
						cacheHints,
						loaders[ level ] );
		final VolatileImgCells< VolatileLabelMultisetArray > cells = new VolatileImgCells< VolatileLabelMultisetArray >( c, new Fraction(), dimensions[ level ], blockDimensions[ level ] );
		final CachedCellImg< T, VolatileLabelMultisetArray > img = new CachedCellImg< T, VolatileLabelMultisetArray >( cells );
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

	static public class MultisetSource
	{
		private final RandomAccessibleInterval< LabelMultisetType >[] currentSources;

		private final LabelblkMultisetSetupImageLoader multisetImageLoader;

		private int currentTimePointIndex;

		@SuppressWarnings( "unchecked" )
		public MultisetSource( final LabelblkMultisetSetupImageLoader multisetImageLoader )
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
	}

	public long[] getDimensions( final int level )
	{
		return dimensions[ level ];
	}

	public int[] getBlockDimensions( final int level )
	{
		return blockDimensions[ level ];
	};
}
