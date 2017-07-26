package bdv.bigcat.viewer;

import graphics.scenery.utils.SceneryPanel;
import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class ExampleApplication3 extends Application
{
	private static Viewer3D viewer3D;

	public static void main( String[] args )
	{
		launch( args );
	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final SceneryPanel[] imagePanel = { null };

		System.out.println( "run later " );
		Stage stage = new Stage();
		stage.setTitle( "Marching Cubes" );

		StackPane stackPane = new StackPane();
		stackPane.setBackground(
				new Background( new BackgroundFill( Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY ) ) );

		GridPane pane = new GridPane();
		Label label = new Label( "Marching Cubes" );

		imagePanel[ 0 ] = new SceneryPanel( 500, 500 );

		GridPane.setHgrow( imagePanel[ 0 ], Priority.ALWAYS );
		GridPane.setVgrow( imagePanel[ 0 ], Priority.ALWAYS );

		GridPane.setFillHeight( imagePanel[ 0 ], true );
		GridPane.setFillWidth( imagePanel[ 0 ], true );

		GridPane.setHgrow( label, Priority.ALWAYS );
		GridPane.setHalignment( label, HPos.CENTER );
		GridPane.setValignment( label, VPos.BOTTOM );

		label.maxWidthProperty().bind( pane.widthProperty() );

		pane.setStyle( "-fx-background-color: rgb(20, 255, 20);" + "-fx-font-family: Consolas;"
				+ "-fx-font-weight: 400;" + "-fx-font-size: 1.2em;" + "-fx-text-fill: white;"
				+ "-fx-text-alignment: center;" );

		label.setStyle( "-fx-padding: 0.2em;" + "-fx-text-fill: black;" );

		label.setTextAlignment( TextAlignment.CENTER );

		pane.add( imagePanel[ 0 ], 1, 1 );
		pane.add( label, 1, 2 );
		stackPane.getChildren().addAll( pane );

		javafx.scene.Scene scene = new javafx.scene.Scene( stackPane );
		stage.setScene( scene );
		stage.show();

		System.out.println( "creating viewer... " );
		viewer3D = new Viewer3D();
		System.out.println( "defining panel... " );
		viewer3D.setPanel( imagePanel[ 0 ] );
		System.out.println( "... " );
		viewer3D.createViewer3D();
	}
}
