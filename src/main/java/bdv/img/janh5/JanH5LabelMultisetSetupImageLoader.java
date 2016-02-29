package bdv.img.janh5;

import java.io.IOException;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.MipmapTransforms;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Util;

/**
 * {@link ViewerSetupImgLoader} for
 * Jan Funke's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class JanH5LabelMultisetSetupImageLoader
	extends AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType >
{
	/**
	 * It seems unnecessary to hold a reference to the {@link IHDF5Reader} if
	 * consumers use only its subclasses, e.g. {@link IHDF5Reader#float32()},
	 * however, the subclasses do not have a reference to their generator and
	 * {@link IHDF5Reader} closes the file when it gets garbage collected by
	 * which the subclasses loose their source. Really quirky!
	 */
	final protected IHDF5Reader reader;

	final protected IHDF5Reader scaleReader;

	final protected long[][] dimensions;

	final protected int[][] blockDimensions;

	protected VolatileGlobalCellCache cache;

	final protected CacheArrayLoader< VolatileLabelMultisetArray > loader;

	final protected int setupId;

	private final int numMipmapLevels;

	private final double[][] mipmapResolutions;

	private final AffineTransform3D[] mipmapTransforms;

	public JanH5LabelMultisetSetupImageLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset,
			final int setupId,
			final int[] blockDimension ) throws IOException
	{
		super( new LabelMultisetType(), new VolatileLabelMultisetType() );
		this.reader = reader;
		this.scaleReader = scaleReader;
		this.setupId = setupId;
		this.loader = new JanH5LabelMultisetArrayLoader( reader, scaleReader, dataset );

		final long[] h5dim = reader.object().getDimensions( dataset );

		final long[] dimension = new long[]{
				h5dim[ 2 ],
				h5dim[ 1 ],
				h5dim[ 0 ], };

		final double[] h5res = reader.float64().getArrayAttr( dataset, "resolution" );

		final double[] resolution = new double[]{
				h5res[ 2 ],
				h5res[ 1 ],
				h5res[ 0 ], };
		final AffineTransform3D calib = new AffineTransform3D();
		calib.set( resolution[ 0 ], 0, 0 );
		calib.set( resolution[ 1 ], 1, 1 );
		calib.set( resolution[ 2 ], 2, 2 );

		cache = new VolatileGlobalCellCache( 1, 1, 1, 10 );

		if ( scaleReader == null )
		{
			numMipmapLevels = 1;
			dimensions = new long[][] { dimension };
			blockDimensions = new int[][] { blockDimension };
			mipmapResolutions = new double[][] { resolution };
			mipmapTransforms = new AffineTransform3D[] { calib };
		}
		else
		{
			numMipmapLevels = scaleReader.uint32().read( "levels" );
			dimensions = new long[ numMipmapLevels ][];
			blockDimensions = new int[ numMipmapLevels ][];
			mipmapResolutions = new double[ numMipmapLevels ][];
			mipmapTransforms = new AffineTransform3D[ numMipmapLevels ];

			dimensions[ 0 ] = dimension;
			blockDimensions[ 0 ] = blockDimension;
			mipmapResolutions[ 0 ] = resolution;
			mipmapTransforms[ 0 ] = calib;

			for ( int level = 1; level < numMipmapLevels; ++level )
			{
				final String dimensionsPath = String.format( "l%02d/dimensions", level );
				final String factorsPath = String.format( "l%02d/factors", level );
				final String blocksizePath = String.format( "l%02d/blocksize", level );

				dimensions[ level ] = scaleReader.uint64().readArray( dimensionsPath );
				blockDimensions[ level ] = Util.long2int( scaleReader.uint64().readArray( blocksizePath ) );

				final long[] factors = scaleReader.uint64().readArray( factorsPath );
				mipmapResolutions[ level ] = new double[ 3 ];
				for ( int d = 0; d < 3; ++d )
					mipmapResolutions[ level ][ d ] = resolution[ d ] * factors[ d ];

				mipmapTransforms[ level ] = new AffineTransform3D();
				mipmapTransforms[ level ].set( calib );
				mipmapTransforms[ level ].concatenate(
						MipmapTransforms.getMipmapTransformDefault(
								bdv.img.hdf5.Util.castToDoubles( Util.long2int( factors ) ) ) );
			}
		}
	}

	protected < S extends NativeType< S > > CachedCellImg< S, VolatileLabelMultisetArray > prepareCachedImage(
			final int timepointId,
			@SuppressWarnings( "hiding" ) final int setupId,
			final int level,
			final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< VolatileLabelMultisetArray > c = cache.new VolatileCellCache< VolatileLabelMultisetArray >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< VolatileLabelMultisetArray > cells = new VolatileImgCells< VolatileLabelMultisetArray >( c, new Fraction(), dimensions[ level ], blockDimensions[ level ] );
		final CachedCellImg< S, VolatileLabelMultisetArray > img = new CachedCellImg< S, VolatileLabelMultisetArray >( cells );
		return img;
	}

	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( timepointId, setupId, level, LoadingStrategy.BLOCKING );
		final LabelMultisetType linkedType = new LabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileLabelMultisetType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		boolean blocking = false;
		if ( hints.length > 0 )
			for ( final ImgLoaderHint hint : hints )
				if ( hint == ImgLoaderHints.LOAD_COMPLETELY )
				{
					blocking = true;
					break;
				}
		final CachedCellImg< VolatileLabelMultisetType, VolatileLabelMultisetArray > img =
				prepareCachedImage( timepointId, setupId, level, blocking ? LoadingStrategy.BLOCKING : LoadingStrategy.VOLATILE );
		final VolatileLabelMultisetType linkedType = new VolatileLabelMultisetType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return mipmapResolutions;
	}

	@Override
	public int numMipmapLevels()
	{
		return numMipmapLevels;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}
}
