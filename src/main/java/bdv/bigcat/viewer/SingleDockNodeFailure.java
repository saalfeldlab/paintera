package bdv.bigcat.viewer;

import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class SingleDockNodeFailure extends Application
{

	public static void main( final String[] args )
	{
		launch( args );
	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final DockPane dock = new DockPane();


		final Label label = new Label( "BLABLA" );
		final DockNode dockedLabel = new DockNode( label );
		dockedLabel.dock( dock, DockPos.TOP );

		final Scene scene = new Scene( dock, 500, 500 );
		primaryStage.setScene( scene );
		primaryStage.sizeToScene();

		primaryStage.show();
		Application.setUserAgentStylesheet( Application.STYLESHEET_MODENA );

		DockPane.initializeDefaultUserAgentStylesheet();
	}

}
