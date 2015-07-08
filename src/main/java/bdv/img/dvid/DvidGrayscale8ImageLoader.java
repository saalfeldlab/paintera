package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Fraction;
import bdv.AbstractViewerImgLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import bdv.util.JsonHelper;

public class DvidGrayscale8ImageLoader extends AbstractViewerImgLoader< UnsignedByteType, VolatileUnsignedByteType >
{
	private final double[] resolutions;

	private final long[] dimensions;

	private final int[] blockDimensions;

	private final AffineTransform3D mipmapTransform;

	protected VolatileGlobalCellCache< VolatileByteArray > cache;

	/**
	 * http://hackathon.janelia.org/api/help/grayscale8
	 * 
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "grayscale"
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 */
	public DvidGrayscale8ImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( new UnsignedByteType(), new VolatileUnsignedByteType() );
		
		final Grayscale8DataInstance dataInstance = 
				JsonHelper.fetch(
						apiUrl + "/node/" + nodeId + "/" + dataInstanceId + "/info",
						Grayscale8DataInstance.class );
		
		dimensions = new long[]{
				dataInstance.Extended.MaxPoint[ 0 ] - dataInstance.Extended.MinPoint[ 0 ],
				dataInstance.Extended.MaxPoint[ 1 ] - dataInstance.Extended.MinPoint[ 1 ],
				dataInstance.Extended.MaxPoint[ 2 ] - dataInstance.Extended.MinPoint[ 2 ] };
		
		resolutions = new double[]{
				dataInstance.Extended.VoxelSize[ 0 ],
				dataInstance.Extended.VoxelSize[ 1 ],
				dataInstance.Extended.VoxelSize[ 2 ] };
		
		mipmapTransform = new AffineTransform3D();

		mipmapTransform.set( 1, 0, 0 );
		mipmapTransform.set( 1, 1, 1 );
		mipmapTransform.set( 1, 2, 2 );

		blockDimensions = dataInstance.Extended.BlockSize;

		cache = new VolatileGlobalCellCache< VolatileByteArray >(
				new DvidGrayscale8VolatileArrayLoader(
						apiUrl,
						nodeId,
						dataInstanceId,
						blockDimensions ), 1, 1, 1, 10 );
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
		return new double[][]{ resolutions };
	}

	@Override
	public int numMipmapLevels( final int setup )
	{
		return 1;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileByteArray > prepareCachedImage( final ViewId view, final int level, final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< VolatileByteArray > c = cache.new VolatileCellCache( view.getTimePointId(), view.getViewSetupId(), level, cacheHints );
		final VolatileImgCells< VolatileByteArray > cells = new VolatileImgCells< VolatileByteArray >( c, new Fraction(), dimensions, blockDimensions );
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
		return new AffineTransform3D[]{ mipmapTransform };
	}
}
