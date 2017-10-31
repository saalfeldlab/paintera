package bdv.bigcat.viewer.ortho;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import bdv.bigcat.viewer.ViewerActor;
import bdv.bigcat.viewer.panel.OnSceneInitListener;
import bdv.bigcat.viewer.panel.OnWindowInitListener;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.panel.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.panel.ViewerTransformManager;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanelFX;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import net.imglib2.realtransform.AffineTransform3D;

public class OrthoView extends GridPane
{

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

	private final Set< KeyCode > activeKeys = new HashSet<>();

	public OrthoView( final VolatileGlobalCellCache cellCache )
	{
		this( new OrthoViewState(), cellCache );
	}

	public OrthoView( final ViewerOptions viewerOptions, final VolatileGlobalCellCache cellCache )
	{
		this( new OrthoViewState( viewerOptions ), cellCache );
	}

	public OrthoView( final OrthoViewState state, final VolatileGlobalCellCache cellCache )
	{
		this( ( vp ) -> {}, ( vp ) -> {}, state, cellCache );
	}

	public OrthoView( final Consumer< ViewerPanelFX > onFocusEnter, final Consumer< ViewerPanelFX > onFocusExit, final OrthoViewState state, final VolatileGlobalCellCache cellCache )
	{
		super();
		this.state = state;

		this.state.constraintsManager.manageGrid( this );
		this.onFocusEnter = onFocusEnter;
		this.onFocusExit = onFocusExit;
//		this.setInfoNode( new Label( "Place your node here!" ) );

		this.resizer = new GridResizer( this.state.constraintsManager, 10, this );
		this.setOnMouseMoved( resizer.onMouseMovedHandler() );
		this.setOnMouseDragged( resizer.onMouseDraggedHandler() );
		this.setOnMouseClicked( resizer.onMouseDoubleClickedHandler() );
		this.setOnMousePressed( resizer.onMousePresedHandler() );
		this.setOnMouseReleased( resizer.onMouseReleased() );

		this.setVgap( 1.0 );
		this.setHgap( 1.0 );
		this.addEventHandler( KeyEvent.KEY_TYPED, event -> {
			if ( event.getCharacter().equals( "f" ) )
				maximizeActiveOrthoView( event );
		} );

		layoutViewers( cellCache );
		this.focusTraversableProperty().set( true );

		final OnSceneInitListener stageInit = OnWindowInitListener.doOnStageInit( stage -> {
			stage.addEventFilter( KeyEvent.KEY_PRESSED, event -> {
				synchronized ( activeKeys )
				{
					activeKeys.add( event.getCode() );
				}
			} );
			stage.addEventFilter( KeyEvent.KEY_RELEASED, event -> {
				synchronized ( activeKeys )
				{
					activeKeys.remove( event.getCode() );
				}
			} );
			stage.focusedProperty().addListener( ( obs, oldv, newv ) -> {
				if ( !newv )
					synchronized ( activeKeys )
					{
						activeKeys.clear();
					}
			} );
		} );
		this.sceneProperty().addListener( stageInit );

	}

	private void layoutViewers( final VolatileGlobalCellCache cellCache )
	{
		addViewer( ViewerAxis.Z, 0, 0, cellCache );
		addViewer( ViewerAxis.X, 0, 1, cellCache );
		addViewer( ViewerAxis.Y, 1, 0, cellCache );
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

		final EventHandler< MouseEvent > handler = event -> {
//			viewerNode.requestFocus();
		};
		viewerNode.focusedProperty().addListener( ( obs, o, n ) -> {
			System.out.println( "Focusing " + viewerNode );
		} );
//		viewerNode.addEventHandler( MouseEvent.MOUSE_CLICKED, handler );

//		viewerNode.addEventHandler( MouseEvent.MOUSE_ENTERED, event -> {
//			final Node focusOwner = viewerNode.sceneProperty().get().focusOwnerProperty().get();
//			for ( final Class< ? > focusKeeper : focusKeepers )
//				if ( focusKeeper.isInstance( focusOwner ) )
//					return;
//			viewerNode.requestFocus();
//		} );

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

	private synchronized void addViewer( final ViewerAxis axis, final int rowIndex, final int colIndex, final VolatileGlobalCellCache cellCache )
	{
		final ViewerNode viewerNode = new ViewerNode( cellCache, axis, this.state.viewerOptions, activeKeys );
//		final ViewerNode viewerNode = new ViewerNode( new CacheControl.Dummy(), axis, this.state.viewerOptions, activeKeys );
		this.viewerNodes.add( viewerNode );
		this.managers.put( viewerNode, viewerNode.manager() );
		viewerNode.getViewerState().setSources( state.sacs, state.visibility, state.currentSource, state.interpolation );
		viewerNode.getViewerState().setGlobalTransform( this.state.globalTransform );
		viewerActors.forEach( actor -> actor.onAdd().accept( viewerNode.getViewer() ) );
		addViewerNodesHandler( viewerNode, FOCUS_KEEPERS );

		this.add( viewerNode, rowIndex, colIndex );
		viewerNode.setOnMouseClicked( resizer.onMouseDoubleClickedHandler() );
		viewerNode.setOnMousePressed( resizer.onMousePresedHandler() );
		viewerNode.setOnMouseDragged( resizer.onMouseDraggedHandler() );
		viewerNode.setOnMouseMoved( resizer.onMouseMovedHandler() );
	}

	private void maximizeActiveOrthoView( final Event event )
	{
		final Scene scene = getScene();
		final Node focusOwner = scene.focusOwnerProperty().get();
		if ( viewerNodes.contains( focusOwner ) )
			// event.consume();
			if ( !this.state.constraintsManager.isFullScreen() )
			{
				viewerNodes.forEach( node -> node.setVisible( node == focusOwner ) );
				this.state.constraintsManager.maximize(
						GridPane.getRowIndex( focusOwner ),
						GridPane.getColumnIndex( focusOwner ),
						0 );
//				( ( ViewerPanel ) ( ( SwingNode ) focusOwner ).getContent() ).requestRepaint();
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

//	@Override
//	public void layoutChildren()
//	{
//		System.out.println( "LAYING OUT CHILDREN IN OrthoView!" );
//		new RuntimeException().printStackTrace();
//		super.layoutChildren();
//	}
}
