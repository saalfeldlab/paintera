package bdv.bigcat.viewer.source;

import java.io.IOException;
import java.util.Optional;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.Volatile;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;

public abstract class H5Source< T extends Type< T >, VT extends Volatile< T > > implements Source< T, VT >
{

	protected final IHDF5Reader h5reader;

	protected final String path;

	protected final String dataset;

	protected final Optional< double[] > resolution;

	protected final Optional< double[] > offset;

	protected final Optional< int[] > cellSize;

	protected final VolatileGlobalCellCache cache;

	protected H5Source(
			final String path, final String dataset, final int[] cellSize, final VolatileGlobalCellCache cache )
	{
		this( path, dataset, Optional.empty(), Optional.empty(), Optional.of( cellSize ), cache );
	}

	protected H5Source(
			final String path,
			final String dataset,
			final Optional< double[] > resolution,
			final Optional< double[] > offset,
			final Optional< int[] > cellSize,
			final VolatileGlobalCellCache cache )
	{
		super();
		this.h5reader = HDF5Factory.open( path );
		this.path = path;
		this.dataset = dataset;
		this.resolution = resolution;
		this.offset = offset;
		this.cellSize = cellSize;
		this.cache = cache;
	}

	@Override
	public String name()
	{
		return "h5://" + path + "/" + dataset;
	}

	public static class UnsignedBytes extends H5Source< UnsignedByteType, VolatileUnsignedByteType >
	{

		public UnsignedBytes(
				final String path,
				final String dataset,
				final Optional< double[] > resolution,
				final Optional< double[] > offset,
				final Optional< int[] > cellSize,
				final VolatileGlobalCellCache cache )
		{
			super( path, dataset, resolution, offset, cellSize, cache );
			// TODO Auto-generated constructor stub
		}

		@Override
		public ViewerSetupImgLoader< UnsignedByteType, VolatileUnsignedByteType > loader() throws IOException
		{
			return new H5UnsignedByteSetupImageLoader( h5reader, dataset, 0, cellSize.get(), cache );
		}

	}

	public static class LabelMultisets extends H5Source< LabelMultisetType, VolatileLabelMultisetType > implements LabelSource
	{

		public LabelMultisets(
				final String path,
				final String dataset,
				final Optional< double[] > resolution,
				final Optional< double[] > offset,
				final Optional< int[] > cellSize,
				final VolatileGlobalCellCache cache )
		{
			super( path, dataset, resolution, offset, cellSize, cache );
		}

		@Override
		public AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType > loader() throws IOException
		{
			return new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, 0, cellSize.get(), cache );
		}

	}

	static public double[] readResolution( final IHDF5Reader reader, final String dataset )
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

	static public double[] readOffset( final IHDF5Reader reader, final String dataset )
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

}
