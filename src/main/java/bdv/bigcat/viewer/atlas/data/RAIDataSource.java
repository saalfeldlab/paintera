package bdv.bigcat.viewer.atlas.data;

import java.util.function.Function;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.view.Views;

public class RAIDataSource< D extends Type< D >, T extends Type< T > > implements DataSource< D, T >
{

	private final AffineTransform3D[] mipmapTransforms;

	private final RandomAccessibleInterval< D >[] dataSources;

	private final RandomAccessibleInterval< T >[] sources;

	private final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation;

	private final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation;

	private final D dataExtension;

	private final T extension;

	private final String name;

	private final D d;

	private final T t;

	public RAIDataSource(
			final RandomAccessibleInterval< D >[] dataSources,
			final RandomAccessibleInterval< T >[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final D dataExtension,
			final T extension,
			final String name )
	{
		super();
		this.mipmapTransforms = mipmapTransforms;
		this.dataSources = dataSources;
		this.sources = sources;
		this.dataInterpolation = dataInterpolation;
		this.interpolation = interpolation;
		this.dataExtension = dataExtension;
		this.extension = extension;
		this.name = name;
		this.d = dataExtension.createVariable();
		this.t = extension.createVariable();
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
		return Views.interpolate( Views.extendValue( getSource( t, level ), extension ), interpolation.apply( method ) );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( mipmapTransforms[ level ] );
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
		return Views.interpolate( Views.extendValue( getDataSource( t, level ), dataExtension ), dataInterpolation.apply( method ) );
	}

	@Override
	public D getDataType()
	{
		return d;
	}

}