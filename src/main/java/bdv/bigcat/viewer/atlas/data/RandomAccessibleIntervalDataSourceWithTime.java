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
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class RandomAccessibleIntervalDataSourceWithTime< D extends Type< D >, T extends Type< T > > implements DataSource< D, T >
{

	private final AffineTransform3D[][] mipmapTransforms;

	private final RandomAccessibleInterval< D >[][] dataSources;

	private final RandomAccessibleInterval< T >[][] sources;

	private final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation;

	private final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation;

	private final Supplier< D > dataTypeSupplier;

	private final Supplier< T > typeSupplier;

	private final String name;

	private final int t0;

	private final int tMax;

	public RandomAccessibleIntervalDataSourceWithTime(
			final RandomAccessibleInterval< D >[][] dataSources,
			final RandomAccessibleInterval< T >[][] sources,
			final AffineTransform3D[][] mipmapTransforms,
			final int t0,
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
		this.t0 = t0;
		this.tMax = t0 + mipmapTransforms.length - 1;
	}

	@Override
	public int tMin()
	{
		return t0;
	}

	@Override
	public int tMax()
	{
		return tMax;
	}

	@Override
	public boolean isPresent( final int t )
	{
		return t >= t0 && t <= tMax;
	}

	@Override
	public RandomAccessibleInterval< T > getSource( final int t, final int level )
	{
		return sources[ t ][ level ];
	}

	@Override
	public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.extendValue( getSource( t, level ), typeSupplier.get() ), interpolation.apply( method ) );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( mipmapTransforms[ t ][ level ] );
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
		return mipmapTransforms[ 0 ].length;
	}

	@Override
	public RandomAccessibleInterval< D > getDataSource( final int t, final int level )
	{
		return dataSources[ t ][ level ];
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

	public static < D extends Type< D >, T extends Type< T > > DataSource< D, T > fromRandomAccessibleInterval(
			final RandomAccessibleInterval< D >[] dataSources,
			final RandomAccessibleInterval< T >[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final int timeDimension,
			final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final String name )
	{
		final long tMin = dataSources[ 0 ].min( timeDimension );
		final long tMax = dataSources[ 0 ].max( timeDimension );
		final int numTimesteps = ( int ) dataSources[ 0 ].dimension( timeDimension );
		final int numScales = dataSources.length;

		final RandomAccessibleInterval< D >[][] dataSourcesArray = new RandomAccessibleInterval[ numTimesteps ][ dataSources.length ];
		final RandomAccessibleInterval< T >[][] sourcesArray = new RandomAccessibleInterval[ numTimesteps ][ dataSources.length ];
		final AffineTransform3D[][] transforms = new AffineTransform3D[ numTimesteps ][];

		for ( long t = tMin, i = 0; t <= tMax; ++t, ++i )
		{
			for ( int scale = 0; scale < dataSources.length; ++scale )
			{
				dataSourcesArray[ ( int ) i ][ scale ] = Views.hyperSlice( dataSources[ scale ], timeDimension, t );
				sourcesArray[ ( int ) i ][ scale ] = Views.hyperSlice( sources[ scale ], timeDimension, t );
			}
			transforms[ ( int ) i ] = mipmapTransforms;
		}

		final D d = Util.getTypeFromInterval( dataSources[ 0 ] );
		final T t = Util.getTypeFromInterval( sources[ 0 ] );

		return new RandomAccessibleIntervalDataSourceWithTime<>(
				dataSourcesArray,
				sourcesArray,
				transforms,
				( int ) tMin,
				dataInterpolation,
				interpolation,
				d::createVariable,
				t::createVariable,
				name );

	}

}
