package bdv.bigcat.viewer.atlas.data;

import java.util.Arrays;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealDoubleConverter;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.volatiles.VolatileRealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class ConvertedSource< T, U extends NumericType< U > > implements Source< U >
{

	public static < T extends RealType< T > > Source< DoubleType > realTypeAsDoubleType( final Source< T > source )
	{
		return new ConvertedSource<>( source, new DoubleType( Double.NaN ), new RealDoubleConverter<>(), source.getName() );
	}

	public static < T extends RealType< T >, V extends Volatile< T > > Source< VolatileRealType< DoubleType > > volatileRealTypeAsDoubleType( final Source< V > source )
	{
		final RealDoubleConverter< T > c = new RealDoubleConverter<>();
		final Converter< V, VolatileRealType< DoubleType > > conv = ( s, t ) -> {
			final boolean isValid = s.isValid();
			t.setValid( isValid );
			if ( isValid )
				c.convert( s.get(), t.get() );
		};
		return new ConvertedSource<>( source, new VolatileRealType<>( new DoubleType( Double.NaN ), true ), conv, source.getName() );
	}

	private final Source< T > source;

	private final U extension;

	private final Converter< T, U > converter;

	private final String name;

	public ConvertedSource( final Source< T > source, final U extension, final Converter< T, U > converter, final String name )
	{
		super();
		this.source = source;
		this.extension = extension;
		this.converter = converter;
		this.name = name;
	}

	@Override
	public boolean isPresent( final int t )
	{
		return source.isPresent( t );
	}

	@Override
	public RandomAccessibleInterval< U > getSource( final int t, final int level )
	{
		return Converters.convert( source.getSource( t, level ), converter, extension.createVariable() );
	}

	@Override
	public RealRandomAccessible< U > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		final RandomAccessibleInterval< U > intervalSource = getSource( t, level );
		final IntervalView< U > extendedBorderByOne = Views.interval(
				Views.extendBorder( intervalSource ),
				Intervals.minAsLongArray( intervalSource ),
				Arrays.stream( Intervals.maxAsLongArray( intervalSource ) ).map( m -> m + 1 ).toArray() );
		return Views.interpolate( Views.extendValue( extendedBorderByOne, extension ), method.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>() );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		source.getSourceTransform( t, level, transform );
	}

	@Override
	public U getType()
	{
		return extension.createVariable();
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return source.getNumMipmapLevels();
	}

}
