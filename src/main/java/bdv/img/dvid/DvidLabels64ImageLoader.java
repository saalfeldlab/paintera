package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
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
import bdv.util.JsonHelper;

public class DvidLabels64ImageLoader extends AbstractViewerImgLoader< ARGBType, VolatileARGBType >
{
	private final double[] resolutions;

	private final long[] dimensions;

	private final int[] blockDimensions;

	private final AffineTransform3D mipmapTransform;

	protected VolatileGlobalCellCache< VolatileIntArray > cache;

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
	public DvidLabels64ImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( new ARGBType(), new VolatileARGBType() );
		
		final Labels64DataInstance dataInstance = 
				JsonHelper.fetch(
						apiUrl + "/node/" + nodeId + "/" + dataInstanceId + "/info",
						Labels64DataInstance.class );
		
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

		cache = new VolatileGlobalCellCache< VolatileIntArray >(
				new DvidLabels64VolatileArrayLoader(
						apiUrl,
						nodeId,
						dataInstanceId,
						blockDimensions ), 1, 1, 1, 10 );
	}
	
	@Override
	public RandomAccessibleInterval< ARGBType > getImage( final ViewId view, final int level )
	{
		final CachedCellImg< ARGBType, VolatileIntArray > img = prepareCachedImage( view, level, LoadingStrategy.BLOCKING );
		final ARGBType linkedType = new ARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileARGBType > getVolatileImage( final ViewId view, final int level )
	{
		final CachedCellImg< VolatileARGBType, VolatileIntArray > img = prepareCachedImage( view, level, LoadingStrategy.VOLATILE );
		final VolatileARGBType linkedType = new VolatileARGBType( img );
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

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileIntArray > prepareCachedImage( final ViewId view, final int level, final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< VolatileIntArray > c = cache.new VolatileCellCache( view.getTimePointId(), view.getViewSetupId(), level, cacheHints );
		final VolatileImgCells< VolatileIntArray > cells = new VolatileImgCells< VolatileIntArray >( c, new Fraction(), dimensions, blockDimensions );
		final CachedCellImg< T, VolatileIntArray > img = new CachedCellImg< T, VolatileIntArray >( cells );
		return img;
	}

	@Override
	public VolatileGlobalCellCache< VolatileIntArray > getCache()
	{
		return cache;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms( final int setup )
	{
		return new AffineTransform3D[]{ mipmapTransform };
	}
}
