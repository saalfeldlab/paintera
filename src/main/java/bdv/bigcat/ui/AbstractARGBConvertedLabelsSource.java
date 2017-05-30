package bdv.bigcat.ui;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

@SuppressWarnings( "unchecked" )
abstract public class AbstractARGBConvertedLabelsSource implements Source< VolatileARGBType >
{
	final protected ARGBStream argbStream;
	final protected long setupId;

	final protected InterpolatorFactory< VolatileARGBType, RandomAccessible< VolatileARGBType > >[] interpolatorFactories;
	{
		interpolatorFactories = new InterpolatorFactory[]{
				new NearestNeighborInterpolatorFactory< VolatileARGBType >(),
				new ClampingNLinearInterpolatorFactory< VolatileARGBType >()
		};
	}

	public AbstractARGBConvertedLabelsSource(
			final int setupId,
			final ARGBStream argbStream )
	{
		this.setupId = setupId;
		this.argbStream = argbStream;
	}

	@Override
	public boolean isPresent( final int t )
	{
		return true;
	}

	@Override
	abstract public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level );

	@Override
	public RealRandomAccessible< VolatileARGBType > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		final ExtendedRandomAccessibleInterval< VolatileARGBType, RandomAccessibleInterval< VolatileARGBType > > extendedSource =
				Views.extendValue( getSource( t,  level ), new VolatileARGBType( 0 ) );
		switch ( method )
		{
		case NLINEAR :
			return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
		default :
			return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
		}
	}

	@Override
	public VolatileARGBType getType()
	{
		return new VolatileARGBType();
	}

	/**
	 * TODO Have a name and return it.
	 */
	@Override
	public String getName()
	{
		return setupId + "";
	}

	/**
	 * TODO Have VoxelDimensions and return it.
	 */
	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return null;
	}

	// TODO: make ARGBType version of this source
	public Source nonVolatile()
	{
		return this;
	}
}
