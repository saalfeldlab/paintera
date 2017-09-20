package bdv.bigcat.viewer;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.BiFunction;

public class GradientBackgroundAlpha implements BiFunction< Integer, Integer, Optional< BufferedImage > >
{

	@Override
	public Optional< BufferedImage > apply( final Integer t, final Integer u )
	{
		final int width = t;
		final int height = u;
		final int size = width * height;
		final double factor = 255.0 / size;
		final BufferedImage bi = new BufferedImage( width, height, BufferedImage.TYPE_INT_ARGB_PRE );
//		final int[] data = new int[ size ];
		for ( int i = 0, y = 0; y < height; ++y )
			for ( int x = 0; x < width; ++x, ++i )
			{
				final int val = 255 - ( int ) ( y * x * factor ) << 24;
//				data[ i ] = val;
				bi.setRGB( x, y, val );
			}
//		bi.setRGB( 0, 0, width, height, data, 0, 1 );
		return Optional.of( bi );
	}

}
