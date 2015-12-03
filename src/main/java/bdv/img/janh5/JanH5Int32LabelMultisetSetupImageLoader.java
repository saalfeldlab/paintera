package bdv.img.janh5;

import java.io.IOException;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileGlobalCellCache.VolatileCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.labels.labelset.VolatileLabelMultisetType;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

/**
 * {@link ViewerSetupImgLoader} for
 * Jan Funke's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class JanH5Int32LabelMultisetSetupImageLoader
	extends AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType >
{
	final private IHDF5Reader reader;

	final private String dataset;

	protected final double[] resolution;

	private final long[] dimension;

	private final int[] blockDimension;

	private final AffineTransform3D mipmapTransform;

	protected VolatileGlobalCellCache cache;

	private int setupId;

	private final JanH5Int32LabelMultisetArrayLoader loader;

	public JanH5Int32LabelMultisetSetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension ) throws IOException
	{
		super( new LabelMultisetType(), new VolatileLabelMultisetType() );
		this.reader = reader;
		this.dataset = dataset;
		this.setupId = setupId;

		final long[] h5dim = reader.object().getDimensions( dataset );

		dimension = new long[]{
				h5dim[ 2 ],
				h5dim[ 1 ],
				h5dim[ 0 ], };

		final double[] h5res = reader.float64().getArrayAttr( dataset, "resolution" );

		resolution = new double[]{
				h5res[ 2 ],
				h5res[ 1 ],
				h5res[ 0 ], };

		mipmapTransform = new AffineTransform3D();

		mipmapTransform.set( 1, 0, 0 );
		mipmapTransform.set( 1, 1, 1 );
		mipmapTransform.set( 1, 2, 2 );

		this.blockDimension = blockDimension;

		loader = new JanH5Int32LabelMultisetArrayLoader( reader, dataset );
		cache = new VolatileGlobalCellCache( 1, 1, 1, 10 );
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( LoadingStrategy.BLOCKING );
		final LabelMultisetType linkedType = new LabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileLabelMultisetType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< VolatileLabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( LoadingStrategy.BLOCKING );
		final VolatileLabelMultisetType linkedType = new VolatileLabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return new double[][]{ resolution };
	}

	@Override
	public int numMipmapLevels()
	{
		return 1;
	}

	protected < T extends NativeType< T > > CachedCellImg< T, VolatileLabelMultisetArray > prepareCachedImage(
			final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final VolatileCellCache< VolatileLabelMultisetArray > c =
				cache.new VolatileCellCache< VolatileLabelMultisetArray >(
						0,
						setupId,
						0,
						cacheHints,
						loader );
		final VolatileImgCells< VolatileLabelMultisetArray > cells = new VolatileImgCells< VolatileLabelMultisetArray >( c, new Fraction(), dimension, new int[]{ 128, 128, 128 });
		final CachedCellImg< T, VolatileLabelMultisetArray > img = new CachedCellImg< T, VolatileLabelMultisetArray >( cells );
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
