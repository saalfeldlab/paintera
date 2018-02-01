package bdv.bigcat.viewer.atlas.data;

import java.util.function.Function;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.view.Views;

public class ConverterDataSource< D, T, U extends Type< U > > implements DataSource< D, U >
{
	private final DataSource< D, T > dataSource;

	private final Converter< T, U > converter;

	private final Function< Interpolation, InterpolatorFactory< U, RandomAccessible< U > > > interpolation;

	private final U extension;

	private final U u;

	public ConverterDataSource( final DataSource< D, T > dataSource, final Converter< T, U > converter, final Function< Interpolation, InterpolatorFactory< U, RandomAccessible< U > > > interpolation, final U extension )
	{
		super();
		this.dataSource = dataSource;
		this.converter = converter;
		this.interpolation = interpolation;
		this.extension = extension;
		this.u = extension.createVariable();
	}

	@Override
	public boolean isPresent( final int t )
	{
		return dataSource.isPresent( t );
	}

	@Override
	public RandomAccessibleInterval< U > getSource( final int t, final int level )
	{
		return Converters.convert( dataSource.getSource( t, level ), converter, u );
	}

	@Override
	public RealRandomAccessible< U > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.extendValue( getSource( t, level ), extension ), interpolation.apply( method ) );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		dataSource.getSourceTransform( t, level, transform );
	}

	@Override
	public U getType()
	{
		return u;
	}

	@Override
	public String getName()
	{
		return dataSource.getName();
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return dataSource.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return dataSource.getNumMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval< D > getDataSource( final int t, final int level )
	{
		return dataSource.getDataSource( t, level );
	}

	@Override
	public RealRandomAccessible< D > getInterpolatedDataSource( final int t, final int level, final Interpolation method )
	{
		return dataSource.getInterpolatedDataSource( t, level, method );
	}

	@Override
	public D getDataType()
	{
		return dataSource.getDataType();
	}

}
