package bdv.img.dvid;

import java.io.IOException;
import java.util.Arrays;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.AbstractViewerSetupImgLoader;
import bdv.img.SetCache;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.JsonHelper;
import bdv.util.MipmapTransforms;
import bdv.util.dvid.DatasetKeyValue;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;

public class LabelblkMultisetSetupImageLoader
	extends AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType >
	implements SetCache
{
	protected final long[][] dimensions;

	protected final int[][] cellDimensions;

	protected final double[][] resolutions;

	protected final AffineTransform3D[] mipmapTransforms;

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

		cellDimensions = new int[ numMipmapLevels ][];
		loaders = new CacheArrayLoader[ numMipmapLevels ];

		/* first loader is a labels64 source */
		cellDimensions[ 0 ] = dataInstance.Extended.BlockSize;
		loaders[ 0 ] = new LabelblkMultisetVolatileArrayLoader( apiUrl, nodeId, dataInstanceId, cellDimensions[ 0 ] );

		/* subsequent loaders are key value stores */
		for ( int i = 0; i < dvidStores.length; ++i ) {
			loaders[ i + 1 ] =
				new DvidLabelMultisetArrayLoader( dvidStores[ i ] );
			cellDimensions[ i + 1 ] = cellDimensions[ 0 ];
		}
	}

	@Override
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
		return cellDimensions[ level ];
	}

	protected < S extends NativeType< S > > VolatileCachedCellImg< S, VolatileLabelMultisetArray > prepareCachedImage(
			final int timepointId,
			final int level,
			final LoadingStrategy loadingStrategy,
			final S t )
	{
		final int priority = resolutions.length - 1 - level;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellGrid grid = new CellGrid( dimensions[ level ], cellDimensions[level ] );

		return cache.createImg( grid, timepointId, setupId, level, cacheHints, loaders[ level ], t );
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		return prepareCachedImage( timepointId, level, LoadingStrategy.BLOCKING, type );
	}

	@Override
	public RandomAccessibleInterval< VolatileLabelMultisetType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		return prepareCachedImage( timepointId, level, LoadingStrategy.VOLATILE, volatileType );
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return resolutions;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}

	@Override
	public int numMipmapLevels()
	{
		return dimensions.length;
	}
}
