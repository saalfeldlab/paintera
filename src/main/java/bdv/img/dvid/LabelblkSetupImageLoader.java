package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheHints;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.util.ColorStream;
import bdv.util.JsonHelper;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Fraction;


/**
 * {@link ViewerSetupImgLoader} for
 * <a href= "http://emdata.janelia.org/api/help/labels64">DVID's labels64 type</a>
 * that maps uint64 into saturated ARGB colors using {@link ColorStream}.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class LabelblkSetupImageLoader
	extends AbstractViewerSetupImgLoader< ARGBType, VolatileARGBType >
{
	private final double[] resolutions;

	private final long[] dimensions;

	private final int[] blockDimensions;

	private final AffineTransform3D mipmapTransform;

	protected VolatileGlobalCellCache cache;

	private final LabelblkVolatileArrayLoader loader;

	private final int setupId;

	/**
	 * http://hackathon.janelia.org/api/help/labels64
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "bodies"
	 * @param argbMask e.g. 0xffffffff for full opacity or 0x7fffffff for half opacity
	 *
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public LabelblkSetupImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId,
			final int argbMask ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( new ARGBType(), new VolatileARGBType() );
		this.setupId = setupId;

		final LabelblkDataInstance dataInstance =
				JsonHelper.fetch(
						apiUrl + "/node/" + nodeId + "/" + dataInstanceId + "/info",
						LabelblkDataInstance.class );

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

		loader = new LabelblkVolatileArrayLoader( apiUrl, nodeId, dataInstanceId, blockDimensions, argbMask );
	}

	/**
	 * http://hackathon.janelia.org/api/help/labels64
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "bodies"
	 *
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public LabelblkSetupImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( apiUrl, nodeId, dataInstanceId, setupId, 0xffffffff );
	}

	@Override
	public RandomAccessibleInterval< ARGBType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< ARGBType, VolatileIntArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final ARGBType linkedType = new ARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileARGBType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileARGBType, VolatileIntArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileARGBType linkedType = new VolatileARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return new double[][]{ resolutions };
	}

	@Override
	public int numMipmapLevels()
	{
		return 1;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileIntArray > prepareCachedImage( final int timepointId,  final int setupId, final int level, final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final VolatileCellCache< VolatileIntArray > c = cache.new VolatileCellCache< VolatileIntArray >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< VolatileIntArray > cells = new VolatileImgCells< VolatileIntArray >( c, new Fraction(), dimensions, blockDimensions );
		final CachedCellImg< T, VolatileIntArray > img = new CachedCellImg< T, VolatileIntArray >( cells );
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
}
