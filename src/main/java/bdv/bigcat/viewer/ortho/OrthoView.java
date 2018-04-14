package bdv.bigcat.viewer.ortho;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.ViewerActor;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.ViewerPanelInOrthoView;
import bdv.bigcat.viewer.panel.ViewerPanelInOrthoView.ViewerAxis;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import net.imglib2.realtransform.AffineTransform3D;

public class OrthoView extends GridPane
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final Class< ? >[] FOCUS_KEEPERS = { TextField.class };

	private final ViewerPanelFX viewerTopLeft;

	private final ViewerPanelFX viewerTopRight;

	private final ViewerPanelFX viewerBottomLeft;

	private final HashMap< ViewerPanelFX, ViewerPanelInOrthoView > viewerPanelToInOrthoView = new HashMap<>();

	private final GridResizer resizer;

	private final OrthoViewState state;

	private final ObservableList< ViewerActor > viewerActors = FXCollections.observableArrayList();
	{
		viewerActors.addListener( ( ListChangeListener< ViewerActor > ) c -> {
			while ( c.next() )
				if ( c.wasAdded() )
					for ( final ViewerActor actor : c.getAddedSubList() )
						viewerPanelToInOrthoView.keySet().forEach( actor.onAdd()::accept );
				else if ( c.wasRemoved() )
					for ( final ViewerActor actor : c.getRemoved() )
						viewerPanelToInOrthoView.keySet().forEach( actor.onRemove()::accept );
		} );
	}

	private final Consumer< ViewerPanelFX > onFocusEnter;

	private final Consumer< ViewerPanelFX > onFocusExit;

	private final KeyTracker keyTracker;

	public OrthoView( final SharedQueue cellCache, final KeyTracker keyTracker )
	{
		this( new OrthoViewState(), cellCache, keyTracker );
	}

	public OrthoView( final ViewerOptions viewerOptions, final SharedQueue cellCache, final KeyTracker keyTracker )
	{
		this( new OrthoViewState( viewerOptions ), cellCache, keyTracker );
	}

	public OrthoView( final OrthoViewState state, final SharedQueue cellCache, final KeyTracker keyTracker )
	{
		this( ( vp ) -> {}, ( vp ) -> {}, state, cellCache, keyTracker );
	}

	public OrthoView(
			final Consumer< ViewerPanelFX > onFocusEnter,
			final Consumer< ViewerPanelFX > onFocusExit,
			final OrthoViewState state,
			final SharedQueue cellCache,
			final KeyTracker keyTracker )
	{
		super();

		this.viewerTopLeft = new ViewerPanelFX( 1, cellCache, state.viewerOptions, s -> Interpolation.NEARESTNEIGHBOR );
		this.viewerTopRight = new ViewerPanelFX( 1, cellCache, state.viewerOptions, s -> Interpolation.NEARESTNEIGHBOR );
		this.viewerBottomLeft = new ViewerPanelFX( 1, cellCache, state.viewerOptions, s -> Interpolation.NEARESTNEIGHBOR );

		this.state = state;

		this.state.constraintsManager.manageGrid( this );
		this.onFocusEnter = onFocusEnter;
		this.onFocusExit = onFocusExit;
//		this.setInfoNode( new Label( "Place your node here!" ) );

		this.resizer = new GridResizer( this.state.constraintsManager, 10, this, keyTracker );
		this.keyTracker = keyTracker;
		this.setOnMouseMoved( resizer.onMouseMovedHandler() );
		this.setOnMouseDragged( resizer.onMouseDraggedHandler() );
		this.setOnMouseClicked( resizer.onMouseDoubleClickedHandler() );
		this.setOnMousePressed( resizer.onMousePressedHandler() );
		this.setOnMouseReleased( resizer.onMouseReleased() );

		this.setVgap( 1.0 );
		this.setHgap( 1.0 );

		this.addEventFilter( MouseEvent.DRAG_DETECTED, event -> {
			if ( this.resizer.isDraggingPanel() )
				event.consume();
		} );

		this.addEventHandler( KeyEvent.KEY_TYPED, event -> {
			if ( event.getCharacter().equals( "f" ) )
				maximizeActiveOrthoView( event );
		} );

		layoutViewers( cellCache );
		this.focusTraversableProperty().set( true );

	}

	private void layoutViewers( final SharedQueue cellCache )
	{
		addViewer( viewerTopLeft, ViewerAxis.Z, 0, 0 );
		addViewer( viewerTopRight, ViewerAxis.Y, 0, 1 );
		addViewer( viewerBottomLeft, ViewerAxis.X, 1, 0 );
	}

	public void setInfoNode( final Node node )
	{
		for ( final Node child : this.getChildren() )
			if ( GridPane.getRowIndex( child ) == 1 && GridPane.getColumnIndex( child ) == 1 )
			{
				this.getChildren().remove( child );
				break;
			}
		this.add( node, 1, 1 );
	}

	public synchronized void addSourcesListener( final ListChangeListener< SourceAndConverter< ? > > listener )
	{
		this.state.sacs.addListener( listener );
	}

	public synchronized void addActor( final ViewerActor actor )
	{
		this.viewerActors.add( actor );
	}

	private void addViewerNodesHandler( final ViewerPanelInOrthoView viewerNode, final Class< ? >[] focusKeepers )
	{
		if ( LOG.isDebugEnabled() )
			viewerNode.getViewer().focusedProperty().addListener( ( obs, o, n ) -> {
				LOG.debug( "Focusing {}", viewerNode );
			} );
		handleFocusEvent( viewerNode );
	}

	private synchronized void handleFocusEvent( final ViewerPanelInOrthoView viewerNode )
	{
		viewerNode.getViewer().focusedProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> {
			final ViewerPanelFX viewer = viewerNode.getViewer();
			if ( newValue && !oldValue )
				this.onFocusEnter.accept( viewer );
			else if ( !newValue )
				this.onFocusExit.accept( viewer );
		} );
	}

	private synchronized void addViewer(
			final ViewerPanelFX viewer,
			final ViewerAxis axis,
			final int rowIndex,
			final int colIndex )
	{
		final ViewerPanelInOrthoView viewerNode = new ViewerPanelInOrthoView(
				viewer,
				this.state.globalTransform,
				axis,
				keyTracker );
//		final ViewerNode viewerNode = new ViewerNode( new CacheControl.Dummy(), axis, this.state.viewerOptions, activeKeys );
		this.viewerPanelToInOrthoView.put( viewer, viewerNode );
		viewerNode.getViewerState().setSources( state.sacs, state.interpolation );
		viewerNode.getViewerState().setGlobalTransform( this.state.globalTransform );
//		viewerNode.manager().zoomSpeedProperty().bind( state.zoomSpeedProperty() );
//		viewerNode.manager().rotationSpeedProperty().bind( state.rotationSpeedProperty() );
//		viewerNode.manager().translationSpeedProperty().bind( state.translationSpeedProperty() );
//		viewerNode.manager().allowRotationsProperty().bind( this.state.allowRotationsProperty() );
		viewerActors.forEach( actor -> actor.onAdd().accept( viewerNode.getViewer() ) );
		addViewerNodesHandler( viewerNode, FOCUS_KEEPERS );
		this.state.timeProperty().addListener( ( obs, oldv, newv ) -> viewerNode.getViewer().setTimepoint( newv.intValue() ) );

		this.add( viewerNode.getViewer(), rowIndex, colIndex );
	}

	private void maximizeActiveOrthoView( final Event event )
	{
		final Scene scene = getScene();
		final Node focusOwner = scene.focusOwnerProperty().get().getParent();
		boolean is3DNode = false;

		// check if the focus is on the 3d viewer
		for ( final Node child : this.getChildren() )
			if ( GridPane.getRowIndex( child ) == 1 && GridPane.getColumnIndex( child ) == 1 )
				if ( child.equals( focusOwner ) )
					is3DNode = true;

		if ( viewerPanelToInOrthoView.keySet().contains( focusOwner ) || is3DNode )
			// event.consume();
			if ( !this.state.constraintsManager.isFullScreen() )
			{
				this.getChildren().forEach( node -> node.setVisible( node == focusOwner ) );
				this.state.constraintsManager.maximize(
						GridPane.getRowIndex( focusOwner ),
						GridPane.getColumnIndex( focusOwner ),
						0 );
//					( ( ViewerPanel ) ( ( SwingNode ) focusOwner ).getContent() ).requestRepaint();
				this.setHgap( 0 );
				this.setVgap( 0 );
			}
			else
			{
				this.state.constraintsManager.resetToLast();
				this.getChildren().forEach( node -> node.setVisible( true ) );
				this.viewerPanelToInOrthoView.keySet().forEach( node -> node.requestRepaint() );
				this.setHgap( 1 );
				this.setVgap( 1 );
			}
	}

	public void setTransform( final AffineTransform3D transform )
	{
		this.state.globalTransform.setTransform( transform );
	}

	public OrthoViewState getState()
	{
		return this.state;
	}

	public void requestRepaint()
	{
		this.viewerPanelToInOrthoView.keySet().forEach( ViewerPanelFX::requestRepaint );
	}

	public ViewerPanelFX viewerTopLeft()
	{
		return this.viewerTopLeft;
	}

	public ViewerPanelFX viewerTopRight()
	{
		return this.viewerTopRight;
	}

	public ViewerPanelFX viewerBottomLeft()
	{
		return this.viewerBottomLeft;
	}

	public ViewerPanelInOrthoView getInOrthoViewManager( final ViewerPanelFX viewer )
	{
		return this.viewerPanelToInOrthoView.get( viewer );
	}
}
