package bdv.bigcat.viewer.panel;

import com.sun.javafx.application.PlatformImpl;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class Dummy
{

	public static void main( final String[] args )
	{
		PlatformImpl.startup( () -> {} );
		Platform.runLater( () -> {
			final Pane top = new HBox();
			final Pane bottom = new Pane();
			top.getChildren().add( bottom );
			bottom.addEventHandler( KeyEvent.KEY_PRESSED, event -> System.out.println( "bottom " + event + " " + event.getCode() ) );
			top.addEventHandler( KeyEvent.KEY_PRESSED, event -> System.out.println( "top " + event + " " + event.getCode() ) );
			top.focusedProperty().addListener( ( obs, oldv, newv ) -> {
				if ( newv )
					bottom.requestFocus();
			} );
			final Stage stage = new Stage();
			final Scene scene = new Scene( top, 500, 500 );
			stage.setScene( scene );
			stage.show();
			top.requestFocus();
		} );
	}

}
