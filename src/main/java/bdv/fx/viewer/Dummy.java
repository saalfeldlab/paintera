package bdv.fx.viewer;

import java.util.Random;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Dummy extends Application
{

	public static void main( final String[] args )
	{
		launch( args );
	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final ResizableCanvas canvas = new ResizableCanvas();
		final StackPane root = new StackPane( canvas );
		final Scene scene = new Scene( root, 800, 600 );

		canvas.widthProperty().bind( root.widthProperty() );
		canvas.heightProperty().bind( root.heightProperty() );

		final Random rng = new Random();
		final Runnable updateCanvas = () -> {
			final int r = rng.nextInt( 255 );
			final int g = rng.nextInt( 255 );
			final int b = rng.nextInt( 255 );
			final GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.setFill( Color.rgb( r, g, b ) );
			InvokeOnJavaFXApplicationThread.invoke( () -> gc.fillRect( 0, 0, canvas.getWidth(), canvas.getHeight() ) );
		};

		canvas.widthProperty().addListener( ( obs, oldv, newv ) -> updateCanvas.run() );
		canvas.heightProperty().addListener( ( obs, oldv, newv ) -> updateCanvas.run() );

		primaryStage.setScene( scene );
		primaryStage.show();
	}

}
