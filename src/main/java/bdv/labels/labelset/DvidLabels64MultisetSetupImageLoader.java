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

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class DvidLabels64MultisetSetupImageLoader
	extends AbstractViewerSetupImgLoader< SuperVoxelMultisetType, VolatileSuperVoxelMultisetType >
{
	private final double[] resolutions;

	private final long[] dimensions;

	private final int[] blockDimensions;

	private final AffineTransform3D mipmapTransform;

	private VolatileGlobalCellCache cache;

	private final VolatileSuperVoxelMultisetArrayLoader loader;

	private final int setupId;

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
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( new SuperVoxelMultisetType(), new VolatileSuperVoxelMultisetType() );
		this.setupId = setupId;

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

		loader = new VolatileSuperVoxelMultisetArrayLoader( apiUrl, nodeId, dataInstanceId, blockDimensions );
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
		final CachedCellImg< VolatileSuperVoxelMultisetType, VolatileSuperVoxelMultisetArray > img = prepareCachedImage( timepointId, setupId, level, LoadingStrategy.VOLATILE );
		final VolatileSuperVoxelMultisetType linkedType = new VolatileSuperVoxelMultisetType( img );
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

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileSuperVoxelMultisetArray > prepareCachedImage( final int timepointId,  final int setupId, final int level, final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final VolatileCellCache< VolatileSuperVoxelMultisetArray > c = cache.new VolatileCellCache< VolatileSuperVoxelMultisetArray >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< VolatileSuperVoxelMultisetArray > cells = new VolatileImgCells< VolatileSuperVoxelMultisetArray >( c, new Fraction(), dimensions, blockDimensions );
		final CachedCellImg< T, VolatileSuperVoxelMultisetArray > img = new CachedCellImg< T, VolatileSuperVoxelMultisetArray >( cells );
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

	public long[] getDimensions()
	{
		return dimensions;
	}

	public int[] getBlockDimensions()
	{
		return blockDimensions;
	}
}
