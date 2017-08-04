package bdv.img.h5;

import java.io.IOException;

import bdv.AbstractCachedViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.SetCache;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.labels.labelset.VolatileLabelMultisetType;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.util.Util;

/**
 * {@link ViewerSetupImgLoader} for labels stored in simple HDF5 files
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class H5LabelMultisetSetupImageLoader
		extends AbstractCachedViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType, VolatileLabelMultisetArray >
		implements ViewerImgLoader, SetCache
{
	static private CacheArrayLoader< VolatileLabelMultisetArray > typedLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset )
	{
		final HDF5DataSetInformation typeInfo = reader.object().getDataSetInformation( dataset );
		final Class< ? > cls = typeInfo.getTypeInformation().tryGetJavaType();
//		System.out.println( typeInfo.getTypeInformation().tryGetJavaType().toString() );
		if ( float.class == cls )
			return new H5FloatLabelMultisetArrayLoader( reader, scaleReader, dataset );
		else if ( short.class == cls )
			return new H5ShortLabelMultisetArrayLoader( reader, scaleReader, dataset );
		else if ( int.class == cls )
			return new H5IntLabelMultisetArrayLoader( reader, scaleReader, dataset );
		else if ( long.class == cls )
			return new H5LongLabelMultisetArrayLoader( reader, scaleReader, dataset );
		else
			return null;
	}

	static private long[][] readDimensions(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset )
	{
		final long[] h5dim = reader.object().getDimensions( dataset );
		if ( scaleReader == null )
			return new long[][] { { h5dim[ 2 ], h5dim[ 1 ], h5dim[ 0 ] } };
		else
		{
			final int numMipmapLevels = scaleReader.uint32().read( "levels" );
			final long[][] dimensions = new long[ numMipmapLevels ][];

			dimensions[ 0 ] = new long[] { h5dim[ 2 ], h5dim[ 1 ], h5dim[ 0 ] };

			for ( int level = 1; level < numMipmapLevels; ++level )
			{
				final String dimensionsPath = String.format( "l%02d/dimensions", level );
				dimensions[ level ] = scaleReader.uint64().readArray( dimensionsPath );
			}
			return dimensions;
		}
	}

	static private int[][] readCellDimensions(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset,
			final int[] cellDimension )
	{
		if ( scaleReader == null )
			return new int[][] { cellDimension };
		else
		{
			final int numMipmapLevels = scaleReader.uint32().read( "levels" );
			final int[][] cellDimensions = new int[ numMipmapLevels ][];

			cellDimensions[ 0 ] = cellDimension;

			for ( int level = 1; level < numMipmapLevels; ++level )
			{
				final String blocksizePath = String.format( "l%02d/blocksize", level );
				cellDimensions[ level ] = Util.long2int( scaleReader.uint64().readArray( blocksizePath ) );
			}
			return cellDimensions;
		}
	}

	static private double[][] readResolutions(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset,
			final double[] resolution )
	{
		if ( scaleReader == null )
		{
			if ( reader.object().hasAttribute( dataset, "resolution" ) )
			{
				final double[] h5res = reader.float64().getArrayAttr( dataset, "resolution" );
				return new double[][] { { h5res[ 2 ], h5res[ 1 ], h5res[ 0 ] } };
			}
			else
				return new double[][] { { 1, 1, 1 } };
		}
		else
		{
			final int numMipmapLevels = scaleReader.uint32().read( "levels" );
			final double[][] resolutions = new double[ numMipmapLevels ][];

			resolutions[ 0 ] = resolution;

			for ( int level = 1; level < numMipmapLevels; ++level )
			{
				final String factorsPath = String.format( "l%02d/factors", level );

				final long[] factors = scaleReader.uint64().readArray( factorsPath );
				resolutions[ level ] = new double[ 3 ];
				for ( int d = 0; d < 3; ++d )
					resolutions[ level ][ d ] = resolution[ d ] * factors[ d ];
			}
			return resolutions;
		}
	}

	final static protected double[] readResolution( final IHDF5Reader reader, final String dataset )
	{
		final double[] h5res = reader.float64().getArrayAttr( dataset, "resolution" );
		return new double[] { h5res[ 2 ], h5res[ 1 ], h5res[ 0 ] };
	}

	final static protected double[] readOffset( final IHDF5Reader reader, final String dataset )
	{
		final double[] h5res = reader.float64().getArrayAttr( dataset, "offset" );
		return new double[] { h5res[ 2 ], h5res[ 1 ], h5res[ 0 ] };
	}

	private final double[] offset;

	public H5LabelMultisetSetupImageLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset,
			final int setupId,
			final int[] cellDimension,
			final double[] resolution,
			final double[] offset,
			final VolatileGlobalCellCache cache ) throws IOException
	{

		super( setupId,
				readDimensions( reader, scaleReader, dataset ),
				readCellDimensions( reader, scaleReader, dataset, cellDimension ),
				readResolutions( reader, scaleReader, dataset, resolution ),
				new LabelMultisetType(),
				new VolatileLabelMultisetType(),
				typedLoader( reader, scaleReader, dataset ),
				cache );
		this.offset = offset;
	}

	public H5LabelMultisetSetupImageLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset,
			final int setupId,
			final int[] cellDimension,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		this( reader, scaleReader, dataset, setupId, cellDimension, readResolution( reader, dataset ), readOffset( reader, dataset ), cache );
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

	public double[] getOffset()
	{
		return this.offset;
	}
}
