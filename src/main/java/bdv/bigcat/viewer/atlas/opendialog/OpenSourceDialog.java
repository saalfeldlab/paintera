package bdv.bigcat.viewer.atlas.opendialog;

import java.util.Optional;

import com.sun.javafx.application.PlatformImpl;

import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class OpenSourceDialog extends Dialog< BackendDialog >
{

	public static enum BACKEND
	{
		N5, HDF5, DVID
	};

	public static enum TYPE
	{
		RAW, LABEL
	};

	private final VBox dialogContent;

	private final GridPane grid;

	private final StackPane backendDialog;

	private final ComboBox< BACKEND > backendChoice;

	private final ComboBox< TYPE > typeChoice;

	private final Label errorInfo;

	private final ObservableList< BACKEND > backendChoices = FXCollections.observableArrayList( BACKEND.values() );

	private final ObservableList< TYPE > typeChoices = FXCollections.observableArrayList( TYPE.values() );

	private final SimpleObjectProperty< String > errorMessage = new SimpleObjectProperty<>( null );

	private final SimpleObjectProperty< BackendDialog > currentBackend = new SimpleObjectProperty<>( new BackendDialogInvalid( BACKEND.N5 ) );

	private final ObservableMap< BACKEND, BackendDialog > backendInfoDialogs = FXCollections.observableHashMap();
	{
		backendInfoDialogs.put( BACKEND.N5, new BackendDialogN5() );
		backendInfoDialogs.put( BACKEND.HDF5, new BackendDialogHDF5() );
	}

	public OpenSourceDialog()
	{
		super();
		this.setTitle( "Open data set" );
		this.getDialogPane().getButtonTypes().addAll( ButtonType.CANCEL, ButtonType.OK );
		this.errorInfo = new Label( "" );

		this.errorMessage.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
			{
				this.getDialogPane().lookupButton( ButtonType.OK ).setDisable( true );
				this.errorInfo.setText( newv );
			}
			else
			{
				this.getDialogPane().lookupButton( ButtonType.OK ).setDisable( false );
				this.errorInfo.setText( newv );
			}
		} );

		this.grid = new GridPane();
		this.backendDialog = new StackPane();
		this.dialogContent = new VBox( 10, grid, errorInfo );
		this.setResizable( true );

		GridPane.setMargin( this.backendDialog, new Insets( 0, 0, 0, 30 ) );
		this.grid.add( this.backendDialog, 1, 0 );
		GridPane.setHgrow( this.backendDialog, Priority.ALWAYS );

		this.getDialogPane().setContent( dialogContent );
		final VBox choices = new VBox();
		backendDialog.widthProperty().addListener( ( obs, oldv, newv ) -> {
			System.out.println( "CHANGING SIZE TO " + newv );
		} );
		this.backendChoice = new ComboBox<>( backendChoices );
		this.typeChoice = new ComboBox<>( typeChoices );

		this.backendChoice.valueProperty().addListener( ( obs, oldv, newv ) -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				final BackendDialog backendDialog = Optional.ofNullable( backendInfoDialogs.get( newv ) ).orElse( new BackendDialogInvalid( newv ) );
				this.backendDialog.getChildren().setAll( backendDialog.getDialogNode() );
				this.errorMessage.bind( backendDialog.errorMessage() );
				this.currentBackend.set( backendDialog );
				this.getDialogPane().getScene().getWindow().sizeToScene();
			} );
		} );

		this.backendChoice.setValue( backendChoices.get( 0 ) );
		this.typeChoice.setValue( typeChoices.get( 0 ) );
		this.backendChoice.setMinWidth( 100 );
		this.typeChoice.setMinWidth( 100 );
		choices.getChildren().addAll( this.backendChoice, this.typeChoice );
		this.grid.add( choices, 0, 0 );
		this.setResultConverter( button -> button.equals( ButtonType.OK ) ? currentBackend.get() : new BackendDialogInvalid( backendChoice.getValue() ) );

	}

	public TYPE getType()
	{
		return typeChoice.getValue();
	}

	public static void main( final String[] args )
	{
		PlatformImpl.startup( () -> {} );
		PlatformImpl.runLater( () -> {
			final OpenSourceDialog d = new OpenSourceDialog();
			d.showAndWait();
		} );
	}

}
