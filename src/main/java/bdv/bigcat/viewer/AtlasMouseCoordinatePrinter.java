package bdv.bigcat.viewer;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.viewer.ViewerPanel;
import javafx.application.Platform;
import javafx.scene.control.Label;
import net.imglib2.RealPoint;

public class AtlasMouseCoordinatePrinter
{
	private final Label statusBar;

	public AtlasMouseCoordinatePrinter( final Label statusBar )
	{
		super();
		this.statusBar = statusBar;
	}

	private final HashMap< ViewerPanel, MouseMotionListener > listeners = new HashMap<>();

	private final Function< ViewerPanel, MouseMotionListener > generator = vp -> {
		return new MouseMotionListener()
		{

			@Override
			public void mouseMoved( final MouseEvent e )
			{
				final double[] pos = new double[ 3 ];
				final RealPoint p = RealPoint.wrap( pos );
				vp.displayToGlobalCoordinates( e.getX(), e.getY(), p );
				Platform.runLater( () -> statusBar.setText( String.format( "(%.3f, %.3f, %.3f)", pos[ 0 ], pos[ 1 ], pos[ 2 ] ) ) );
			}

			@Override
			public void mouseDragged( final MouseEvent e )
			{
				// TODO Auto-generated method stub

			}
		};
	};

	public Consumer< ViewerPanel > onEnter()
	{
		return t -> {
			System.out.println( "ENTERING!" );
			this.listeners.put( t, generator.apply( t ) );
			t.getDisplay().addMouseMotionListener( this.listeners.get( t ) );
		};
	}

	public Consumer< ViewerPanel > onExit()
	{
		return t -> {
			System.out.println( "EXITING!" );
			t.getDisplay().removeMouseMotionListener( this.listeners.get( t ) );
			if ( statusBar != null )
				statusBar.setText( "" );
		};
	}

}
