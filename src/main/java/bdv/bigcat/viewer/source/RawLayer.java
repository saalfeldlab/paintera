package bdv.bigcat.viewer.source;

import bdv.ViewerSetupImgLoader;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileRealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class RawLayer< T extends RealType< T > > implements SourceLayer
{

	private final Source< T, VolatileRealType< T > > source;

	private final ViewerSetupImgLoader< T, VolatileRealType< T > > loader;

	private final bdv.viewer.Source< T > sourceSource;

	public RawLayer( final Source< T, VolatileRealType< T > > source ) throws Exception
	{
		super();
		this.source = source;
		this.loader = source.loader();
		this.sourceSource = new SourceSource();
	}

	private final RealARGBConverter< T > conv = new RealARGBConverter<>( 0, 1 );

	private boolean isActive = true;

	@Override
	public SourceAndConverter< ? > getSourceAndConverter()
	{
		return new SourceAndConverter<>( sourceSource, conv );
	}

	@Override
	public boolean isActive()
	{
		return isActive;
	}

	@Override
	public void setActive( final boolean active )
	{
		isActive = active;
	}

	@Override
	public String name()
	{
		return source.name();
	}

	@Override
	public Source< T, VolatileRealType< T > > source()
	{
		return source;
	}

	public void setContrast( final double min, final double max )
	{
		this.conv.setMax( min );
		this.conv.setMax( max );
	}

	public class SourceSource implements bdv.viewer.Source< T >
	{

		@Override
		public boolean isPresent( final int t )
		{
			return true;
		}

		@Override
		public RandomAccessibleInterval< T > getSource( final int t, final int level )
		{
			return loader.getImage( 0, 0 );
		}

		@Override
		public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{
			final T type = loader.getImageType();
			type.setZero();
			final RandomAccessible< T > ext = Views.extendValue( getSource( t, level ), type );
			return Views.interpolate( ext, method.equals( Interpolation.NEARESTNEIGHBOR ) ? new NearestNeighborInterpolatorFactory<>() : new NLinearInterpolatorFactory<>() );
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.set( loader.getMipmapTransforms()[ level ] );
		}

		@Override
		public T getType()
		{
			return Util.getTypeFromInterval( getSource( 0, 0 ) );
//			return t.createVariable();
		}

		@Override
		public String getName()
		{
			return source.name();
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getNumMipmapLevels()
		{
			return 1;
		}

	}

}
