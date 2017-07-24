package bdv.bigcat.viewer;

import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

import org.scijava.ui.behaviour.Behaviour;

import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ViewerNode.ViewerAxis;
import bdv.cache.CacheControl;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.ui.OverlayRenderer;

public class BaseView extends BorderPane
{

	public static final Class< ? >[] FOCUS_KEEPERS = { TextField.class };

	private final Node infoPane;

	private final HashSet< ViewerNode > viewerNodes = new HashSet<>();

	private final HashMap< ViewerNode, ViewerTransformManager > managers = new HashMap<>();

	private final GridPane grid;

	private final GlobalTransformManager gm;

	private final GridConstraintsManager constraintsManager;

	private final boolean[] isFullScreen = new boolean[] { false };

	private final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositeMap = new HashMap<>();

	private final ViewerOptions viewerOptions;

	private final ObservableList< SourceAndConverter< ? > > sourceLayers = FXCollections.observableArrayList();
	{
		sourceLayers.addListener( ( ListChangeListener< SourceAndConverter< ? > > ) c -> {
			c.next();
			if ( c.wasRemoved() )
				c.getRemoved().forEach( sourceCompositeMap::remove );

		} );
	}

	private final ObservableList< OverlayRenderer > overlayRenderers = FXCollections.observableArrayList();
	{
		overlayRenderers.addListener( ( ListChangeListener< OverlayRenderer > ) c -> {
			while ( c.next() )
				if ( c.wasRemoved() )
					for ( final OverlayRenderer renderer : c.getRemoved() )
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).getDisplay().removeOverlayRenderer( renderer ) );
				else if ( c.wasAdded() )
					for ( final OverlayRenderer renderer : c.getAddedSubList() )
						viewerNodes.forEach( vn -> ( ( ViewerPanel ) vn.getContent() ).getDisplay().addOverlayRenderer( renderer ) );
		} );
	}

	private final ObservableList< Behaviour > behaviours = FXCollections.observableArrayList();

	private final HashMap< Behaviour, String > behaviourNames = new HashMap<>();

	private final HashMap< Behaviour, String[] > behaviourTriggers = new HashMap<>();
	{
		behaviours.addListener( ( ListChangeListener< Behaviour > ) c -> {
			while ( c.next() )
				if ( c.wasAdded() )
					for ( final Behaviour behaviour : c.getAddedSubList() )
						viewerNodes.forEach( vn -> vn.addBehaviour( behaviour, behaviourNames.get( behaviour ), behaviourTriggers.get( behaviour ) ) );
		} );
	}

	private final ObservableList< MouseMotionListener > mouseMotionListeners = FXCollections.observableArrayList();
	{
		mouseMotionListeners.addListener( ( ListChangeListener< MouseMotionListener > ) c -> {
			System.out.println( "EVENT! " );
			while ( c.next() )
				if ( c.wasAdded() )
					for ( final MouseMotionListener listener : c.getAddedSubList() )
						viewerNodes.forEach( vn -> vn.addMouseMotionListener( listener ) );
				else if ( c.wasRemoved() )
					for ( final MouseMotionListener listener : c.getRemoved() )
						viewerNodes.forEach( vn -> vn.removeMouseMotionListener( listener ) );
		} );
	}

	private final Consumer< ViewerPanel > onFocusEnter;

	private final Consumer< ViewerPanel > onFocusExit;

	public BaseView()
	{
		this( ViewerOptions.options() );
	}

	public BaseView( final ViewerOptions viewerOptions )
	{
		this( ( vp ) -> {}, ( vp ) -> {}, viewerOptions );
	}

	public BaseView( final Consumer< ViewerPanel > onFocusEnter, final Consumer< ViewerPanel > onFocusExit, final ViewerOptions viewerOptions )
	{
		super();
		this.infoPane = createInfo();
		this.gm = new GlobalTransformManager( new AffineTransform3D() );
		gm.setTransform( new AffineTransform3D() );

		this.constraintsManager = new GridConstraintsManager();
		this.grid = constraintsManager.createGrid();
		this.centerProperty().set( grid );
		this.onFocusEnter = onFocusEnter;
		this.onFocusExit = onFocusExit;
		this.viewerOptions = viewerOptions;
	}

	public synchronized void addSource( final SourceAndConverter< ? > source, final Composite< ARGBType, ARGBType > comp )
	{

		if ( sourceLayers.contains( source ) || sourceCompositeMap.containsKey( source.getSpimSource() ) )
		{

		}
		else
		{
			this.sourceLayers.add( source );
			this.sourceCompositeMap.put( source.getSpimSource(), comp );
		}
	}

	public synchronized void removeSource( final Source< ? > source )
	{
		int i = 0;
		for ( ; i < sourceLayers.size(); ++i )
			if ( sourceLayers.get( i ).getSpimSource().equals( source ) )
				break;
		if ( i < sourceLayers.size() )
		{
			assert sourceCompositeMap.containsKey( source );
			sourceLayers.remove( i );
			sourceCompositeMap.remove( source );
		}
	}

	protected Node createInfo()
	{

		final TableView< ? > table = new TableView<>();
		table.setEditable( true );
		table.getColumns().addAll( new TableColumn<>( "Property" ), new TableColumn<>( "Value" ) );

		final TextField tf = new TextField( "some text" );

		final TabPane infoPane = new TabPane();

		final VBox jfxStuff = new VBox( 1 );
		jfxStuff.getChildren().addAll( tf, table );
		infoPane.getTabs().add( new Tab( "jfx stuff", jfxStuff ) );
		infoPane.getTabs().add( new Tab( "dataset info", new Label( "random floats" ) ) );
		return infoPane;

	}

	public Scene createScene( final int width, final int height ) throws Exception
	{
		final Scene scene = new Scene( this, width, height );
		scene.setOnKeyTyped( event -> {
			if ( event.getCharacter().equals( "a" ) )
				maximizeActiveOrthoView( scene, event );
		} );

		return scene;
	}

	public synchronized void addOverlayRenderer( final OverlayRenderer renderer )
	{
		this.overlayRenderers.add( renderer );
	}

	public synchronized void removeOverlayRenderer( final OverlayRenderer renderer )
	{
		this.overlayRenderers.remove( renderer );
	}

	public synchronized void addMouseMotionListener( final MouseMotionListener listener )
	{
		this.mouseMotionListeners.add( listener );
	}

	private void addViewerNodesHandler( final ViewerNode viewerNode, final Class< ? >[] focusKeepers )
	{

		viewerNode.addEventHandler( MouseEvent.MOUSE_CLICKED, event -> viewerNode.requestFocus() );

		viewerNode.addEventHandler( MouseEvent.MOUSE_ENTERED, event -> {
			final Node focusOwner = viewerNode.sceneProperty().get().focusOwnerProperty().get();
			for ( final Class< ? > focusKeeper : focusKeepers )
				if ( focusKeeper.isInstance( focusOwner ) )
					return;
			viewerNode.requestFocus();
		} );

		handleFocusEvent( viewerNode );
	}

	private synchronized void handleFocusEvent( final ViewerNode viewerNode )
	{
		viewerNode.focusedProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> {
			final ViewerPanel viewer = ( ViewerPanel ) viewerNode.getContent();
			if ( viewer == null )
				return;
			else if ( newValue )
				this.onFocusEnter.accept( viewer );
			else
			{
				System.out.println( "Handling exit: accept viewer!" );
				this.onFocusExit.accept( viewer );
			}
		} );
	}

	private synchronized void addViewer( final ViewerAxis axis, final int rowIndex, final int colIndex )
	{
		final ViewerNode viewerNode = new ViewerNode( new CacheControl.Dummy(), axis, gm, viewerOptions );
		this.viewerNodes.add( viewerNode );
		this.managers.put( viewerNode, viewerNode.manager() );

		sourceLayers.addListener( viewerNode );

		this.grid.add( viewerNode, rowIndex, colIndex );

		final Thread t = new Thread( () -> {
			boolean createdViewer = false;
			while ( !createdViewer )
			{
				try
				{
					Thread.sleep( 10 );
				}
				catch ( final InterruptedException e )
				{
					e.printStackTrace();
					return;
				}
				createdViewer = viewerNode.isReady();
			}
			createdViewer = true;
			sourceLayers.forEach( sl -> ( ( ViewerPanel ) viewerNode.getContent() ).addSource( sl ) );
			behaviours.forEach( behaviour -> viewerNode.addBehaviour( behaviour, behaviourNames.get( behaviour ), behaviourTriggers.get( behaviour ) ) );

		} );
		t.start();

		addViewerNodesHandler( viewerNode, FOCUS_KEEPERS );
	}

	public void makeDefaultLayout()
	{
		addViewer( ViewerAxis.Z, 0, 0 );
		addViewer( ViewerAxis.X, 0, 1 );
		addViewer( ViewerAxis.Y, 1, 0 );
		this.grid.add( this.infoPane, 1, 1 );
	}

	private void maximizeActiveOrthoView( final Scene scene, final Event event )
	{
		final Node focusOwner = scene.focusOwnerProperty().get();
		if ( viewerNodes.contains( focusOwner ) )
		{
			event.consume();
			if ( !isFullScreen[ 0 ] )
			{
				viewerNodes.forEach( node -> node.setVisible( node == focusOwner ) );
				infoPane.setVisible( false );
				constraintsManager.maximize(
						GridPane.getRowIndex( focusOwner ),
						GridPane.getColumnIndex( focusOwner ),
						0 );
				( ( ViewerPanel ) ( ( SwingNode ) focusOwner ).getContent() ).requestRepaint();
				grid.setHgap( 0 );
				grid.setVgap( 0 );
			}
			else
			{
				constraintsManager.resetToLast();
				viewerNodes.forEach( node -> node.setVisible( true ) );
				viewerNodes.forEach( node -> ( ( ViewerPanel ) node.getContent() ).requestRepaint() );
				infoPane.setVisible( true );
				grid.setHgap( 1 );
				grid.setVgap( 1 );
			}
			isFullScreen[ 0 ] = !isFullScreen[ 0 ];
		}
	}
}
