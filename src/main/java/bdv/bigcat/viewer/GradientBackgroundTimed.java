package bdv.bigcat.viewer;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class GradientBackgroundTimed implements BiFunction< Integer, Integer, Optional< BufferedImage > >
{

	private final Timer timer = new Timer();
	{
		timer.schedule( new TimerTask()
		{

			@Override
			public void run()
			{
				int old = shift.get();
				shift.set( ( old += 8 ) % 24 );

			}
		}, 0, 100 );
	}

	private final AtomicInteger shift = new AtomicInteger( 0 );

	@Override
	public Optional< BufferedImage > apply( final Integer t, final Integer u )
	{
		final int bitShift = shift.get();
		final int width = t;
		final int height = u;
		final int size = width * height;
		final double factor = 255.0 / size;
		final BufferedImage bi = new BufferedImage( width, height, BufferedImage.TYPE_INT_RGB );
		final int[] data = new int[ size ];
		for ( int i = 0, y = 0; y < height; ++y )
			for ( int x = 0; x < width; ++x, ++i )
			{
				final int val = ( int ) ( y * x * factor );;
				data[ i ] = val << bitShift;
				bi.setRGB( x, y, data[ i ] );
			}
//		bi.setRGB( 0, 0, width, height, data, 0, 1 );
		return Optional.of( bi );
	}

}
