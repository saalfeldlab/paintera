package bdv.bigcat.viewer;

import javafx.application.Application;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class ExampleApplication3 extends Application
{
	public static void main( final String[] args )
	{
		launch( args );
	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		System.out.println( "creating viewer... " );
		final StackPane stackPane = new StackPane();

		final Viewer3D viewer3D = new Viewer3D( "Marching Cubes", 500, 500, false );
		stackPane.getChildren().addAll( viewer3D.getPanel() );

		final javafx.scene.Scene scene = new javafx.scene.Scene( stackPane );
		primaryStage.setScene( scene );
		primaryStage.show();

		new Thread( () -> {
			viewer3D.main();
		} ).start();

	}
}
