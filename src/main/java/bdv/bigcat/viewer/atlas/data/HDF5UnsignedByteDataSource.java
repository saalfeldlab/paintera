package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;
import java.util.Optional;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.viewer.Interpolation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.view.Views;

public class HDF5UnsignedByteDataSource implements DataSource< UnsignedByteType, VolatileUnsignedByteType >
{

	private final String name;

	private final String uri;

	private final int setupId;

	private final H5UnsignedByteSetupImageLoader loader;

	public HDF5UnsignedByteDataSource(
			final String path,
			final String dataset,
			final int[] cellSize,
			final double[] resolution,
			final String name,
			final VolatileGlobalCellCache cellCache,
			final int setupId ) throws IOException
	{
		super();
		final IHDF5Reader h5reader = HDF5Factory.open( path );
		// TODO Use better value for number of threads of shared queue
		this.loader = new H5UnsignedByteSetupImageLoader( h5reader, dataset, setupId, cellSize, resolution, cellCache );
		this.name = name;
		this.uri = "h5://" + path + "/" + dataset;
		this.setupId = setupId;
	}

	public Optional< String > uri()
	{
		return Optional.of( uri );
	}

	@Override
	public boolean isPresent( final int t )
	{
		return true;
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedByteType > getSource( final int t, final int level )
	{
		return loader.getVolatileImage( t, level );
	}

	@Override
	public RealRandomAccessible< VolatileUnsignedByteType > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate(
				Views.extendValue( getSource( t, level ), new VolatileUnsignedByteType( 0 ) ),
				method.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>() );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( loader.getMipmapTransforms()[ level ] );
	}

	@Override
	public VolatileUnsignedByteType getType()
	{
		return loader.getVolatileImageType();
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return null;
	}

	@Override
	public int getNumMipmapLevels()
	{
		return loader.getMipmapTransforms().length;
	}

	@Override
	public RandomAccessibleInterval< UnsignedByteType > getDataSource( final int t, final int level )
	{
		return loader.getImage( 0, level );
	}

	@Override
	public RealRandomAccessible< UnsignedByteType > getInterpolatedDataSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate(
				Views.extendValue( getDataSource( t, level ), new UnsignedByteType( 0 ) ),
				method.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>() );
	}

	@Override
	public UnsignedByteType getDataType()
	{
		return loader.getImageType();
	}

}
