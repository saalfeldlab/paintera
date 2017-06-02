package bdv.bigcat.viewer;

import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.cache.CacheControl;
import bdv.util.AxisOrder;
import bdv.util.BdvFunctions;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class OrthoView
{

	private final SwingNode viewerNode1;

	private final SwingNode viewerNode2;

	private final SwingNode viewerNode3;

	private final Node infoPane;

	private final SwingNode[] viewerNodesArray;

	private final ViewerPanel[] viewers;

	private final ViewerTransformManager[] managers = new ViewerTransformManager[ 3 ];

	private final GridPane grid;

	private final GridConstraintsManager gridConstraintsManager = new GridConstraintsManager();

	private boolean createdViewers;

	final boolean[] isFullScreen = new boolean[] { false };

	public OrthoView()
	{
		this.viewerNode1 = new SwingNode();
		this.viewerNode2 = new SwingNode();
		this.viewerNode3 = new SwingNode();
		this.viewerNodesArray = new SwingNode[] { viewerNode1, viewerNode2, viewerNode3 };
		this.viewers = createSwingContent( viewerNode1, viewerNode2, viewerNode3 );
		this.infoPane = createInfo();
		this.grid = createGrid( viewerNode1, viewerNode2, viewerNode3, infoPane, gridConstraintsManager );

		addViewerNodesHandlers( this.viewerNodesArray );
	}

	public synchronized boolean isInitialized()
	{
		return createdViewers;
	}

	public void toggleSourceVisibility( final int sourceIndex )
	{
		waitUntilInitialized();
		synchronized ( this )
		{
			for ( final ViewerPanel viewer : this.viewers )
			{
				viewer.getVisibilityAndGrouping().toggleActiveGroupOrSource( sourceIndex );
				viewer.requestRepaint();
			}
		}
	}

	public void setCurrentSource( final int sourceIndex )
	{
		waitUntilInitialized();
		synchronized ( this )
		{
			for ( final ViewerPanel viewer : this.viewers )
			{
				viewer.getVisibilityAndGrouping().setCurrentGroupOrSource( sourceIndex );
				viewer.requestRepaint();
			}
		}
	}

	public void addSource( final SourceAndConverter< ? > source )
	{
		waitUntilInitialized();
		for ( final ViewerPanel viewer : this.viewers )
			viewer.addSource( source );

	}

	public void removeSource( final SourceAndConverter< ? > source )
	{
		waitUntilInitialized();
		for ( final ViewerPanel viewer : this.viewers )
			viewer.removeSource( source.getSpimSource() );
	}

	public void toggleInterpolation()
	{
		for ( final ViewerPanel viewer : this.viewers )
			viewer.toggleInterpolation();
	}

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

	public synchronized void speedFactor( final double speedFactor )
	{
		waitUntilInitialized();
		for ( final ViewerTransformManager manager : managers )
			manager.speedFactor( speedFactor );
	}

	private static GridPane createGrid( final SwingNode node1, final SwingNode node2, final SwingNode node3, final Node infoPane, final GridConstraintsManager manager )
	{

		final GridPane grid = new GridPane();
		GridPane.setConstraints( node1, 0, 0 );
		GridPane.setConstraints( node2, 1, 0 );
		GridPane.setConstraints( node3, 0, 1 );
		GridPane.setConstraints( infoPane, 1, 1 );
		grid.getChildren().add( node1 );
		grid.getChildren().add( node2 );
		grid.getChildren().add( node3 );
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

	private static void addViewerNodesHandlers( final SwingNode[] viewerNodesArray )
	{
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
	}

	private void maximizeActiveOrthoView( final Scene scene, final Event event )
	{
		final Node focusOwner = scene.focusOwnerProperty().get();
		if ( Arrays.asList( viewerNodesArray ).contains( focusOwner ) )
		{
			event.consume();
			if ( !isFullScreen[ 0 ] )
			{
				Arrays.asList( viewerNodesArray ).forEach( node -> node.setVisible( node == focusOwner ) );
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
				Arrays.asList( viewerNodesArray ).forEach( node -> node.setVisible( true ) );
				Arrays.asList( viewerNodesArray ).forEach( node -> ( ( ViewerPanel ) node.getContent() ).requestRepaint() );
				infoPane.setVisible( true );
				grid.setHgap( 1 );
				grid.setVgap( 1 );
			}
			isFullScreen[ 0 ] = !isFullScreen[ 0 ];
		}
	}

	private void toggleVisibilityOrSetActiveSource( final Scene scene, final KeyEvent event )
	{
		if ( event.isAltDown() || event.isConsumed() || event.isControlDown() || event.isMetaDown() || event.isShortcutDown() )
			return;
		final Node focusOwner = scene.focusOwnerProperty().get();

		if ( Arrays.asList( viewerNodesArray ).contains( focusOwner ) )
		{

			final int number;
			switch ( event.getCode() )
			{
			case DIGIT1:
				number = 0;
				break;
			case DIGIT2:
				number = 1;
				break;
			case DIGIT3:
				number = 2;
				break;
			case DIGIT4:
				number = 3;
				break;
			case DIGIT5:
				number = 4;
				break;
			case DIGIT6:
				number = 5;
				break;
			case DIGIT7:
				number = 6;
				break;
			case DIGIT8:
				number = 7;
				break;
			case DIGIT9:
				number = 8;
				break;
			case DIGIT0:
				number = 9;
				break;
			default:
				number = -1;
				break;
			}
			System.out.println( "NUMBER IS " + number );
			if ( event.isShiftDown() )
				toggleSourceVisibility( number );
			else
				setCurrentSource( number );

			event.consume();
		}
	}

	private void toggleInterpolation( final Scene scene, final KeyEvent event )
	{
		toggleInterpolation();
		event.consume();
	}

	public void start( final Stage primaryStage ) throws Exception
	{

		final Scene scene = new Scene( grid, 500, 500 );

		primaryStage.setTitle( "BigCAT" );
		primaryStage.setScene( scene );

		scene.setOnKeyTyped( event -> {
			if ( event.getCharacter().equals( "a" ) )
				maximizeActiveOrthoView( scene, event );
			else if ( event.getCharacter().equals( "i" ) )
				toggleInterpolation( scene, event );
		} );

		scene.setOnKeyPressed( event -> {
			System.out.println( "PRESSING! " + event );
			System.out.println( " code " + event.getCode() );
			if ( event.getCode().isDigitKey() )
				toggleVisibilityOrSetActiveSource( scene, event );
		});

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

		primaryStage.show();
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

	public Pair< ViewerPanel, ViewerTransformManager > makeViewer(
			final List< SourceAndConverter< ? > > sacs,
			final int numTimePoints,
			final CacheControl cacheControl,
			final SwingNode swingNode,
			final GlobalTransformManager gm,
			final AffineTransform3D tf )
	{

		final ViewerPanel viewer = new ViewerPanel( sacs, numTimePoints, cacheControl );
		viewer.setDisplayMode( DisplayMode.FUSED );
		viewer.setMinimumSize( new Dimension( 100, 100 ) );

		final InputActionBindings keybindings = new InputActionBindings();
		final TriggerBehaviourBindings triggerbindings = new TriggerBehaviourBindings();
		final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();

		final ViewerTransformManager vtm = new ViewerTransformManager( gm, tf, viewer );
		viewer.getDisplay().setTransformEventHandler( vtm );
		vtm.install( triggerbindings );

		final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
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

		swingNode.setContent( viewer );
		SwingUtilities.replaceUIActionMap( viewer.getRootPane(), keybindings.getConcatenatedActionMap() );
		SwingUtilities.replaceUIInputMap( viewer.getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );

//		OrthoViewNavigationActions.installActionBindings( keybindings, viewer, inputTriggerConfig );

		return new ValuePair<>( viewer, vtm );
	}

	private ViewerPanel[] createSwingContent( final SwingNode swingNode1, final SwingNode swingNode2, final SwingNode swingNode3 )
	{
		final ViewerPanel[] viewers = new ViewerPanel[ 3 ];
		SwingUtilities.invokeLater( () -> {
			// this is just temporary: sleep until all javafx is set up to avoid width == 0 or height == 0 exceptions
//			whiel( swingNode1.getBoundsInParent().)
			while ( true )
			{
				final Bounds b1 = swingNode1.getBoundsInParent();
				final Bounds b2 = swingNode2.getBoundsInParent();
				final Bounds b3 = swingNode3.getBoundsInParent();
				if ( b1.getWidth() > 0 || b1.getHeight() > 0 || b2.getWidth() > 0 || b2.getHeight() > 0 || b3.getWidth() > 0 || b3.getHeight() > 0 )
					break;
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

			final GlobalTransformManager gm = new GlobalTransformManager( new AffineTransform3D() );

			gm.setTransform( new AffineTransform3D() );

			final AffineTransform3D tf1 = new AffineTransform3D();
			final AffineTransform3D tf2 = new AffineTransform3D();
			final AffineTransform3D tf3 = new AffineTransform3D();
			tf2.rotate( 1, Math.PI / 2 );
			tf3.rotate( 0, -Math.PI / 2 );

			tf2.set( tf2.copy() );
			tf3.set( tf3.copy() );

			System.out.println( "WAS IST DA LOS?" );
			System.out.println( tf2 );
			System.out.println( tf2.inverse() );


			final Pair< ViewerPanel, ViewerTransformManager > viewerAndManager1 = makeViewer( new ArrayList<>(), 1, new CacheControl.Dummy(), swingNode1, gm, tf1 );
			final Pair< ViewerPanel, ViewerTransformManager > viewerAndManager2 = makeViewer( new ArrayList<>(), 1, new CacheControl.Dummy(), swingNode2, gm, tf2 );
			final Pair< ViewerPanel, ViewerTransformManager > viewerAndManager3 = makeViewer( new ArrayList<>(), 1, new CacheControl.Dummy(), swingNode3, gm, tf3 );

			final ViewerPanel viewer1 = viewerAndManager1.getA();
			final ViewerPanel viewer2 = viewerAndManager2.getA();
			final ViewerPanel viewer3 = viewerAndManager3.getA();

			viewer1.setPreferredSize( new Dimension( 200, 200 ) );
			viewer2.setPreferredSize( new Dimension( 200, 200 ) );
			viewer3.setPreferredSize( new Dimension( 200, 200 ) );

			viewers[ 0 ] = viewer1;
			viewers[ 1 ] = viewer2;
			viewers[ 2 ] = viewer3;

			managers[ 0 ] = viewerAndManager1.getB();
			managers[ 1 ] = viewerAndManager2.getB();
			managers[ 2 ] = viewerAndManager3.getB();

			createdViewers = true;

		} );
		return viewers;
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