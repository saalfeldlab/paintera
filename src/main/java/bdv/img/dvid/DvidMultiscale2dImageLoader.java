package bdv.img.dvid;

import java.io.IOException;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Fraction;
import bdv.AbstractViewerImgLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import bdv.img.dvid.Multiscale2dDataInstance.Extended.Level;
import bdv.util.JsonHelper;

public class DvidMultiscale2dImageLoader extends AbstractViewerImgLoader< UnsignedByteType, VolatileUnsignedByteType >
{
	private final int numScales;
	
	private final double[][] mipmapResolutions;

	private final long[][] imageDimensions;

	private final int[][] blockDimensions;

	private final AffineTransform3D[] mipmapTransforms;

	protected VolatileGlobalCellCache< VolatileByteArray > cache;

	/**
	 * http://hackathon.janelia.org/api/help/grayscale8
	 * 
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "graytiles"
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 */
	public DvidMultiscale2dImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( new UnsignedByteType(), new VolatileUnsignedByteType() );
		
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
		
		mipmapResolutions = new double[ numScales ][];
		imageDimensions = new long[ numScales ][];
		mipmapTransforms = new AffineTransform3D[ numScales ];
		blockDimensions = new int[ numScales ][];
		final int[] zScales = new int[ numScales ];
		
		for ( int l = 0; l < numScales; ++l )
		{
			final Level level = tileInstance.Extended.getLevel( l );
			
			final int sixy = 1 << l;
			final int siz = Math.max( 1, ( int )Math.round( sixy / zScale ) );

			mipmapResolutions[ l ] = new double[] { sixy, sixy, siz };
			imageDimensions[ l ] = new long[]{ width >> l, height >> l, depth / siz };
			blockDimensions[ l ] = new int[]{ level.TileSize[ 0 ], level.TileSize[ 1 ], 1 };
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

		cache = new VolatileGlobalCellCache< VolatileByteArray >(
				new DvidMultiscale2dVolatileArrayLoader(
						apiUrl,
						nodeId,
						dataInstanceId,
						zScales,
						blockDimensions ), 1, 1, numScales, 10 );
		
		
//		"http://hackathon.janelia.org/api/repo/2a3fd320aef011e4b0ce18037320227c/info"
		
//		"http://hackathon.janelia.org/api/node/2a3fd320aef011e4b0ce18037320227c/bodies/info"
		
//		"<api URL>/node/3f8c/mymultiscale2d/tile/xy/0/10_10_20"

	}
	
	@Override
	public RandomAccessibleInterval< UnsignedByteType > getImage( final ViewId view, final int level )
	{
		final CachedCellImg< UnsignedByteType, VolatileByteArray > img = prepareCachedImage( view, level, LoadingStrategy.BLOCKING );
		final UnsignedByteType linkedType = new UnsignedByteType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedByteType > getVolatileImage( final ViewId view, final int level )
	{
		final CachedCellImg< VolatileUnsignedByteType, VolatileByteArray > img = prepareCachedImage( view, level, LoadingStrategy.VOLATILE );
		final VolatileUnsignedByteType linkedType = new VolatileUnsignedByteType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions( final int setup )
	{
		return mipmapResolutions;
	}

	@Override
	public int numMipmapLevels( final int setup )
	{
		return numScales;
	}

	/**
	 * (Almost) create a {@link CachedCellImg} backed by the cache. The created image
	 * needs a {@link NativeImg#setLinkedType(net.imglib2.type.Type) linked
	 * type} before it can be used. The type should be either {@link ARGBType}
	 * and {@link VolatileARGBType}.
	 */
	protected < T extends NativeType< T > > CachedCellImg< T, VolatileByteArray > prepareCachedImage( final ViewId view, final int level, final LoadingStrategy loadingStrategy )
	{
		final long[] dimensions = imageDimensions[ level ];
		final int[] cellDimensions = blockDimensions[ level ];

		final int priority = numScales - 1 - level;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< VolatileByteArray > c = cache.new VolatileCellCache( view.getTimePointId(), view.getViewSetupId(), level, cacheHints );
		final VolatileImgCells< VolatileByteArray > cells = new VolatileImgCells< VolatileByteArray >( c, new Fraction(), dimensions, cellDimensions );
		final CachedCellImg< T, VolatileByteArray > img = new CachedCellImg< T, VolatileByteArray >( cells );
		return img;
	}

	@Override
	public VolatileGlobalCellCache< VolatileByteArray > getCache()
	{
		return cache;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms( final int setup )
	{
		return mipmapTransforms;
	}
}
