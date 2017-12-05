package bdv.bigcat.viewer.atlas.opendialog;

import java.util.Optional;

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
import javafx.scene.control.TextField;
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

	private final SimpleObjectProperty< String > name = new SimpleObjectProperty<>( "" );

	private final ObservableMap< BACKEND, BackendDialog > backendInfoDialogs = FXCollections.observableHashMap();
	{
		backendInfoDialogs.put( BACKEND.N5, new BackendDialogN5() );
	}

	private final MetaPanel metaPanel = new MetaPanel();

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
		final TextField nameField = new TextField();
		nameField.textProperty().bindBidirectional( this.name );
		nameField.setPromptText( "source name (required)" );
		this.dialogContent = new VBox( 10, nameField, grid, errorInfo, metaPanel.getPane() );
		this.setResizable( true );

		this.name.addListener( ( obs, oldv, newv ) -> this.getDialogPane().lookupButton( ButtonType.OK ).setDisable( newv == null || newv.length() == 0 ) );

		GridPane.setMargin( this.backendDialog, new Insets( 0, 0, 0, 30 ) );
		this.grid.add( this.backendDialog, 1, 0 );
		GridPane.setHgrow( this.backendDialog, Priority.ALWAYS );

		this.getDialogPane().setContent( dialogContent );
		final VBox choices = new VBox();
		this.backendChoice = new ComboBox<>( backendChoices );
		this.typeChoice = new ComboBox<>( typeChoices );
		this.metaPanel.bindDataTypeTo( this.typeChoice.valueProperty() );

		this.backendChoice.valueProperty().addListener( ( obs, oldv, newv ) -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				final BackendDialog backendDialog = Optional.ofNullable( backendInfoDialogs.get( newv ) ).orElse( new BackendDialogInvalid( newv ) );
				this.backendDialog.getChildren().setAll( backendDialog.getDialogNode() );
				this.errorMessage.bind( backendDialog.errorMessage() );
				this.currentBackend.set( backendDialog );

				this.metaPanel.listenOnResolution( backendDialog.resolutionX(), backendDialog.resolutionY(), backendDialog.resolutionZ() );
				this.metaPanel.listenOnOffset( backendDialog.offsetX(), backendDialog.offsetY(), backendDialog.offsetZ() );
				this.metaPanel.listenOnMinMax( backendDialog.min(), backendDialog.max() );

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

	public String getName()
	{
		return name.get();
	}

	public MetaPanel getMeta()
	{
		return this.metaPanel;
	}

}
