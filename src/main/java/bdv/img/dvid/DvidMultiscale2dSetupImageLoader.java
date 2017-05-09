package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.AbstractViewerSetupImgLoader;
import bdv.img.SetCache;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.dvid.Multiscale2dDataInstance.Extended.Level;
import bdv.util.ColorStream;
import bdv.util.JsonHelper;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;

/**
 * {@link ViewerImgSetupLoader} for
 * <a href= "http://emdata.janelia.org/api/help/multiscale2d">DVID's multiscale2d type</a>
 * that maps uint64 into saturated ARGB colors using {@link ColorStream}.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class DvidMultiscale2dSetupImageLoader
	extends AbstractViewerSetupImgLoader< UnsignedByteType, VolatileUnsignedByteType >
	implements SetCache
{
	private final int numScales;

	private final double[][] resolutions;

	private final long[][] dimensions;

	private final int[][] cellDimensions;

	private final AffineTransform3D[] mipmapTransforms;

	protected VolatileGlobalCellCache cache;

	private final DvidMultiscale2dVolatileArrayLoader loader;

	final int setupId;

	/**
	 * http://hackathon.janelia.org/api/help/multiscale2d
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "graytiles"
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public DvidMultiscale2dSetupImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( new UnsignedByteType(), new VolatileUnsignedByteType() );

		this.setupId = setupId;

		/* fetch the list of available DataInstances */
//		final Map< String, Info >  repos = JsonHelper.tryFetch(
//				url + "/repos/info",
//				new TypeToken< Map< String, Info > >(){}.getType(),
//				20 );
//
//		final DataInstance repo = repos.get( nodeId ).DataInstances.get(dataInstanceId);

		final Multiscale2dDataInstance tileInstance =
				JsonHelper.fetch(
						apiUrl + "/node/" + nodeId + "/" + dataInstanceId + "/info",
						Multiscale2dDataInstance.class );

		final String sourceId = tileInstance.Extended.Source;

		final Grayscale8DataInstance sourceInstance =
				JsonHelper.fetch(
						apiUrl + "/node/" + nodeId + "/" + sourceId + "/info",
						Grayscale8DataInstance.class );

		final double zScale = sourceInstance.Extended.VoxelSize[ 2 ] / sourceInstance.Extended.VoxelSize[ 0 ];
		final long width = sourceInstance.Extended.MaxPoint[ 0 ] - sourceInstance.Extended.MinPoint[ 0 ];
		final long height = sourceInstance.Extended.MaxPoint[ 1 ] - sourceInstance.Extended.MinPoint[ 1 ];
		final long depth = sourceInstance.Extended.MaxPoint[ 2 ] - sourceInstance.Extended.MinPoint[ 2 ];

		numScales = tileInstance.Extended.Levels.size();

		resolutions = new double[ numScales ][];
		dimensions = new long[ numScales ][];
		mipmapTransforms = new AffineTransform3D[ numScales ];
		cellDimensions = new int[ numScales ][];
		final int[] zScales = new int[ numScales ];

		for ( int l = 0; l < numScales; ++l )
		{
			final Level level = tileInstance.Extended.getLevel( l );

			final int sixy = 1 << l;
			final int siz = Math.max( 1, ( int )Math.round( sixy / zScale ) );

			resolutions[ l ] = new double[] { sixy, sixy, siz };
			dimensions[ l ] = new long[]{ width >> l, height >> l, depth / siz };
			cellDimensions[ l ] = new int[]{ level.TileSize[ 0 ], level.TileSize[ 1 ], 1 };
			zScales[ l ] = siz;

			final AffineTransform3D mipmapTransform = new AffineTransform3D();

			mipmapTransform.set( sixy, 0, 0 );
			mipmapTransform.set( sixy, 1, 1 );
			mipmapTransform.set( zScale * siz, 2, 2 );

			mipmapTransform.set( 0.5 * ( sixy - 1 ), 0, 3 );
			mipmapTransform.set( 0.5 * ( sixy - 1 ), 1, 3 );
//			mipmapTransform.set( 0.5 * ( zScale * siz - 1 ), 2, 3 );

			mipmapTransforms[ l ] = mipmapTransform;
		}

		loader = new DvidMultiscale2dVolatileArrayLoader( apiUrl, nodeId, dataInstanceId, zScales, cellDimensions );


//		"http://hackathon.janelia.org/api/repo/2a3fd320aef011e4b0ce18037320227c/info"

//		"http://hackathon.janelia.org/api/node/2a3fd320aef011e4b0ce18037320227c/bodies/info"

//		"<api URL>/node/3f8c/mymultiscale2d/tile/xy/0/10_10_20"

	}

	protected < S extends NativeType< S > > VolatileCachedCellImg< S, VolatileByteArray > prepareCachedImage(
			final int timepointId,
			final int level,
			final LoadingStrategy loadingStrategy,
			final S t )
	{
		final int priority = resolutions.length - 1 - level;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellGrid grid = new CellGrid( dimensions[ level ], cellDimensions[level ] );

		return cache.createImg( grid, timepointId, setupId, level, cacheHints, loader, t );
	}

	@Override
	public RandomAccessibleInterval< UnsignedByteType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		return prepareCachedImage( timepointId, level, LoadingStrategy.BLOCKING, type );
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedByteType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		return prepareCachedImage( timepointId, level, LoadingStrategy.VOLATILE, volatileType );
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return resolutions;
	}

	@Override
	public int numMipmapLevels()
	{
		return numScales;
	}



	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}

	@Override
	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}
}
