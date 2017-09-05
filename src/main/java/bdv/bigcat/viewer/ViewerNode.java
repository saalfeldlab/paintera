package bdv.bigcat.viewer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.cache.CacheControl;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import javafx.collections.ListChangeListener;
import javafx.embed.swing.SwingNode;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;

public class ViewerNode extends SwingNode implements ListChangeListener< SourceAndConverter< ? > >
{

	public enum ViewerAxis
	{
		X, Y, Z
	};

	private ViewerPanel viewer;

	private final ViewerTransformManager manager;

	private ViewerAxis viewerAxis;

	private final CacheControl cacheControl;

	private final HashMap< Source< ? >, Boolean > visibility = new HashMap<>();

	private boolean managesOwnLayerVisibility = false;

	private final InputActionBindings keybindings = new InputActionBindings();

	private final TriggerBehaviourBindings triggerbindings = new TriggerBehaviourBindings();

	private final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();

	private final Behaviours behaviours = new Behaviours( inputTriggerConfig );

	private final Actions actions = new Actions( inputTriggerConfig );

	private final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();

	private boolean isReady = false;

	private ViewerPanelState state;

	public ViewerNode( final CacheControl cacheControl, final ViewerAxis viewerAxis, final GlobalTransformManager manager, final ViewerOptions viewerOptions )
	{
		this.viewer = null;
		this.cacheControl = cacheControl;
		this.viewerAxis = viewerAxis;
		this.manager = new ViewerTransformManager( manager, globalToViewer( viewerAxis ) );
		initialize( viewerOptions );
	}

	public ViewerTransformManager manager()
	{
		return manager;
	}

	private void setViewer( final ViewerPanel viewer )
	{
		this.viewer = viewer;
	}

	private void setReady( final boolean ready )
	{
		isReady = ready;
	}

	public boolean isReady()
	{
		return isReady;
	}

	public void addBehaviour( final Behaviour behaviour, final String name, final String... defaultTriggers )
	{
		this.behaviours.behaviour( behaviour, name, defaultTriggers );
	}

	public void addAction( final AbstractNamedAction action, final String... defaultKeyStrokes )
	{
		this.actions.namedAction( action, defaultKeyStrokes );
	}

	public void addAction( final Runnable action, final String name, final String... defaultKeyStrokes )
	{
		this.actions.runnableAction( action, name, defaultKeyStrokes );
	}

	public void addMouseMotionListener( final MouseMotionListener listener )
	{
		waitUntilInitialized();
		this.viewer.getDisplay().addMouseMotionListener( listener );
	}

	public void removeMouseMotionListener( final MouseMotionListener listener )
	{
		waitUntilInitialized();
		this.viewer.getDisplay().removeMouseMotionListener( listener );
	}

	public void setViewerPanelState( final ViewerPanelState state )
	{
		if ( this.viewer != null )
		{
			if ( this.state != null )
				this.state.removeViewer( viewer );
			this.state = state;
			if ( !this.state.isViewerInstalled( viewer ) )
				this.state.installViewer( viewer );
		}
	}

	private void waitUntilInitialized()
	{
		while ( this.viewer == null )
			try
			{
				Thread.sleep( 10 );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	private void initialize( final ViewerOptions viewerOptions )
	{
		SwingUtilities.invokeLater( () -> {
			final ViewerPanel vp = new ViewerPanel( new ArrayList<>(), 1, cacheControl, viewerOptions );
			setViewer( vp );

			viewer.setDisplayMode( DisplayMode.FUSED );
			viewer.setMinimumSize( new Dimension( 100, 100 ) );
			viewer.setPreferredSize( new Dimension( 100, 100 ) );
			viewer.setFocusTraversalKeysEnabled( false );
			this.setFocusTraversable( false );
			// how to make ctrl tab work here?
			// http://www.davidc.net/programming/java/how-make-ctrl-tab-switch-tabs-jtabbedpane&usg=AFQjCNHZr2pQpE3cxIn7G6zYt3gZZcHSug

//			{
//				final KeyStroke ctrlTab = KeyStroke.getKeyStroke( "ctrl TAB" );
//				final Set< AWTKeyStroke > fwdKeys = vp.getFocusTraversalKeys( KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS );
//				fwdKeys.remove( ctrlTab );
//				vp.setFocusTraversalKeys( KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, fwdKeys );
//			}

//			{
//				final KeyStroke ctrlShiftTab = KeyStroke.getKeyStroke( "ctrl shift TAB" );
//				final Set< AWTKeyStroke > bckKeys = vp.getFocusTraversalKeys( KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS );
//				bckKeys.remove( ctrlShiftTab );
//				vp.setFocusTraversalKeys( KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, bckKeys );
//			}
//			.remove( ctrlTab );

			this.setContent( viewer );

			viewer.getDisplay().setTransformEventHandler( this.manager );
			this.manager.install( triggerbindings, keybindings );

			triggerbindings.addBehaviourMap( "default", behaviours.getBehaviourMap() );
			triggerbindings.addInputTriggerMap( "default", behaviours.getInputTriggerMap() );
			keybindings.addActionMap( "default", actions.getActionMap() );
			keybindings.addInputMap( "default", actions.getInputMap() );

			mouseAndKeyHandler.setInputMap( triggerbindings.getConcatenatedInputTriggerMap() );
			mouseAndKeyHandler.setBehaviourMap( triggerbindings.getConcatenatedBehaviourMap() );
			viewer.getDisplay().addHandler( mouseAndKeyHandler );
			viewer.getDisplay().addOverlayRenderer( new OverlayRenderer()
			{

				private int w, h;

				@Override
				public void setCanvasSize( final int width, final int height )
				{
					w = width;
					h = height;

				}

				@Override
				public void drawOverlays( final Graphics g )
				{

					final Color c = java.awt.Color.WHITE;
					g.setColor( new Color( c.getRed(), c.getGreen(), c.getBlue(), 127 ) );
					g.drawLine( 0, h / 2, w, h / 2 );
					g.drawLine( w / 2, 0, w / 2, h );

				}
			} );
			SwingUtilities.replaceUIActionMap( viewer.getRootPane(), keybindings.getConcatenatedActionMap() );
			SwingUtilities.replaceUIInputMap( viewer.getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );
			setReady( true );
			viewer.setVisible( true );
			this.manager.setTransformListener( viewer );
			this.manager.setViewer( viewer );
		} );
	}

	public static AffineTransform3D globalToViewer( final ViewerAxis axis )
	{

		final AffineTransform3D tf = new AffineTransform3D();
		switch ( axis )
		{
		case Z:
			break;
		case Y:
			tf.rotate( 1, Math.PI / 2 );
			break;
		case X:
			tf.rotate( 0, -Math.PI / 2 );
			break;
		}
		return tf;
	}

	@Override
	public void onChanged( final javafx.collections.ListChangeListener.Change< ? extends SourceAndConverter< ? > > c )
	{
		c.next();
		if ( c.wasRemoved() )
			c.getRemoved().forEach( removed -> {
				visibility.remove( removed );
				viewer.removeSource( removed.getSpimSource() );
			} );
		else if ( c.wasAdded() )
			c.getAddedSubList().forEach( added -> {
				visibility.put( added.getSpimSource(), true );
				viewer.addSource( added );
				final int numSources = viewer.getState().numSources();
				if ( numSources > 1 )
					viewer.getVisibilityAndGrouping().setCurrentSource( 1 );
			} );
	}

	public void manageOwnLayerVisibility( final boolean manageVisibility )
	{
		this.managesOwnLayerVisibility = manageVisibility;
	}

	public boolean manageOwnLayerVisibility()
	{
		return managesOwnLayerVisibility;
	}

	public void setViewerAxis( final ViewerAxis axis )
	{
		this.viewerAxis = axis;
		this.manager.setGlobalToViewer( globalToViewer( axis ) );
	}

	public AffineTransform3D getTransformCopy()
	{
		return this.manager.getTransform();
	}

	public InputTriggerConfig inputTriggerConfig()
	{
		return inputTriggerConfig;
	}

	@Override
	public double minWidth( final double height )
	{
		return getContent() == null ? 0 : getContent().getMinimumSize().getWidth();
	}

	@Override
	public double minHeight( final double width )
	{
		return getContent() == null ? 0 : getContent().getMinimumSize().getHeight();
	}

	@Override
	public double maxWidth( final double height )
	{
		return getContent() == null ? 100 : getContent().getMaximumSize().getWidth();
	}

	@Override
	public double maxHeight( final double width )
	{
		return getContent() == null ? 100 : getContent().getMaximumSize().getHeight();
	}

	@Override
	public double prefWidth( final double height )
	{
		return getContent() == null ? 10 : getContent().getPreferredSize().getWidth();
	}

	@Override
	public double prefHeight( final double width )
	{
		return getContent() == null ? 10 : getContent().getPreferredSize().getHeight();
	}

}
