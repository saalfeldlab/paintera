package bdv.bigcat.viewer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.source.LabelLayer;
import bdv.bigcat.viewer.source.LabelSource;
import bdv.bigcat.viewer.source.RandomAccessibleIntervalSource;
import bdv.bigcat.viewer.source.RawLayer;
import bdv.bigcat.viewer.source.Source;
import bdv.bigcat.viewer.source.SourceLayer;
import bdv.cache.CacheControl;
import bdv.util.AxisOrder;
import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
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
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;

public class OrthoView
{
	private final Node infoPane;

	private final ViewerNode[] viewerNodes = new ViewerNode[ 3 ];

	private final ViewerTransformManager[] managers = new ViewerTransformManager[ 3 ];

	private final GridPane grid;

	private final GridConstraintsManager gridConstraintsManager = new GridConstraintsManager();

	private boolean createdViewers;

	final boolean[] isFullScreen = new boolean[] { false };

	final HashMap< bdv.viewer.Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositeMap = new HashMap<>();

	final ObservableList< SourceLayer > sourceLayers = FXCollections.observableArrayList();
	{
		sourceLayers.addListener( ( ListChangeListener< SourceLayer > ) c -> {
			c.next();
			if ( c.wasRemoved() )
				c.getRemoved().forEach( sourceCompositeMap::remove );

		} );
	}

	public OrthoView()
	{
		this.infoPane = createInfo();
		this.grid = createGrid( infoPane, gridConstraintsManager );
		addViewerNodesHandlers( this.viewerNodes );
	}

	public synchronized boolean isInitialized()
	{
		return createdViewers;
	}

//	public void toggleSourceVisibility( final int sourceIndex )
//	{
//		waitUntilInitialized();
//		synchronized ( this )
//		{
//			for ( final ViewerPanel viewer : this.viewers )
//			{
//				viewer.getVisibilityAndGrouping().toggleActiveGroupOrSource( sourceIndex );
//				viewer.requestRepaint();
//			}
//		}
//	}
//
//	public void setCurrentSource( final int sourceIndex )
//	{
//		waitUntilInitialized();
//		synchronized ( this )
//		{
//			for ( final ViewerPanel viewer : this.viewers )
//			{
//				viewer.getVisibilityAndGrouping().setCurrentGroupOrSource( sourceIndex );
//				viewer.requestRepaint();
//			}
//		}
//	}

	public void addSource( final Source< ?, ? > source )
	{
		waitUntilInitialized();

		if ( sourceLayers.stream().map( SourceLayer::name ).filter( source::equals ).count() > 0 )
			return;

		final SourceLayer sourceLayer;
		if ( source instanceof LabelSource )
			sourceLayer = new LabelLayer( ( LabelSource ) source, sourceCompositeMap );
		else if ( source instanceof RandomAccessibleIntervalSource ) {
			if ( source.loader().getImageType() instanceof FloatType )
				sourceLayer = new RawLayer<>( ( RandomAccessibleIntervalSource< FloatType > ) source );
			else
				return;
		}
		else
		{
			sourceLayer = null;
			return;
		}

		this.sourceLayers.add( sourceLayer );

	}

	public void removeSource( final Source< ?, ? > source )
	{
		waitUntilInitialized();
		final Stream< ? > matches = this.sourceLayers.stream().filter( sourceLayer -> sourceLayer.source().equals( source ) );
		matches.forEach( sourceLayer -> {
			this.sourceLayers.remove( sourceLayer );
		} );
//		for ( final ViewerPanel viewer : this.viewers )
//			viewer.removeSource( source.getSpimSource() );

	}

//	public void toggleInterpolation()
//	{
//		for ( final ViewerPanel viewer : this.viewers )
//			viewer.toggleInterpolation();
//	}

	private void waitUntilInitialized()
	{
		while ( !isInitialized() )
			try
		{
				Thread.sleep( 10 );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
			return;
		}
	}

//	public synchronized void speedFactor( final double speedFactor )
//	{
//		waitUntilInitialized();
//		for ( final ViewerTransformManager manager : managers )
//			manager.speedFactor( speedFactor );
//	}

	private static GridPane createGrid( final Node infoPane, final GridConstraintsManager manager )
	{

		final GridPane grid = new GridPane();
		GridPane.setConstraints( infoPane, 1, 1 );
		grid.getChildren().add( infoPane );

		grid.getColumnConstraints().add( manager.column1 );
		grid.getColumnConstraints().add( manager.column2 );

		grid.getRowConstraints().add( manager.row1 );
		grid.getRowConstraints().add( manager.row2 );

		grid.setHgap( 1 );
		grid.setVgap( 1 );

		return grid;
	}

	protected Node createInfo()
	{

//			final Label node = new Label( "This is a box for info or 3D rendering." );

//			final TableView< ? > table = new TableView<>();
//			table.setEditable( true );
//			table.getColumns().addAll( new TableColumn<>( "Property" ), new TableColumn<>( "Value" ) );
//
//			final TextField tf = new TextField( "some text" );
//
//			final TabPane infoPane = new TabPane();
//
//			final VBox jfxStuff = new VBox( 1 );
//			jfxStuff.getChildren().addAll( tf, table );
//			infoPane.getTabs().add( new Tab( "jfx stuff", jfxStuff ) );
//			infoPane.getTabs().add( new Tab( "dataset info", new Label( "random floats" ) ) );
//			infoPane.getTabs().add( new Tab( "contrast", listView ) );

		return new Label( "info box" );
	}

	private static void addViewerNodesHandlers( final ViewerNode[] viewerNodesArray )
	{
		final Thread t = new Thread( () -> {
			while ( viewerNodesArray[ 0 ] == null || viewerNodesArray[ 1 ] == null || viewerNodesArray[ 2 ] == null )
				try
			{
					Thread.sleep( 10 );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
				return;
			}
			final Class< ? >[] focusKeepers = { TextField.class };
			for ( int i = 0; i < viewerNodesArray.length; ++i )
			{
				final SwingNode viewerNode = viewerNodesArray[ i ];
//				final DropShadow ds = new DropShadow( 10, Color.PURPLE );
				final DropShadow ds = new DropShadow( 10, Color.hsb( 60.0 + 360.0 * i / viewerNodesArray.length, 1.0, 0.5, 1.0 ) );
				viewerNode.focusedProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> {
					if ( newValue )
						viewerNode.setEffect( ds );
					else
						viewerNode.setEffect( null );
				} );

				viewerNode.addEventHandler( MouseEvent.MOUSE_CLICKED, event -> viewerNode.requestFocus() );

				viewerNode.addEventHandler( MouseEvent.MOUSE_ENTERED, event -> {
					final Node focusOwner = viewerNode.sceneProperty().get().focusOwnerProperty().get();
					for ( final Class< ? > focusKeeper : focusKeepers )
						if ( focusKeeper.isInstance( focusOwner ) )
							return;
					viewerNode.requestFocus();
				} );
			}
		} );
		t.start();
	}

	private void maximizeActiveOrthoView( final Scene scene, final Event event )
	{
		final Node focusOwner = scene.focusOwnerProperty().get();
		if ( Arrays.asList( viewerNodes ).contains( focusOwner ) )
		{
			event.consume();
			if ( !isFullScreen[ 0 ] )
			{
				Arrays.asList( viewerNodes ).forEach( node -> node.setVisible( node == focusOwner ) );
				infoPane.setVisible( false );
				gridConstraintsManager.maximize(
						GridPane.getRowIndex( focusOwner ),
						GridPane.getColumnIndex( focusOwner ),
						0 );
				( ( ViewerPanel ) ( ( SwingNode ) focusOwner ).getContent() ).requestRepaint();
				grid.setHgap( 0 );
				grid.setVgap( 0 );
			}
			else
			{
				gridConstraintsManager.resetToLast();
				Arrays.asList( viewerNodes ).forEach( node -> node.setVisible( true ) );
				Arrays.asList( viewerNodes ).forEach( node -> ( ( ViewerPanel ) node.getContent() ).requestRepaint() );
				infoPane.setVisible( true );
				grid.setHgap( 1 );
				grid.setVgap( 1 );
			}
			isFullScreen[ 0 ] = !isFullScreen[ 0 ];
		}
	}

//	private void toggleVisibilityOrSetActiveSource( final Scene scene, final KeyEvent event )
//	{
//		if ( event.isAltDown() || event.isConsumed() || event.isControlDown() || event.isMetaDown() || event.isShortcutDown() )
//			return;
//		final Node focusOwner = scene.focusOwnerProperty().get();
//
//		if ( Arrays.asList( viewerNodes ).contains( focusOwner ) )
//		{
//
//			final int number;
//			switch ( event.getCode() )
//			{
//			case DIGIT1:
//				number = 0;
//				break;
//			case DIGIT2:
//				number = 1;
//				break;
//			case DIGIT3:
//				number = 2;
//				break;
//			case DIGIT4:
//				number = 3;
//				break;
//			case DIGIT5:
//				number = 4;
//				break;
//			case DIGIT6:
//				number = 5;
//				break;
//			case DIGIT7:
//				number = 6;
//				break;
//			case DIGIT8:
//				number = 7;
//				break;
//			case DIGIT9:
//				number = 8;
//				break;
//			case DIGIT0:
//				number = 9;
//				break;
//			default:
//				number = -1;
//				break;
//			}
//			System.out.println( "NUMBER IS " + number );
//			if ( event.isShiftDown() )
//				toggleSourceVisibility( number );
//			else
//				setCurrentSource( number );
//
//			event.consume();
//		}
//	}

//	private void toggleInterpolation( final Scene scene, final KeyEvent event )
//	{
//		toggleInterpolation();
//		event.consume();
//	}

	public void start( final Stage primaryStage ) throws Exception
	{

		final Scene scene = new Scene( grid, 500, 500 );

		primaryStage.setTitle( "BigCAT" );
		primaryStage.setScene( scene );

//		scene.setOnKeyTyped( event -> {
//			if ( event.getCharacter().equals( "a" ) )
//				maximizeActiveOrthoView( scene, event );
//			else if ( event.getCharacter().equals( "i" ) )
//				toggleInterpolation( scene, event );
//		} );
//
//		scene.setOnKeyPressed( event -> {
//			System.out.println( "PRESSING! " + event );
//			System.out.println( " code " + event.getCode() );
//			if ( event.getCode().isDigitKey() )
//				toggleVisibilityOrSetActiveSource( scene, event );
//		});

//		SwingUtilities.invokeLater( () -> {
//			// this is just temporary: sleep until all javafx is set up to
//			// avoid width == 0 or height == 0 exceptions
//			try
//			{
////					System.out.println( swingNode1.isVisible() + " " + swingNode2.isVisible() + " " + swingNode3.isVisible() );
//				Thread.sleep( 1500 );
//			}
//			catch ( final InterruptedException e )
//			{
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//
////			for ( final SwingNode viewerNode : viewerNodesArray )
////			{
////				final ViewerPanel vp = ( ViewerPanel ) viewerNode.getContent();
////				System.out.println( "Adding source! " + vp.getState().getSources().size() );
////				sacs.forEach( vp::addSource );
////				System.out.println( vp.getState().getSources().size() );
////				vp.requestRepaint();
////			}
//
//		} );

		createSwingContent();

		final Thread t = new Thread( () -> {
			while ( viewerNodes[ 0 ] == null || viewerNodes[ 1 ] == null || viewerNodes[ 2 ] == null )
				try
			{
					Thread.sleep( 10 );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
				return;
			}

		} );

		t.start();

		primaryStage.show();

//		final Thread t = new Thread( () -> primaryStage.show() );
//		t.start();
	}

	protected List< SourceAndConverter< ? > > createSourceAndConverter()
	{
		final Random rng = new Random( 100 );
		final Img< FloatType > rai = ArrayImgs.floats( 100, 200, 300 );

		for ( final FloatType f1 : rai )
			f1.set( rng.nextFloat() );
		final RealARGBConverter< FloatType > conv = new RealARGBConverter<>( 0.0, 1.0 );

		final AffineTransform3D tf = new AffineTransform3D();

		final List< SourceAndConverter< FloatType > > sacs = BdvFunctions.toSourceAndConverter( rai, conv, AxisOrder.XYZ, tf, "ok" );
		final List< SourceAndConverter< ? > > sacsWildcard = sacs.stream().map( sac -> ( SourceAndConverter< ? > ) sac ).collect( Collectors.toList() );
		return sacsWildcard;
	}

	private void createSwingContent()
	{
//			whiel( swingNode1.getBoundsInParent().)
//			while ( true )
//			{
//				final Bounds b1 = swingNode1.getBoundsInParent();
//				final Bounds b2 = swingNode2.getBoundsInParent();
//				final Bounds b3 = swingNode3.getBoundsInParent();
//				if ( b1.getWidth() > 0 || b1.getHeight() > 0 || b2.getWidth() > 0 || b2.getHeight() > 0 || b3.getWidth() > 0 || b3.getHeight() > 0 )
//					break;
//				try
//				{
//					Thread.sleep( 10 );
//				}
//				catch ( final InterruptedException e )
//				{
//					e.printStackTrace();
//					return;
//				}
//			}

		final GlobalTransformManager gm = new GlobalTransformManager( new AffineTransform3D() );

		gm.setTransform( new AffineTransform3D() );

		final ViewerNode viewerNode1 = new ViewerNode( new CacheControl.Dummy(), ViewerAxis.Z, gm );
		final ViewerNode viewerNode2 = new ViewerNode( new CacheControl.Dummy(), ViewerAxis.Y, gm );
		final ViewerNode viewerNode3 = new ViewerNode( new CacheControl.Dummy(), ViewerAxis.X, gm );

		this.viewerNodes[ 0 ] = viewerNode1;
		this.viewerNodes[ 1 ] = viewerNode2;
		this.viewerNodes[ 2 ] = viewerNode3;

		sourceLayers.addListener( viewerNode1 );
		sourceLayers.addListener( viewerNode2 );
		sourceLayers.addListener( viewerNode3 );

		createdViewers = true;

		this.grid.add( viewerNode1, 0, 0 );
		this.grid.add( viewerNode2, 1, 0 );
		this.grid.add( viewerNode3, 0, 1 );
	}

//	public static class OrthoViewNavigationActions extends Actions
//	{
//
//		/**
//		 * Create navigation actions and install them in the specified
//		 * {@link InputActionBindings}.
//		 *
//		 * @param inputActionBindings
//		 *            {@link InputMap} and {@link ActionMap} are installed here.
//		 * @param viewer
//		 *            Navigation actions are targeted at this
//		 *            {@link ViewerPanel}.
//		 * @param keyProperties
//		 *            user-defined key-bindings.
//		 */
//		public static void installActionBindings(
//				final InputActionBindings inputActionBindings,
//				final ViewerPanel viewer,
//				final KeyStrokeAdder.Factory keyProperties )
//		{
//			final OrthoViewNavigationActions actions = new OrthoViewNavigationActions( keyProperties );
//
//			actions.modes( viewer );
//			actions.sources( viewer );
//
//			actions.install( inputActionBindings, "navigation" );
//		}
//
//		public static final String TOGGLE_INTERPOLATION = "toggle interpolation";
//
//		public static final String SET_CURRENT_SOURCE = "set current source %d";
//
//		public static final String TOGGLE_SOURCE_VISIBILITY = "toggle source visibility %d";
//
//		public OrthoViewNavigationActions( final KeyStrokeAdder.Factory keyConfig )
//		{
//			super( keyConfig, new String[] { "bdv", "navigation" } );
//		}
//
//		public void sources( final ViewerPanel viewer )
//		{
//			final String[] numkeys = new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" };
//			for ( int i = 0; i < numkeys.length; ++i )
//			{
//				final int sourceIndex = i;
//				runnableAction(
//						() -> viewer.getVisibilityAndGrouping().setCurrentGroupOrSource( sourceIndex ),
//						String.format( SET_CURRENT_SOURCE, i ), numkeys[ i ] );
//				runnableAction(
//						() -> {
//							System.out.println( "TOGGLING SOURCE VISISBILITY! " );
//							viewer.getVisibilityAndGrouping().toggleActiveGroupOrSource( sourceIndex );
//						},
//						String.format( TOGGLE_SOURCE_VISIBILITY, i ), "shift " + numkeys[ i ] );
//			}
//		}
//
//		public void modes( final ViewerPanel viewer )
//		{
//			runnableAction(
//					() -> viewer.toggleInterpolation(),
//					TOGGLE_INTERPOLATION, "I" );
//		}
//
//	}
}