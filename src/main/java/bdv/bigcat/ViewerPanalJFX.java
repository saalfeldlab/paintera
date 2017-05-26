package bdv.bigcat;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BehaviourTransformEventHandler;
import bdv.cache.CacheControl;
import bdv.util.AxisOrder;
import bdv.util.BdvFunctions;
import bdv.viewer.NavigationActions;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingNode;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.imglib2.converter.Converter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.ui.TransformEventHandler;

public class ViewerPanalJFX
{
	public static void main( final String[] args )
	{
		Application.launch( MyApplication.class );
	}

	public static class MyApplication extends Application
	{

		public static class GridConstraintsManager
		{

			private final double defaultColumnWidth1 = 50;

			private final double defaultColumnWidth2 = 50;

			private final double defaultRowHeight1 = 50;

			private final double defaultRowHeight2 = 50;

			private final ColumnConstraints column1 = new ColumnConstraints();

			private final ColumnConstraints column2 = new ColumnConstraints();

			final RowConstraints row1 = new RowConstraints();

			final RowConstraints row2 = new RowConstraints();

			private double columnWidth1;

			private double columnWidth2;

			private double rowHeight1;

			private double rowHeight2;

			public GridConstraintsManager()
			{
				resetToDefault();
				storeCurrent();
			}

			private synchronized final void resetToDefault()
			{
				column1.setPercentWidth( defaultColumnWidth1 );
				column2.setPercentWidth( defaultColumnWidth2 );
				row1.setPercentHeight( defaultRowHeight1 );
				row2.setPercentHeight( defaultRowHeight2 );
			}

			private synchronized final void resetToLast()
			{
				column1.setPercentWidth( columnWidth1 );
				column2.setPercentWidth( columnWidth2 );
				row1.setPercentHeight( rowHeight1 );
				row2.setPercentHeight( rowHeight2 );
			}

			private synchronized void storeCurrent()
			{
				this.columnWidth1 = column1.getPercentWidth();
				this.columnWidth2 = column2.getPercentWidth();
				this.rowHeight1 = row1.getPercentHeight();
				this.rowHeight2 = row2.getPercentHeight();
			}

			private synchronized void maximize( final int r, final int c, final int steps )
			{
				storeCurrent();
				final ColumnConstraints increaseColumn = c == 0 ? column1 : column2;
				final ColumnConstraints decreaseColumn = c == 0 ? column2 : column1;
				final RowConstraints increaseRow = r == 0 ? row1 : row2;
				final RowConstraints decreaseRow = r == 0 ? row2 : row1;
				final double increaseColumnStep = ( 100 - increaseColumn.getPercentWidth() ) / steps;
				final double decreaseColumnStep = ( decreaseColumn.getPercentWidth() - 0 ) / steps;
				final double increaseRowStep = ( 100 - increaseRow.getPercentHeight() ) / steps;
				final double decreaseRowStep = ( decreaseRow.getPercentHeight() - 0 ) / steps;

				for ( int i = 0; i < steps; ++i )
				{
					increaseColumn.setPercentWidth( increaseColumn.getPercentWidth() + increaseColumnStep );
					decreaseColumn.setPercentWidth( decreaseColumn.getPercentWidth() - decreaseColumnStep );
					increaseRow.setPercentHeight( increaseRow.getPercentHeight() + increaseRowStep );
					decreaseRow.setPercentHeight( decreaseRow.getPercentHeight() - decreaseRowStep );
				}

				increaseColumn.setPercentWidth( 100 );
				decreaseColumn.setPercentWidth( 0 );
				increaseRow.setPercentHeight( 100 );
				decreaseRow.setPercentHeight( 0 );

			}

		}

		@SuppressWarnings( "unchecked" )
		@Override
		public void start( final Stage primaryStage ) throws Exception
		{
//			final StackPane root = new StackPane();
			final GridPane root = new GridPane();

			final SwingNode viewerNode1 = new SwingNode();
			final SwingNode viewerNode2 = new SwingNode();
			final SwingNode viewerNode3 = new SwingNode();
			final SwingNode[] viewerNodesArray = new SwingNode[] { viewerNode1, viewerNode2, viewerNode3 };

			final HashSet< SwingNode > viewerNodes = new HashSet<>( Arrays.asList( viewerNodesArray ) );

			final Class< ? >[] focusKeepers = { TextField.class };
			for ( int i = 0; i < viewerNodesArray.length; ++i )
			{
				final SwingNode viewerNode = viewerNodesArray[ i ];
//				final DropShadow ds = new DropShadow( 10, Color.PURPLE );
				final DropShadow ds = new DropShadow( 10, Color.hsb( 60.0 + 360.0 * i / viewerNodes.size(), 1.0, 0.5, 1.0 ) );
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

			final TableView< ? > table = new TableView<>();
			table.setEditable( true );
			table.getColumns().addAll( new TableColumn<>( "Property" ), new TableColumn<>( "Value" ) );

			final TextField tf = new TextField( "some text" );

			root.setHgap( 1 );
			root.setVgap( 1 );

			final Scene scene = new Scene( root, 500, 500 );

			final TabPane infoPane = new TabPane();

			final VBox jfxStuff = new VBox( 1 );
			jfxStuff.getChildren().addAll( tf, table );
			infoPane.getTabs().add( new Tab( "jfx stuff", jfxStuff ) );

			infoPane.getTabs().add( new Tab( "dataset info", new Label( "random floats" ) ) );

			final GridConstraintsManager gridConstraintsManager = new GridConstraintsManager();

			GridPane.setConstraints( viewerNode1, 0, 0 );
			GridPane.setConstraints( viewerNode2, 1, 0 );
			GridPane.setConstraints( viewerNode3, 0, 1 );
			GridPane.setConstraints( infoPane, 1, 1 );
			root.getChildren().add( viewerNode1 );
			root.getChildren().add( viewerNode2 );
			root.getChildren().add( viewerNode3 );
			root.getChildren().add( infoPane );

			root.getColumnConstraints().add( gridConstraintsManager.column1 );
			root.getColumnConstraints().add( gridConstraintsManager.column2 );

			root.getRowConstraints().add( gridConstraintsManager.row1 );
			root.getRowConstraints().add( gridConstraintsManager.row2 );

			final boolean[] isFullScreen = new boolean[] { false };

			primaryStage.setTitle( "BigCAT" );
			primaryStage.setScene( scene );
			createSwingContent( viewerNode1, viewerNode2, viewerNode3, root );

			scene.setOnKeyTyped( event -> {
				if ( event.getCharacter().equals( "a" ) )
				{
					final Node focusOwner = scene.focusOwnerProperty().get();
					if ( viewerNodes.contains( focusOwner ) )
					{
						event.consume();
						if ( !isFullScreen[ 0 ] )
						{
							viewerNodes.forEach( node -> node.setVisible( node == focusOwner ) );
							infoPane.setVisible( false );
							gridConstraintsManager.maximize(
									GridPane.getRowIndex( focusOwner ),
									GridPane.getColumnIndex( focusOwner ),
									0 );
							( ( ViewerPanel ) ( ( SwingNode ) focusOwner ).getContent() ).requestRepaint();
							root.setHgap( 0 );
							root.setVgap( 0 );
						}
						else
						{
							gridConstraintsManager.resetToLast();
							viewerNodes.forEach( node -> node.setVisible( true ) );
							viewerNodes.forEach( node -> ( ( ViewerPanel ) node.getContent() ).requestRepaint() );
							infoPane.setVisible( true );
							root.setHgap( 1 );
							root.setVgap( 1 );
						}
						isFullScreen[ 0 ] = !isFullScreen[ 0 ];
					}
				}
			} );

			primaryStage.show();
		}

		private void createSwingContent( final SwingNode swingNode1, final SwingNode swingNode2, final SwingNode swingNode3, final Pane root )
		{
			SwingUtilities.invokeLater( () -> {
				// this is just temporary: sleep until all javafx is set up to
				// avoid width == 0 or height == 0 exceptions
				try
				{
					Thread.sleep( 100 );
				}
				catch ( final InterruptedException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				final Random rng = new Random( 100 );
				final Img< FloatType > rai = ArrayImgs.floats( 100, 200, 300 );

				for ( final FloatType f1 : rai )
					f1.set( rng.nextFloat() );

				final Converter< FloatType, ARGBType > conv = ( s, t ) -> {
					t.set( ARGBType.rgba( 0, s.getRealDouble() * 255, 0, 0 ) );
				};

				final AffineTransform3D tf = new AffineTransform3D();

				final List< SourceAndConverter< FloatType > > sacs = BdvFunctions.toSourceAndConverter( rai, conv, AxisOrder.XYZ, tf, "ok" );
				final List< SourceAndConverter< ? > > sacsWildcard = sacs.stream().map( sac -> (SourceAndConverter< ? >)sac ).collect( Collectors.toList() );

				final ViewerPanel viewer1 = makeViewer( sacsWildcard, 1, new CacheControl.Dummy(), swingNode1, root );
				final ViewerPanel viewer2 = makeViewer( sacsWildcard, 1, new CacheControl.Dummy(), swingNode2, root );
				final ViewerPanel viewer3 = makeViewer( sacsWildcard, 1, new CacheControl.Dummy(), swingNode3, root );



				swingNode1.setVisible( true );
				swingNode2.setVisible( true );
				swingNode3.setVisible( true );
				viewer1.setVisible( true );
				viewer2.setVisible( true );
				viewer3.setVisible( true );


			} );
		}
	}

	public static ViewerPanel makeViewer(
			final List< SourceAndConverter< ? > > sacs,
			final int numTimePoints,
			final CacheControl cacheControl,
			final SwingNode swingNode,
			final Pane root )
	{

		final ViewerPanel viewer = new ViewerPanel( sacs, numTimePoints, cacheControl );
		viewer.setMinimumSize( new Dimension( 100, 100 ) );

		final InputActionBindings keybindings = new InputActionBindings();
		final TriggerBehaviourBindings triggerbindings = new TriggerBehaviourBindings();
		final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();

		final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
		mouseAndKeyHandler.setInputMap( triggerbindings.getConcatenatedInputTriggerMap() );
		mouseAndKeyHandler.setBehaviourMap( triggerbindings.getConcatenatedBehaviourMap() );
		viewer.getDisplay().addHandler( mouseAndKeyHandler );

		final TransformEventHandler< ? > tfHandler = viewer.getDisplay().getTransformEventHandler();
		if ( tfHandler instanceof BehaviourTransformEventHandler )
			( ( BehaviourTransformEventHandler< ? > ) tfHandler ).install( triggerbindings );

		NavigationActions.installActionBindings( keybindings, viewer, inputTriggerConfig );

		swingNode.setContent( viewer );
		SwingUtilities.replaceUIActionMap( viewer.getRootPane(), keybindings.getConcatenatedActionMap() );
		SwingUtilities.replaceUIInputMap( viewer.getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );

		return viewer;
	}

}
