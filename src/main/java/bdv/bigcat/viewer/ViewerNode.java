package bdv.bigcat.viewer;

import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.bigcat.viewer.source.SourceLayer;
import bdv.cache.CacheControl;
import bdv.viewer.DisplayMode;
import bdv.viewer.ViewerPanel;
import javafx.collections.ListChangeListener;
import javafx.embed.swing.SwingNode;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;

public class ViewerNode extends SwingNode implements ListChangeListener< SourceLayer >
{

	public enum ViewerAxis
	{
		X, Y, Z
	};

	private ViewerPanel viewer;

	private final ViewerTransformManager manager;

	private ViewerAxis viewerAxis;

	private final CacheControl cacheControl;

	private final HashMap< SourceLayer, Boolean > visibility = new HashMap<>();

	private boolean managesOwnLayerVisibility = false;

	private final InputActionBindings keybindings = new InputActionBindings();

	private final TriggerBehaviourBindings triggerbindings = new TriggerBehaviourBindings();

	private final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();

	final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();

	private boolean isReady = false;

	public ViewerNode( final CacheControl cacheControl, final ViewerAxis viewerAxis, final GlobalTransformManager manager )
	{
		this.viewer = null;
		this.cacheControl = cacheControl;
		this.viewerAxis = viewerAxis;
		this.manager = new ViewerTransformManager( manager, globalToViewer( viewerAxis ), viewer );
		initialize();
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

	public void initialize()
	{
		SwingUtilities.invokeLater( () -> {
			final ViewerPanel vp = new ViewerPanel( new ArrayList<>(), 1, cacheControl );
			setViewer( vp );

			this.setContent( viewer );

			viewer.setDisplayMode( DisplayMode.FUSED );
			viewer.setMinimumSize( new Dimension( 100, 100 ) );
			viewer.setPreferredSize( new Dimension( 100, 100 ) );

			viewer.getDisplay().setTransformEventHandler( this.manager );
			this.manager.install( triggerbindings );

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

					g.setColor( java.awt.Color.RED );
					g.drawLine( 0, h / 2, w, h / 2 );
					g.drawLine( w / 2, 0, w / 2, h );

				}
			} );
			SwingUtilities.replaceUIActionMap( viewer.getRootPane(), keybindings.getConcatenatedActionMap() );
			SwingUtilities.replaceUIInputMap( viewer.getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );
			setReady( true );
			viewer.setVisible( true );
			this.manager.setTransformListener( viewer );
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
	public void onChanged( final javafx.collections.ListChangeListener.Change< ? extends SourceLayer > c )
	{
		c.next();
		if ( c.wasRemoved() )
			c.getRemoved().forEach( removed -> {
				visibility.remove( removed );
				viewer.removeSource( removed.getSourceAndConverter().getSpimSource() );
			} );
		else if ( c.wasAdded() )
			c.getAddedSubList().forEach( added -> {
				visibility.put( added, added.isActive() );
				viewer.addSource( added.getSourceAndConverter() );
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

}
