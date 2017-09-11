package bdv.bigcat.viewer.atlas;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.viewer.ViewerPanel;
import javafx.application.Platform;
import javafx.scene.control.Label;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class AtlasMouseCoordinatePrinter
{

	private class Listener implements MouseMotionListener, TransformListener< AffineTransform3D >
	{

		private final ViewerPanel viewer;

		private int x, y;

		private final double[] pos = new double[ 3 ];

		private final RealPoint p = RealPoint.wrap( pos );

		private Listener( final ViewerPanel viewer )
		{
			this.viewer = viewer;
		}

		private final void updateStatusBar()
		{
			viewer.displayToGlobalCoordinates( x, y, p );
			Platform.runLater( () -> statusBar.setText( String.format( "(%.3f, %.3f, %.3f)", pos[ 0 ], pos[ 1 ], pos[ 2 ] ) ) );
		}

		@Override
		public void transformChanged( final AffineTransform3D transform )
		{
			updateStatusBar();
		}

		@Override
		public void mouseDragged( final MouseEvent arg0 )
		{

		}

		@Override
		public void mouseMoved( final MouseEvent e )
		{
			this.x = e.getX();
			this.y = e.getY();
			updateStatusBar();
		}

	}

	private final Label statusBar;

	public AtlasMouseCoordinatePrinter( final Label statusBar )
	{
		super();
		this.statusBar = statusBar;
	}

	private final HashMap< ViewerPanel, Listener > listeners = new HashMap<>();

	private final Function< ViewerPanel, Listener > generator = Listener::new;

	public Consumer< ViewerPanel > onEnter()
	{
		return t -> {
			this.listeners.put( t, generator.apply( t ) );
			final Listener listener = this.listeners.get( t );
			t.getDisplay().addMouseMotionListener( listener );
			t.addTransformListener( listener );
		};
	}

	public Consumer< ViewerPanel > onExit()
	{
		return t -> {
			final Listener listener = this.listeners.get( t );
			t.getDisplay().removeMouseMotionListener( listener );
			t.removeTransformListener( listener );
			if ( statusBar != null )
				statusBar.setText( "(---.---, ---.---, ---.---)" );
		};
	}

}
