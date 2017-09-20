package bdv.img.h5;

import java.io.IOException;

import bdv.AbstractCachedViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.SetCache;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.Volatile;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.type.NativeType;

/**
 * {@link ViewerSetupImgLoader} for CREMI style 3D H5 datasets meaning that the
 * dataset may have an optional <code>double[]:resolution</code> field that
 * specifies the size of voxels.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
abstract public class AbstractH5SetupImageLoader< T extends NativeType< T >, V extends Volatile< T > & NativeType< V >, A extends VolatileAccess >
		extends AbstractCachedViewerSetupImgLoader< T, V, A >
		implements ViewerImgLoader, SetCache
{

	final protected double[] offset;

	final static protected long[] readDimension( final IHDF5Reader reader, final String dataset )
	{
		final long[] h5dim = reader.object().getDimensions( dataset );
		return new long[] { h5dim[ 2 ], h5dim[ 1 ], h5dim[ 0 ] };
	}

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

	final static protected double[] readOffset( final IHDF5Reader reader, final String dataset )
	{
		final double[] offset;
		if ( reader.object().hasAttribute( dataset, "offset" ) )
		{
			final double[] h5offset = reader.float64().getArrayAttr( dataset, "offset" );
			offset = new double[] { h5offset[ 2 ], h5offset[ 1 ], h5offset[ 0 ], };
		}
		else
			offset = new double[] { 0, 0, 0 };

		return offset;

	}

	public AbstractH5SetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final double[] resolution,
			final double[] offset,
			final T type,
			final V vType,
			final CacheArrayLoader< A > loader,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		super(
				setupId,
				new long[][] { readDimension( reader, dataset ) },
				new int[][] { blockDimension },
				new double[][] { resolution },
				type,
				vType,
				loader,
				cache );
		this.offset = offset;
	}

	public AbstractH5SetupImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final T type,
			final V vType,
			final CacheArrayLoader< A > loader,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		this(
				reader,
				dataset,
				setupId,
				blockDimension,
				readResolution( reader, dataset ),
				new double[ 3 ],
				type,
				vType,
				loader,
				cache );
	}

	@Override
	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	@Override
	public AbstractH5SetupImageLoader< T, V, A > getSetupImgLoader( final int setupId )
	{
		return this;
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cache;
	}

	public double[] getOffset()
	{
		return offset;
	}
}
