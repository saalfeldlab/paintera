package bdv.bigcat.viewer.atlas.data;

import java.util.function.Function;
import java.util.function.Supplier;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class RandomAccessibleIntervalPairDataSource< A extends Type< A >, B extends Type< B >, T extends Type< T >, U extends Type< U > > implements DataSource< Pair< A, B >, Pair< T, U > >
{

	private final AffineTransform3D[] mipmapTransforms;

	private final Pair< RandomAccessibleInterval< A >, RandomAccessibleInterval< B > >[] dataSources;

	private final Pair< RandomAccessibleInterval< T >, RandomAccessibleInterval< U > >[] sources;

	private final Function< Interpolation, InterpolatorFactory< Pair< A, B >, RandomAccessible< Pair< A, B > > > > dataInterpolation;

	private final Function< Interpolation, InterpolatorFactory< Pair< T, U >, RandomAccessible< Pair< T, U > > > > interpolation;

	private final Supplier< Pair< A, B > > dataTypeSupplier;

	private final Supplier< Pair< T, U > > typeSupplier;

	private final String name;

	public RandomAccessibleIntervalPairDataSource(
			final Pair< RandomAccessibleInterval< A >, RandomAccessibleInterval< B > >[] dataSources,
			final Pair< RandomAccessibleInterval< T >, RandomAccessibleInterval< U > >[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final Function< Interpolation, InterpolatorFactory< Pair< A, B >, RandomAccessible< Pair< A, B > > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< Pair< T, U >, RandomAccessible< Pair< T, U > > > > interpolation,
			final Supplier< Pair< A, B > > dataTypeSupplier,
			final Supplier< Pair< T, U > > typeSupplier,
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
	public RandomAccessibleInterval< Pair< T, U > > getSource( final int t, final int level )
	{
		return Views.interval( Views.pair( sources[ level ].getA(), sources[ level ].getB() ), sources[ level ].getA() );
	}

	@Override
	public RealRandomAccessible< Pair< T, U > > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.pair(
				Views.extendValue( sources[ level ].getA(), typeSupplier.get().getA() ),
				Views.extendValue( sources[ level ].getB(), typeSupplier.get().getB() ) ), interpolation.apply( method ) );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( mipmapTransforms[ level ] );
	}

	@Override
	public Pair< T, U > getType()
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
	public RandomAccessibleInterval< Pair< A, B > > getDataSource( final int t, final int level )
	{
		return Views.interval( Views.pair( dataSources[ level ].getA(), dataSources[ level ].getB() ), dataSources[ level ].getA() );
	}

	@Override
	public RealRandomAccessible< Pair< A, B > > getInterpolatedDataSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.pair(
				Views.extendValue( dataSources[ level ].getA(), dataTypeSupplier.get().getA() ),
				Views.extendValue( dataSources[ level ].getB(), dataTypeSupplier.get().getB() ) ), dataInterpolation.apply( method ) );
	}

	@Override
	public Pair< A, B > getDataType()
	{
		return dataTypeSupplier.get();
	}

}
