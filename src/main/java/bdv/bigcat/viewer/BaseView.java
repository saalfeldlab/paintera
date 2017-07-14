package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.HashSet;

import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;

import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ViewerNode.ViewerAxis;
import bdv.cache.CacheControl;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
import javafx.scene.layout.VBox;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

public class BaseView
{

	public static final Class< ? >[] FOCUS_KEEPERS = { TextField.class };

	private final Node infoPane;

	private final HashSet< ViewerNode > viewerNodes = new HashSet<>();

	private final HashMap< ViewerNode, ViewerTransformManager > managers = new HashMap<>();

	private final DockPane dock = new DockPane();

	private final BorderPane root = new BorderPane( dock );

	private final DockNode status = new DockNode( new Label( "STATUS-BAR" ) );
	{
		root.setBottom( status );
	}

	private final GlobalTransformManager gm;

	final boolean[] isFullScreen = new boolean[] { false };

	final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositeMap = new HashMap<>();

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
		this.infoPane = createInfo();
		this.gm = new GlobalTransformManager( new AffineTransform3D() );
		gm.setTransform( new AffineTransform3D() );

		final DockNode infoDock = new DockNode( this.infoPane );
		infoDock.dock( this.dock, DockPos.RIGHT );
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

		final Scene scene = new Scene( this.root, width, height );

		return scene;

//		final Thread t = new Thread( () -> primaryStage.show() );
//		t.start();
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

	public void addViewer( final ViewerAxis axis, final DockPos pos )
	{
		final ViewerNode viewerNode = new ViewerNode( new CacheControl.Dummy(), axis, gm );
		this.viewerNodes.add( viewerNode );
		this.managers.put( viewerNode, viewerNode.manager() );

		sourceLayers.addListener( viewerNode );

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
		final DockNode dockNode = new DockNode( viewerNode );
		dockNode.dock( dock, pos );

		addViewerNodesHandler( viewerNode, FOCUS_KEEPERS );
	}

	public void makeDefaultLayout()
	{
		final DockNode dockNode = ( DockNode ) this.infoPane.parentProperty().get();
		dockNode.undock();
		addViewer( ViewerAxis.Z, DockPos.LEFT );
		addViewer( ViewerAxis.Y, DockPos.RIGHT );
		addViewer( ViewerAxis.X, DockPos.BOTTOM );
		dockNode.dock( this.dock, DockPos.RIGHT );
	}
}