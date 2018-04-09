package bdv.bigcat.viewer.atlas;

import java.util.Optional;

import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

public class AtlasApp extends Application
{

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final SharedQueue sharedQueue = new SharedQueue( 1, 20 );
		final Atlas atlas = new Atlas( sharedQueue );
		atlas.start( primaryStage );

		Platform.setImplicitExit( true );

		atlas.baseView().sceneProperty().get().addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( atlas.keyTracker().areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.M ) )
			{
				final Source< ? > currentSource = atlas.sourceInfo().currentSourceProperty().get();
				final AtlasSourceState< ?, ? > currentSourceState = atlas.sourceInfo().getState( currentSource );
				final FragmentSegmentAssignmentState< ? > assignment = currentSourceState.assignmentProperty().get();
				Optional.ofNullable( assignment ).ifPresent( a -> a.persist() );
				event.consume();
			}
		} );

	}

	public static void main( final String[] args )
	{
		launch( args );
	}

}
