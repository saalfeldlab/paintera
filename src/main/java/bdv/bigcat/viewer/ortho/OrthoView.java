package bdv.bigcat.viewer.ortho;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.ViewerActor;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.panel.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.panel.transform.ViewerTransformManager;
import bdv.util.volatiles.SharedQueue;
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

	private final HashSet< ViewerNode > viewerNodes = new HashSet<>();

	private final HashMap< ViewerNode, ViewerTransformManager > managers = new HashMap<>();

	private final GridResizer resizer;

	private final OrthoViewState state;

	private final ObservableList< ViewerActor > viewerActors = FXCollections.observableArrayList();
	{
		viewerActors.addListener( ( ListChangeListener< ViewerActor > ) c -> {
			while ( c.next() )
				if ( c.wasAdded() )
					for ( final ViewerActor actor : c.getAddedSubList() )
						viewerNodes.forEach( vn -> actor.onAdd().accept( vn.getViewer() ) );
				else if ( c.wasRemoved() )
					for ( final ViewerActor actor : c.getRemoved() )
						viewerNodes.forEach( vn -> actor.onRemove().accept( vn.getViewer() ) );
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
		addViewer( ViewerAxis.Z, 0, 0, cellCache );
		addViewer( ViewerAxis.Y, 0, 1, cellCache );
		addViewer( ViewerAxis.X, 1, 0, cellCache );
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

	private void addViewerNodesHandler( final ViewerNode viewerNode, final Class< ? >[] focusKeepers )
	{
		if ( LOG.isDebugEnabled() )
			viewerNode.focusedProperty().addListener( ( obs, o, n ) -> {
				LOG.debug( "Focusing {}", viewerNode );
			} );
		handleFocusEvent( viewerNode );
	}

	private synchronized void handleFocusEvent( final ViewerNode viewerNode )
	{
		viewerNode.getViewer().focusedProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> {
			final ViewerPanelFX viewer = viewerNode.getViewer();
			if ( newValue && !oldValue )
				this.onFocusEnter.accept( viewer );
			else if ( !newValue )
				this.onFocusExit.accept( viewer );
		} );
	}

	private synchronized void addViewer( final ViewerAxis axis, final int rowIndex, final int colIndex, final SharedQueue cellCache )
	{
		final ViewerNode viewerNode = new ViewerNode( cellCache, axis, this.state.viewerOptions, keyTracker );
//		final ViewerNode viewerNode = new ViewerNode( new CacheControl.Dummy(), axis, this.state.viewerOptions, activeKeys );
		this.viewerNodes.add( viewerNode );
		this.managers.put( viewerNode, viewerNode.manager() );
		viewerNode.getViewerState().setSources( state.sacs, state.interpolation );
		viewerNode.getViewerState().setGlobalTransform( this.state.globalTransform );
		viewerNode.manager().zoomSpeedProperty().bind( state.zoomSpeedProperty() );
		viewerNode.manager().rotationSpeedProperty().bind( state.rotationSpeedProperty() );
		viewerNode.manager().translationSpeedProperty().bind( state.translationSpeedProperty() );
		viewerNode.manager().allowRotationsProperty().bind( this.state.allowRotationsProperty() );
		viewerActors.forEach( actor -> actor.onAdd().accept( viewerNode.getViewer() ) );
		addViewerNodesHandler( viewerNode, FOCUS_KEEPERS );
		this.state.timeProperty().addListener( ( obs, oldv, newv ) -> viewerNode.getViewer().setTimepoint( newv.intValue() ) );

		this.add( viewerNode, rowIndex, colIndex );
//		viewerNode.setOnMouseClicked( resizer.onMouseDoubleClickedHandler() );
//		viewerNode.setOnMousePressed( resizer.onMousePressedHandler() );
//		viewerNode.setOnMouseDragged( resizer.onMouseDraggedHandler() );
//		viewerNode.setOnMouseMoved( resizer.onMouseMovedHandler() );
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

		if ( viewerNodes.contains( focusOwner ) || is3DNode )
			// event.consume();
			if ( !this.state.constraintsManager.isFullScreen() )
			{
				viewerNodes.forEach( node -> node.setVisible( node == focusOwner ) );
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
				viewerNodes.forEach( node -> node.setVisible( true ) );
				viewerNodes.forEach( node -> node.getViewer().requestRepaint() );
				this.setHgap( 1 );
				this.setVgap( 1 );
			}
	}

//	public Node globalSourcesInfoNode()
//	{
//		final FlowPane p = new FlowPane( Orientation.VERTICAL );
//
//		final HashMap< Source< ? >, Node > sourceToEntry = new HashMap<>();
//
//		final Function< SourceAndConverter< ? >, Node > entryCreator = ( sac ) -> {
//			final FlowPane fp = new FlowPane();
//			fp.getChildren().add( new Label( sac.getSpimSource().getName() ) );
//
//			final Converter< ?, ARGBType > conv = sac.getConverter();
//
//			if ( conv instanceof RealARGBConverter )
//			{
//				final RealARGBConverter< ? > c = ( RealARGBConverter< ? > ) conv;
//				// alpha
//				{
//					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getAlpha() );
//					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
//						c.setAlpha( newValue );
//						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
//					} );
//					sp.setEditable( true );
//					fp.getChildren().add( sp );
//				}
//
//				// min
//				{
//					final Spinner< Double > sp = new Spinner<>( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, c.getMin() );
//					sp.valueProperty().addListener( ( ChangeListener< Double > ) ( observable, oldValue, newValue ) -> {
//						c.setMin( newValue );
//						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
//					} );
//					sp.setEditable( true );
//					fp.getChildren().add( sp );
//				}
//
//				// max
//				{
//					final Spinner< Double > sp = new Spinner<>( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, c.getMax() );
//					sp.valueProperty().addListener( ( ChangeListener< Double > ) ( observable, oldValue, newValue ) -> {
//						c.setMax( newValue );
//						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
//					} );
//					sp.setEditable( true );
//					fp.getChildren().add( sp );
//				}
//			}
//
//			else if ( conv instanceof HighlightingStreamConverter )
//			{
//				final HighlightingStreamConverter c = ( HighlightingStreamConverter ) conv;
//
//				// alpha
//				{
//					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getAlpha() );
//					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
//						c.setAlpha( newValue );
//						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
//					} );
//					sp.setEditable( true );
//					fp.getChildren().add( sp );
//				}
//
//				// highlighting alpha
//				{
//					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getHighlightAlpha() );
//					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
//						c.setHighlightAlpha( newValue );
//						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
//					} );
//					sp.setEditable( true );
//					fp.getChildren().add( sp );
//				}
//
//				// invalid alpha
//				{
//					final Spinner< Integer > sp = new Spinner<>( 0, 255, c.getInvalidSegmentAlpha() );
//					sp.valueProperty().addListener( ( ChangeListener< Integer > ) ( observable, oldValue, newValue ) -> {
//						c.setInvalidSegmentAlpha( newValue );
//						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).requestRepaint() );
//					} );
//					sp.setEditable( true );
//					fp.getChildren().add( sp );
//				}
//			}
//
//			sourceToEntry.put( sac.getSpimSource(), fp );
//			p.getChildren().add( fp );
//
//			return fp;
//		};
//		for ( final SourceAndConverter< ? > source : this.state.viewerPanelState.getSourcesCopy() )
//			entryCreator.apply( source );
//
//		this.state.viewerPanelState.addSourcesListener( c -> {
//			while ( c.next() )
//				if ( c.wasRemoved() )
//					c.getRemoved().forEach( rm -> p.getChildren().remove( sourceToEntry.remove( rm.getSpimSource() ) ) );
//				else if ( c.wasAdded() )
//					c.getAddedSubList().forEach( entryCreator::apply );
//
//		} );
//
//		return p;
//	}

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
		viewerNodes.stream().map( ViewerNode::getViewer ).forEach( ViewerPanelFX::requestRepaint );
	}

	public HashMap< ViewerPanelFX, ViewerAxis > viewerAxes()
	{
		final HashMap< ViewerPanelFX, ViewerAxis > axes = new HashMap<>();
		this.viewerNodes.forEach( vn -> axes.put( vn.getViewer(), vn.getViewerAxis() ) );
		return axes;
	}

//	@Override
//	public void layoutChildren()
//	{
//		new RuntimeException().printStackTrace();
//		super.layoutChildren();
//	}
}
