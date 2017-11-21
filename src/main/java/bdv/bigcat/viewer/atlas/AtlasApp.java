package bdv.bigcat.viewer.atlas;

import bdv.util.volatiles.SharedQueue;
import javafx.application.Application;
import javafx.stage.Stage;

public class AtlasApp extends Application
{

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final SharedQueue sharedQueue = new SharedQueue( 1, 20 );
		final Atlas atlas = new Atlas( sharedQueue );
		atlas.start( primaryStage );
	}

	public static void main( final String[] args )
	{
		launch( args );
	}

}
