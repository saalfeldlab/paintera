package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;
import java.util.Optional;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
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

public class HDF5UnsignedByteSpec implements DatasetSpec< UnsignedByteType, VolatileUnsignedByteType >
{

	private final String name;

	private final String uri;

	public abstract class InternalSource< T > implements Source< T >
	{

		private final H5UnsignedByteSetupImageLoader loader;

		private final T t;

		private final String name;

		public InternalSource( final H5UnsignedByteSetupImageLoader loader, final T t, final String name )
		{
			super();
			this.loader = loader;
			this.t = t;
			this.name = name;
		}

		@Override
		public boolean isPresent( final int t )
		{
			return t == 0;
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.set( loader.getMipmapTransforms()[ level ] );
		}

		@Override
		public T getType()
		{
			return t;
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
			return loader.getMipmapResolutions().length;
		}
	}

	public class UnsignedByteSource extends InternalSource< UnsignedByteType >
	{

		public UnsignedByteSource( final H5UnsignedByteSetupImageLoader loader, final UnsignedByteType t, final String name )
		{
			super( loader, t, name );
			// TODO Auto-generated constructor stub
		}

		@Override
		public RandomAccessibleInterval< UnsignedByteType > getSource( final int t, final int level )
		{
			return loader.getImage( t );
		}

		@Override
		public RealRandomAccessible< UnsignedByteType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{
			return Views.interpolate(
					Views.extendZero( getSource( t, level ) ),
					method.equals( Interpolation.NEARESTNEIGHBOR ) ? new NearestNeighborInterpolatorFactory<>() : new NLinearInterpolatorFactory<>() );
		}

	}

	public class VolatileUnsignedByteSource extends InternalSource< VolatileUnsignedByteType >
	{

		public VolatileUnsignedByteSource( final H5UnsignedByteSetupImageLoader loader, final VolatileUnsignedByteType t, final String name )
		{
			super( loader, t, name );
			// TODO Auto-generated constructor stub
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
					Views.extendZero( getSource( t, level ) ),
					method.equals( Interpolation.NEARESTNEIGHBOR ) ? new NearestNeighborInterpolatorFactory<>() : new NLinearInterpolatorFactory<>() );
		}

	}

	private final H5UnsignedByteSetupImageLoader loader;

	public HDF5UnsignedByteSpec( final String path, final String dataset, final int[] cellSize, final double[] resolution, final String name ) throws IOException
	{
		super();
		final IHDF5Reader h5reader = HDF5Factory.open( path );
		this.loader = new H5UnsignedByteSetupImageLoader( h5reader, dataset, 0, cellSize, resolution, new VolatileGlobalCellCache( new SharedQueue( 8 ) ) );
		this.name = name;
		this.uri = "h5://" + path + "/" + dataset;
	}

	@Override
	public UnsignedByteSource getSource()
	{
		return new UnsignedByteSource( loader, new UnsignedByteType(), "data" );
	}

	@Override
	public VolatileUnsignedByteSource getViewerSource()
	{
		return new VolatileUnsignedByteSource( loader, new VolatileUnsignedByteType(), "data" );
	}

	@Override
	public String name()
	{
		return name;
	}

	@Override
	public Optional< String > uri()
	{
		return Optional.of( uri );
	}

}
