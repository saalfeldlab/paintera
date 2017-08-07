package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.HashSet;

import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ViewerNode.ViewerAxis;
import bdv.cache.CacheControl;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

public class BaseView
{

	public static final Class< ? >[] FOCUS_KEEPERS = { TextField.class };

	private final Node infoPane;

	private final HashSet< ViewerNode > viewerNodes = new HashSet<>();

	private final HashMap< ViewerNode, ViewerTransformManager > managers = new HashMap<>();

	private final GridPane grid;

	private final BorderPane root;

	private final GlobalTransformManager gm;

	private final GridConstraintsManager constraintsManager;

	final boolean[] isFullScreen = new boolean[] { false };

	final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositeMap = new HashMap<>();

	private final Viewer3D viewer3D;

	private boolean created3DViewer;

	final ObservableList< SourceAndConverter< ? > > sourceLayers = FXCollections.observableArrayList();
	{
		sourceLayers.addListener( ( ListChangeListener< SourceAndConverter< ? > > ) c -> {
			c.next();
			if ( c.wasRemoved() )
				c.getRemoved().forEach( sourceCompositeMap::remove );

		} );
	}

	public BaseView()
	{
		viewer3D = new Viewer3D();
		this.infoPane = createInfo();
		this.gm = new GlobalTransformManager( new AffineTransform3D() );
		gm.setTransform( new AffineTransform3D() );

		this.constraintsManager = new GridConstraintsManager();
		this.grid = constraintsManager.createGrid();
		this.root = new BorderPane( grid );

		viewer3D.init();
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
		return viewer3D.getPanel();
	}

	public Scene createScene( final int width, final int height ) throws Exception
	{
		final Scene scene = new Scene( this.root, width, height );
		scene.setOnKeyTyped( event -> {
			if ( event.getCharacter().equals( "a" ) )
				maximizeActiveOrthoView( scene, event );
		} );

		return scene;
	}

	private static void addViewerNodesHandler( final ViewerNode viewerNode, final Class< ? >[] focusKeepers )
	{

		viewerNode.addEventHandler( MouseEvent.MOUSE_CLICKED, event -> viewerNode.requestFocus() );

		viewerNode.addEventHandler( MouseEvent.MOUSE_ENTERED, event -> {
			final Node focusOwner = viewerNode.sceneProperty().get().focusOwnerProperty().get();
			for ( final Class< ? > focusKeeper : focusKeepers )
				if ( focusKeeper.isInstance( focusOwner ) )
					return;
			viewerNode.requestFocus();
		} );
	}

	private void addViewer( final ViewerAxis axis, final int rowIndex, final int colIndex )
	{
		final ViewerNode viewerNode = new ViewerNode( new CacheControl.Dummy(), axis, gm );
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
			sourceLayers.remove( null );
		} );
		t.start();

		addViewerNodesHandler( viewerNode, FOCUS_KEEPERS );
	}

	public void makeDefaultLayout()
	{
		addViewer( ViewerAxis.Z, 0, 0 );
		addViewer( ViewerAxis.Y, 0, 1 );
		addViewer( ViewerAxis.X, 1, 0 );
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
