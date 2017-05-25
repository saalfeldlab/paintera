package bdv.bigcat;

import java.awt.Dimension;
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
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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

		@SuppressWarnings( "unchecked" )
		@Override
		public void start( final Stage primaryStage ) throws Exception
		{
//			final StackPane root = new StackPane();
			final GridPane root = new GridPane();

			final SwingNode swingNode1 = new SwingNode();
			final SwingNode swingNode2 = new SwingNode();
			final SwingNode swingNode3 = new SwingNode();

			final TableView< ? > table = new TableView<>();
			table.setEditable( true );
			table.getColumns().addAll( new TableColumn<>( "Property" ), new TableColumn<>( "Value" ) );

			final TextField tf = new TextField( "some text" );

			root.setHgap( 1 );
			root.setVgap( 1 );

			final Scene scene = new Scene( root, 500, 500 );

			final VBox jfxStuff = new VBox( 1 );
			jfxStuff.getChildren().addAll( tf, table );

			GridPane.setConstraints( swingNode1, 0, 0 );
			GridPane.setConstraints( swingNode2, 1, 0 );
			GridPane.setConstraints( swingNode3, 0, 1 );
			GridPane.setConstraints( jfxStuff, 1, 1 );
			root.getChildren().add( swingNode1 );
			root.getChildren().add( swingNode2 );
			root.getChildren().add( swingNode3 );
			root.getChildren().add( jfxStuff );

			primaryStage.setTitle( "BDV" );
			primaryStage.setScene( scene );
			createSwingContent( swingNode1, swingNode2, swingNode3, root );
			primaryStage.show();
		}

		private void createSwingContent( final SwingNode swingNode1, final SwingNode swingNode2, final SwingNode swingNode3, final Pane root )
		{
			SwingUtilities.invokeLater( () -> {
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

		swingNode.addEventHandler( MouseEvent.MOUSE_CLICKED, event -> swingNode.requestFocus() );

		swingNode.addEventHandler( MouseEvent.MOUSE_ENTERED, event -> {
			if ( swingNode.sceneProperty().get().focusOwnerProperty().get() instanceof SwingNode )
				swingNode.requestFocus();
		} );

		swingNode.setContent( viewer );
		SwingUtilities.replaceUIActionMap( viewer.getRootPane(), keybindings.getConcatenatedActionMap() );
		SwingUtilities.replaceUIInputMap( viewer.getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );

		return viewer;
	}

}
