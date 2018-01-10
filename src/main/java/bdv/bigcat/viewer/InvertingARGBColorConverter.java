package bdv.bigcat.viewer;

import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class InvertingARGBColorConverter< R extends RealType< R > > extends RealARGBColorConverter< R >
{

	public InvertingARGBColorConverter( final double min, final double max )
	{
		super( min, max );
	}

	@Override
	public void convert( final R input, final ARGBType output )
	{
		final double v = input.getRealDouble() - min;
		final int r0 = ( int ) ( scaleR * v + 0.5 );
		final int g0 = ( int ) ( scaleG * v + 0.5 );
		final int b0 = ( int ) ( scaleB * v + 0.5 );
		final int r = Math.min( 255, Math.max( r0, 0 ) );
		final int g = Math.min( 255, Math.max( g0, 0 ) );
		final int b = Math.min( 255, Math.max( b0, 0 ) );
		output.set( ARGBType.rgba( r, g, b, A ) );
	}

}
