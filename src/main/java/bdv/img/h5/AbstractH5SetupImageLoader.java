package bdv.img.h5;

import java.io.IOException;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.cache.CacheHints;
import bdv.cache.LoadingStrategy;
import bdv.img.SetCache;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.Volatile;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

/**
 * {@link ViewerSetupImgLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractH5SetupImageLoader< T extends NativeType< T > , V extends Volatile< T >, A extends VolatileAccess >
	extends AbstractViewerSetupImgLoader< T, V >
	implements ViewerImgLoader, SetCache
{
	final protected double[] resolution;

	final protected long[] dimension;

	final protected int[] blockDimension;

	final protected AffineTransform3D mipmapTransform;

	protected VolatileGlobalCellCache cache;

	final protected CacheArrayLoader< A > loader;

	final protected int setupId;

	final static protected double[] readResolution( final IHDF5Reader reader, final String dataset )
	{
		final double[] resolution;
		if ( reader.object().hasAttribute( dataset, "resolution" ) )
		{
			final double[] h5res = reader.float64().getArrayAttr( dataset, "resolution" );
			resolution = new double[] { h5res[ 2 ], h5res[ 1 ], h5res[ 0 ], };
		}
		else
			resolution = new double[] { 1, 1, 1 };

		return resolution;
	}

	public AbstractH5SetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final double[] resolution,
			final T type,
			final V vType,
			final CacheArrayLoader< A > loader ) throws IOException
	{
		super( type, vType );
		this.setupId = setupId;
		this.loader = loader;
		this.resolution = resolution;

		final long[] h5dim = reader.object().getDimensions( dataset );

		dimension = new long[]{
				h5dim[ 2 ],
				h5dim[ 1 ],
				h5dim[ 0 ], };

		mipmapTransform = new AffineTransform3D();

		mipmapTransform.set( resolution[ 0 ], 0, 0 );
		mipmapTransform.set( resolution[ 1 ], 1, 1 );
		mipmapTransform.set( resolution[ 2 ], 2, 2 );

		this.blockDimension = blockDimension;

		cache = new VolatileGlobalCellCache( 1, 10 );
	}

	public AbstractH5SetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final T type,
			final V vType,
			final CacheArrayLoader< A > loader ) throws IOException
	{
		this( reader, dataset, setupId, blockDimension, readResolution( reader, dataset ), type, vType, loader );
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

	protected < S extends NativeType< S > > CachedCellImg< S, A > prepareCachedImage(
			final int timepointId,
			@SuppressWarnings( "hiding" ) final int setupId,
			final int level,
			final LoadingStrategy loadingStrategy )
	{
		final int priority = 0;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< A > c = cache.new VolatileCellCache< A >( timepointId, setupId, level, cacheHints, loader );
		final VolatileImgCells< A > cells = new VolatileImgCells< A >( c, new Fraction(), dimension, blockDimension );
		final CachedCellImg< S, A > img = new CachedCellImg< S, A >( cells );
		return img;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return new AffineTransform3D[]{ mipmapTransform };
	}

	@Override
	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cache;
	}
}
