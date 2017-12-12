package bdv.bigcat.viewer.panel;

import java.util.ArrayList;
import java.util.HashMap;

import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.transform.ViewerTransformManager;
import bdv.cache.CacheControl;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import net.imglib2.realtransform.AffineTransform3D;

public class ViewerNode extends Pane implements ListChangeListener< SourceAndConverter< ? > >
{

	public enum ViewerAxis
	{
		X, Y, Z
	};

	private final ViewerPanelFX viewer;

	private final ViewerState state;

	private final ViewerTransformManager manager;

	private ViewerAxis viewerAxis;

	private final HashMap< Source< ? >, Boolean > visibility = new HashMap<>();

	private boolean managesOwnLayerVisibility = false;

	private final CrossHair crosshair = new CrossHair();

	private final Color onFocusColor = Color.rgb( 255, 255, 255, 180.0 / 255.0 );

	private final Color outOfFocusColor = Color.rgb( 127, 127, 127, 100.0 / 255.0 );
	{
		crosshair.setColor( outOfFocusColor );
	}

	public ViewerNode(
			final CacheControl cacheControl,
			final ViewerAxis viewerAxis,
			final ViewerOptions viewerOptions,
			final KeyTracker keyTracker,
			final ObservableMap< Source< ? >, Boolean > visibilityMap )
	{
		super();
		this.viewer = new ViewerPanelFX( new ArrayList<>(), 1, cacheControl, viewerOptions );
		this.getChildren().add( this.viewer );
		this.setWidth( this.viewer.getWidth() );
		this.setHeight( this.viewer.getHeight() );
		this.setMinSize( 0, 0 );
		this.heightProperty().addListener( ( obs, oldv, newv ) -> viewer.setPrefHeight( getHeight() ) );
		this.widthProperty().addListener( ( obs, oldv, newv ) -> viewer.setPrefWidth( getWidth() ) );
		this.viewer.setMinSize( 0, 0 );
//		this.viewer.showMultibox( false );
		this.viewerAxis = viewerAxis;
		this.state = new ViewerState( this.viewer );
		this.manager = new ViewerTransformManager( this.viewer, viewerAxis, state, globalToViewer( viewerAxis ), keyTracker, visibilityMap );
		initializeViewer();
		addCrosshair();
//		https://stackoverflow.com/questions/21657034/javafx-keyevent-propagation-order
//		https://stackoverflow.com/questions/32802664/setonkeypressed-event-not-working-properly
//		Node only reacts to key events when focused!
		this.viewer.addEventFilter( MouseEvent.MOUSE_ENTERED, event -> {
			this.viewer.requestFocus();
		} );
		this.viewer.addEventFilter( MouseEvent.MOUSE_PRESSED, event -> {
			if ( !this.isFocused() )
				this.viewer.requestFocus();
		} );
		visibilityMap.addListener( ( MapChangeListener< Source< ? >, Boolean > ) change -> {
			if ( change.wasAdded() )
				getViewer().getVisibilityAndGrouping().setSourceActive( change.getKey(), change.getValueAdded() );
		} );
	}

	private void addCrosshair()
	{

		this.viewer.focusedProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> {
			if ( newValue )
				crosshair.setColor( onFocusColor );
			else
				crosshair.setColor( outOfFocusColor );
			viewer.getDisplay().drawOverlays();
		} );
	}

	public void setCrossHairColor( final int r, final int g, final int b, final int a )
	{
		this.crosshair.setColor( r, g, b, a );
	}

	public ViewerTransformManager manager()
	{
		return manager;
	}

	public void setViewerPanelState( final ViewerState state )
	{
		this.state.set( state );
	}

	private void initializeViewer()
	{
		viewer.setDisplayMode( DisplayMode.FUSED );
		this.manager.setCanvasSize( ( int ) viewer.getDisplay().getWidth(), ( int ) viewer.getDisplay().getHeight(), true );
		viewer.getDisplay().setTransformEventHandler( this.manager );
//		this.manager.install( triggerbindings, keybindings );
//		triggerbindings.addBehaviourMap( "default", behaviours.getBehaviourMap() );
//		triggerbindings.addInputTriggerMap( "default", behaviours.getInputTriggerMap() );
//		keybindings.addActionMap( "default", actions.getActionMap() );
//		keybindings.addInputMap( "default", actions.getInputMap() );
//		mouseAndKeyHandler.setInputMap( triggerbindings.getConcatenatedInputTriggerMap() );
//		mouseAndKeyHandler.setBehaviourMap( triggerbindings.getConcatenatedBehaviourMap() );
//		viewer.getDisplay().addHandler( mouseAndKeyHandler );
		viewer.getDisplay().addOverlayRenderer( crosshair );
	}

	public static AffineTransform3D globalToViewer( final ViewerAxis axis )
	{

		final AffineTransform3D tf = new AffineTransform3D();
		switch ( axis )
		{
		case Z:
			break;
		case Y:
			tf.rotate( 0, -Math.PI / 2 );
			break;
		case X:
			tf.rotate( 1, Math.PI / 2 );
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

	public ViewerState getViewerState()
	{
		return state;
	}

	public ViewerPanelFX getViewer()
	{
		return this.viewer;
	}

}
