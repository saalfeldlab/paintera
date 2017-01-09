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
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import bdv.util.JsonHelper;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Fraction;

/**
 * {@link ViewerSetupImgLoader} for
 * <a href= "http://emdata.janelia.org/api/help/grayscale8">DVID's grayscale8 type</a>.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class Uint8blkSetupImageLoader
	extends AbstractViewerSetupImgLoader< UnsignedByteType, VolatileUnsignedByteType >
{
	protected final double[] resolutions;

	private final long[] dimensions;

	private final int[] blockDimensions;

	private final AffineTransform3D mipmapTransform;

	protected VolatileGlobalCellCache cache;

	private final Uint8blkVolatileArrayLoader loader;

	private final int setupId;

	/**
	 * http://emdata.janelia.org/api/help/grayscale8
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "grayscale"
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public Uint8blkSetupImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( new UnsignedByteType(), new VolatileUnsignedByteType() );
		this.setupId = setupId;

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

		loader = new Uint8blkVolatileArrayLoader( apiUrl, nodeId, dataInstanceId, blockDimensions );
		cache = new VolatileGlobalCellCache( 1, 10 );
	}

	@Override
	public RandomAccessibleInterval< UnsignedByteType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< UnsignedByteType, VolatileByteArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final UnsignedByteType linkedType = new UnsignedByteType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedByteType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileUnsignedByteType, VolatileByteArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileUnsignedByteType linkedType = new VolatileUnsignedByteType( img );
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

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileByteArray > prepareCachedImage(
			final int timepointId,
			final int setupId,
			final int level,
			final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< VolatileByteArray > c = cache.new VolatileCellCache< VolatileByteArray >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< VolatileByteArray > cells = new VolatileImgCells< VolatileByteArray >( c, new Fraction(), dimensions, blockDimensions );
		final CachedCellImg< T, VolatileByteArray > img = new CachedCellImg< T, VolatileByteArray >( cells );
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
