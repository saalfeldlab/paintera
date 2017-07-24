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
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

@SuppressWarnings( "unchecked" )
abstract public class AbstractARGBConvertedLabelsSourceNonVolatile implements Source< ARGBType >
{
	protected ARGBStream argbStream;

	final protected long setupId;

	final protected InterpolatorFactory< ARGBType, RandomAccessible< ARGBType > >[] interpolatorFactories;
	{
		interpolatorFactories = new InterpolatorFactory[] {
				new NearestNeighborInterpolatorFactory< ARGBType >(),
				new ClampingNLinearInterpolatorFactory< ARGBType >()
		};
	}

	public AbstractARGBConvertedLabelsSourceNonVolatile(
			final int setupId,
			final ARGBStream argbStream )
	{
		this.setupId = setupId;
		this.argbStream = argbStream;
	}

	public void setStream( final ARGBStream stream )
	{
		this.argbStream = stream;
	}

	@Override
	public boolean isPresent( final int t )
	{
		return true;
	}

	@Override
	abstract public RandomAccessibleInterval< ARGBType > getSource( final int t, final int level );

	@Override
	public RealRandomAccessible< ARGBType > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		final ExtendedRandomAccessibleInterval< ARGBType, RandomAccessibleInterval< ARGBType > > extendedSource =
				Views.extendValue( getSource( t, level ), new ARGBType( 0 ) );
		switch ( method )
		{
		case NLINEAR:
			return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
		default:
			return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
		}
	}

	@Override
	public ARGBType getType()
	{
		return new ARGBType();
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
}
