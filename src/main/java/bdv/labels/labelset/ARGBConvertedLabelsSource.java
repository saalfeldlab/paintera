package bdv.labels.labelset;

import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import bdv.labels.display.ARGBSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;

@SuppressWarnings( "unchecked" )
public class ARGBConvertedLabelsSource
	implements Source< VolatileARGBType >
{
	final private DvidLabels64MultisetSetupImageLoader multisetImageLoader;
	final private ARGBSource argbSource;

	final protected InterpolatorFactory< VolatileARGBType, RandomAccessible< VolatileARGBType > >[] interpolatorFactories;
	{
		interpolatorFactories = new InterpolatorFactory[]{
				new NearestNeighborInterpolatorFactory< VolatileARGBType >(),
				new ClampingNLinearInterpolatorFactory< VolatileARGBType >()
		};
	}

	public ARGBConvertedLabelsSource(
			final int setupId,
			final DvidLabels64MultisetSetupImageLoader multisetImageLoader,
			final ARGBSource argbSource )
	{
		this.multisetImageLoader = multisetImageLoader;
		this.argbSource = argbSource;
	}

	@Override
	public boolean isPresent( final int t )
	{
		return true;
	}

	@Override
	public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level )
	{
		return Converters.convert(
				multisetImageLoader.getVolatileImage( t, level ),
				new VolatileSuperVoxelMultisetARGBConverter( argbSource ),
				new VolatileARGBType() );
	}

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
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( multisetImageLoader.getMipmapTransforms()[ level ] );
	}

	@Override
	public AffineTransform3D getSourceTransform( final int t, final int level )
	{
		return multisetImageLoader.getMipmapTransforms()[ level ];
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
		return multisetImageLoader.getSetupId() + "";
	}

	/**
	 * TODO Have VoxelDimensions and return it.
	 */
	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return null;
	}

	/**
	 * TODO Store this in a field
	 */
	@Override
	public int getNumMipmapLevels()
	{
		return multisetImageLoader.getMipmapResolutions().length;
	}

	// TODO: make ARGBType version of this source
	public Source nonVolatile()
	{
		return this;
	}
}
