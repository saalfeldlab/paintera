package bdv.bigcat.viewer;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public abstract class ARGBColorConverter< R extends RealType< R > > implements ColorConverter, Converter< R, ARGBType >
{
	protected final DoubleProperty alpha = new SimpleDoubleProperty( 1.0 );

	protected final DoubleProperty min = new SimpleDoubleProperty( 0.0 );

	protected final DoubleProperty max = new SimpleDoubleProperty( 1.0 );

	protected final ObjectProperty< ARGBType > color = new SimpleObjectProperty<>( new ARGBType( ARGBType.rgba( 255, 255, 255, 255 ) ) );

	protected int A;

	protected double scaleR;

	protected double scaleG;

	protected double scaleB;

	protected int black;

	public ARGBColorConverter( final double min, final double max )
	{
		this.min.set( min );
		this.max.set( max );

		this.min.addListener( ( obs, oldv, newv ) -> update() );
		this.max.addListener( ( obs, oldv, newv ) -> update() );
		this.color.addListener( ( obs, oldv, newv ) -> update() );
		this.alpha.addListener( ( obs, oldv, newv ) -> update() );

		update();
	}

	public DoubleProperty minProperty()
	{
		return min;
	}

	public DoubleProperty maxProperty()
	{
		return max;
	}

	public ObjectProperty< ARGBType > colorProperty()
	{
		return color;
	}

	public DoubleProperty alphaProperty()
	{
		return this.alpha;
	}

	@Override
	public ARGBType getColor()
	{
		return color.get().copy();
	}

	@Override
	public void setColor( final ARGBType c )
	{
		color.set( c );
	}

	@Override
	public boolean supportsColor()
	{
		return true;
	}

	@Override
	public double getMin()
	{
		return min.get();
	}

	@Override
	public double getMax()
	{
		return max.get();
	}

	@Override
	public void setMax( final double max )
	{
		this.max.set( max );
	}

	@Override
	public void setMin( final double min )
	{
		this.min.set( min );
	}

	private void update()
	{
		final double scale = 1.0 / ( max.get() - min.get() );
		final int value = color.get().get();
		A = ( int ) Math.min( Math.max( Math.round( 255 * alphaProperty().get() ), 0 ), 255 );
		scaleR = ARGBType.red( value ) * scale;
		scaleG = ARGBType.green( value ) * scale;
		scaleB = ARGBType.blue( value ) * scale;
		black = ARGBType.rgba( 0, 0, 0, A );
	}

	public static class Imp0< R extends RealType< R > > extends ARGBColorConverter< R >
	{
		public Imp0( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final R input, final ARGBType output )
		{
			final double v = input.getRealDouble() - min.get();
			if ( v < 0 )
				output.set( black );
			else
			{
				final int r0 = ( int ) ( scaleR * v + 0.5 );
				final int g0 = ( int ) ( scaleG * v + 0.5 );
				final int b0 = ( int ) ( scaleB * v + 0.5 );
				final int r = Math.min( 255, r0 );
				final int g = Math.min( 255, g0 );
				final int b = Math.min( 255, b0 );
				output.set( ARGBType.rgba( r, g, b, A ) );
			}
		}
	}

	public static class Imp1< R extends RealType< R > > extends ARGBColorConverter< R >
	{
		public Imp1( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final R input, final ARGBType output )
		{
			final double v = input.getRealDouble() - min.get();
			if ( v < 0 )
				output.set( black );
			else
			{
				final int r0 = ( int ) ( scaleR * v + 0.5 );
				final int g0 = ( int ) ( scaleG * v + 0.5 );
				final int b0 = ( int ) ( scaleB * v + 0.5 );
				final int r = Math.min( 255, r0 );
				final int g = Math.min( 255, g0 );
				final int b = Math.min( 255, b0 );
				output.set( ARGBType.rgba( r, g, b, A ) );
			}
		}
	}

	public static class InvertingImp0< R extends RealType< R > > extends ARGBColorConverter< R >
	{

		public InvertingImp0( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final R input, final ARGBType output )
		{
			final double v = input.getRealDouble() - min.get();
			final int r0 = ( int ) ( scaleR * v + 0.5 );
			final int g0 = ( int ) ( scaleG * v + 0.5 );
			final int b0 = ( int ) ( scaleB * v + 0.5 );
			final int r = Math.min( 255, Math.max( r0, 0 ) );
			final int g = Math.min( 255, Math.max( g0, 0 ) );
			final int b = Math.min( 255, Math.max( b0, 0 ) );
			output.set( ARGBType.rgba( r, g, b, A ) );
		}

	}

	public static class InvertingImp1< R extends RealType< R > > extends ARGBColorConverter< R >
	{

		public InvertingImp1( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final R input, final ARGBType output )
		{
			final double v = input.getRealDouble() - min.get();
			final int r0 = ( int ) ( scaleR * v + 0.5 );
			final int g0 = ( int ) ( scaleG * v + 0.5 );
			final int b0 = ( int ) ( scaleB * v + 0.5 );
			final int r = Math.min( 255, Math.max( r0, 0 ) );
			final int g = Math.min( 255, Math.max( g0, 0 ) );
			final int b = Math.min( 255, Math.max( b0, 0 ) );
			output.set( ARGBType.rgba( r, g, b, A ) );
		}

	}
}
