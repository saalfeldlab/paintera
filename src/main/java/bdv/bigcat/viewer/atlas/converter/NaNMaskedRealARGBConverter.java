package bdv.bigcat.viewer.atlas.converter;

import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.volatiles.VolatileRealType;

public class NaNMaskedRealARGBConverter< T extends RealType< T > > extends RealARGBConverter< T >
{

	public NaNMaskedRealARGBConverter( final double min, final double max )
	{
		super( min, max );
	}

	@Override
	public void convert( final T input, final ARGBType output )
	{
		if ( Double.isNaN( input.getRealDouble() ) )
			output.set( 0x00000000 );
		else
			super.convert( input, output );
	}

	public static void main( final String[] args )
	{
		final Converter< VolatileRealType< DoubleType >, ARGBType > conv = new NaNMaskedRealARGBConverter<>( 0, 255 );
	}

}
