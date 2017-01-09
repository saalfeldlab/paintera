package bdv.img.h5;

import java.io.IOException;

import bdv.ViewerSetupImgLoader;
import bdv.cache.LoadingStrategy;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.CachedCellImg;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.MipmapTransforms;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;

/**
 * {@link ViewerSetupImgLoader} for labels stored in simple HDF5 files
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class H5LabelMultisetSetupImageLoader
	extends AbstractH5SetupImageLoader< LabelMultisetType, VolatileLabelMultisetType, VolatileLabelMultisetArray >
{
	final private int numMipmapLevels;

	final private long[][] dimensions;

	final private double[][] mipmapResolutions;

	final private int[][] blockDimensions;

	final private AffineTransform3D[] mipmapTransforms;

	static private CacheArrayLoader<VolatileLabelMultisetArray> typedLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset )
	{
		final HDF5DataSetInformation typeInfo = reader.object().getDataSetInformation( dataset );
		final Class< ? > cls = typeInfo.getTypeInformation().tryGetJavaType();
//		System.out.println( typeInfo.getTypeInformation().tryGetJavaType().toString() );
		if ( short.class == cls )
			return new H5ShortLabelMultisetArrayLoader( reader, scaleReader, dataset );
		else if ( int.class == cls )
			return new H5IntLabelMultisetArrayLoader( reader, scaleReader, dataset );
		else if ( long.class == cls )
			return new H5LongLabelMultisetArrayLoader( reader, scaleReader, dataset );
		else
			return null;
	}

	public H5LabelMultisetSetupImageLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset,
			final int setupId,
			final int[] blockDimension,
			final double[] resolution ) throws IOException
	{
		super(
				reader,
				dataset,
				setupId,
				blockDimension,
				resolution,
				new LabelMultisetType(),
				new VolatileLabelMultisetType(),
				typedLoader( reader, scaleReader, dataset ) );

		if ( scaleReader == null )
		{
			numMipmapLevels = 1;
			dimensions = new long[][] { dimension };
			blockDimensions = new int[][] { blockDimension };
			mipmapResolutions = new double[][] { resolution };
			mipmapTransforms = new AffineTransform3D[] { mipmapTransform };
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
			mipmapTransforms[ 0 ] = mipmapTransform;

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
				mipmapTransforms[ level ].set( mipmapTransform );
				mipmapTransforms[ level ].concatenate(
						MipmapTransforms.getMipmapTransformDefault(
								bdv.img.hdf5.Util.castToDoubles( Util.long2int( factors ) ) ) );
			}
		}
	}


	public H5LabelMultisetSetupImageLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset,
			final int setupId,
			final int[] blockDimension ) throws IOException
	{
		this( reader, scaleReader, dataset, setupId, blockDimension, readResolution( reader, dataset ) );
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
