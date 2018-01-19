package bdv.bigcat.viewer.atlas.opendialog;

import com.sun.javafx.application.PlatformImpl;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class OpenDataset
{

	private final ObjectProperty< BackendDialog > currentBackend = new SimpleObjectProperty<>();

	public Dialog< BackendDialog > open()
	{
		final Dialog< BackendDialog > dialog = new Dialog<>();

		final StackPane content = new StackPane();
		final ObservableList< BackendDialog > backends = FXCollections.observableArrayList(
				new BackendDialogN5(),
				new BackendDialogHDF5() );
		final ComboBox< BackendDialog > backendChoices = new ComboBox<>( backends );
		backendChoices.setConverter( new StringConverter< BackendDialog >()
		{

			@Override
			public String toString( final BackendDialog object )
			{
				return object.identifier();
			}

			@Override
			public BackendDialog fromString( final String string )
			{
				return backends.stream().filter( backend -> string.equals( backend.identifier() ) ).findFirst().orElse( null );
			}
		} );

		final StackPane dPane = new StackPane();
		backendChoices.valueProperty().addListener( ( obs, oldv, newv ) -> dPane.getChildren().setAll( newv.getDialogNode() ) );
		backendChoices.setValue( backends.get( 0 ) );

		final HBox bla = new HBox( backendChoices, dPane );
		content.getChildren().add( bla );

		dialog.getDialogPane().setContent( content );

		dialog.setResultConverter( db -> {

			return db == ButtonType.OK ? currentBackend.get() : null;
		} );
		return dialog;
	}

	public static void main( final String[] args )
	{
		PlatformImpl.startup( () -> {} );

		Platform.runLater( () -> {

			final Stage stage = new Stage();
			final Button b = new Button( "CLICK!" );
			final Scene scene = new Scene( b, 500, 500 );
			stage.setScene( scene );
			stage.show();
			final OpenDataset ds = new OpenDataset();
			b.onActionProperty().set( event -> ds.open().show() );
		} );
	}

}
