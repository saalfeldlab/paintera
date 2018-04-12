package bdv.bigcat.viewer.atlas.control.navigation;

import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.beans.value.ObservableDoubleValue;
import net.imglib2.realtransform.AffineTransform3D;

public class Zoom
{

	private final ObservableDoubleValue speed;

	private final AffineTransform3D global = new AffineTransform3D();

	private final AffineTransform3D concatenated;

	private final GlobalTransformManager manager;

	private final Object lock;

	public Zoom(
			final ObservableDoubleValue speed,
			final GlobalTransformManager manager,
			final AffineTransform3D concatenated,
			final Object lock )
	{
		this.speed = speed;
		this.manager = manager;
		this.concatenated = concatenated;
		this.lock = lock;

		this.manager.addListener( global::set );
	}

	public void zoomCenteredAt( final double delta, final double x, final double y )
	{
		final AffineTransform3D global = new AffineTransform3D();
		synchronized ( lock )
		{
			global.set( this.global );
		}
		final double[] location = new double[] { x, y, 0 };
		concatenated.applyInverse( location, location );
		global.apply( location, location );

		final double dScale = speed.get();
		final double scale = delta > 0 ? 1.0 / dScale : dScale;

		for ( int d = 0; d < location.length; ++d )
			global.set( global.get( d, 3 ) - location[ d ], d, 3 );
		global.scale( scale );
		for ( int d = 0; d < location.length; ++d )
			global.set( global.get( d, 3 ) + location[ d ], d, 3 );

		manager.setTransform( global );
	}
}
