package bdv.bigcat.viewer.atlas.data;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class RandomAccessibleIntervalDataSource< D extends Type< D >, T extends Type< T > > implements DataSource< D, T >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final AffineTransform3D[] mipmapTransforms;

	private final RandomAccessibleInterval< D >[] dataSources;

	private final RandomAccessibleInterval< T >[] sources;

	private final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation;

	private final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation;

	private final Supplier< D > dataTypeSupplier;

	private final Supplier< T > typeSupplier;

	private final String name;

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval< D >[] dataSources,
			final RandomAccessibleInterval< T >[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final String name )
	{
		this(
				dataSources,
				sources,
				mipmapTransforms,
				dataInterpolation,
				interpolation,
				() -> Util.getTypeFromInterval( dataSources[ 0 ] ).createVariable(),
				() -> Util.getTypeFromInterval( sources[ 0 ] ).createVariable(),
				name );
	}

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval< D >[] dataSources,
			final RandomAccessibleInterval< T >[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final Supplier< D > dataTypeSupplier,
			final Supplier< T > typeSupplier,
			final String name )
	{
		super();
		this.mipmapTransforms = mipmapTransforms;
		this.dataSources = dataSources;
		this.sources = sources;
		this.dataInterpolation = dataInterpolation;
		this.interpolation = interpolation;
		this.dataTypeSupplier = dataTypeSupplier;
		this.typeSupplier = typeSupplier;
		this.name = name;
	}

	@Override
	public boolean isPresent( final int t )
	{
		return true;
	}

	@Override
	public RandomAccessibleInterval< T > getSource( final int t, final int level )
	{
		return sources[ level ];
	}

	@Override
	public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.extendValue( getSource( t, level ), typeSupplier.get() ), interpolation.apply( method ) );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		LOG.trace( "Requesting mipmap transform for level {} at time {}: {}", level, t, mipmapTransforms[ level ] );
		transform.set( mipmapTransforms[ level ] );
	}

	@Override
	public T getType()
	{
		return typeSupplier.get();
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		// TODO What to do about this? Do we need this at all?
		return null;
	}

	@Override
	public int getNumMipmapLevels()
	{
		return mipmapTransforms.length;
	}

	@Override
	public RandomAccessibleInterval< D > getDataSource( final int t, final int level )
	{
		return dataSources[ level ];
	}

	@Override
	public RealRandomAccessible< D > getInterpolatedDataSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.extendValue( getDataSource( t, level ), dataTypeSupplier.get() ), dataInterpolation.apply( method ) );
	}

	@Override
	public D getDataType()
	{
		return dataTypeSupplier.get();
	}

}
