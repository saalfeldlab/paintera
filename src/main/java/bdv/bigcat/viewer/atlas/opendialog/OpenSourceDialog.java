package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

import bdv.bigcat.viewer.atlas.opendialog.meta.MetaPanel;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;

public class OpenSourceDialog extends Dialog< BackendDialog > implements CombinesErrorMessages
{

	public static Color TEXTFIELD_ERROR = Color.ORANGE;

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

	private final Label errorMessage;

	private final TitledPane errorInfo;

	private final ObservableBooleanValue isLabelType;

	private final ObservableList< BACKEND > backendChoices = FXCollections.observableArrayList( BACKEND.values() );

	private final ObservableList< TYPE > typeChoices = FXCollections.observableArrayList( TYPE.values() );

	private final SimpleObjectProperty< BackendDialog > currentBackend = new SimpleObjectProperty<>( new BackendDialogN5() );

	private final NameField nameField = new NameField( "Source name", "Specify source name (required)", new InnerShadow( 10, Color.ORANGE ) );

	private final BooleanBinding isError;

	private final ObservableMap< BACKEND, BackendDialog > backendInfoDialogs = FXCollections.observableHashMap();
	{
		backendInfoDialogs.put( BACKEND.N5, new BackendDialogN5() );
		backendInfoDialogs.put( BACKEND.HDF5, new BackendDialogHDF5() );
	}

	private final MetaPanel metaPanel = new MetaPanel();

	private final CheckBox usePaintingLayer = new CheckBox();

	private final TextField paintingCacheDirectory = new TextField( "" );

	private final Button paintingCacheDirectoryChooserButton = new Button( "Browse" );

	private final HBox paintingCacheDirectoryBox = new HBox( paintingCacheDirectory, paintingCacheDirectoryChooserButton );

	private final TitledPane paintingInfoPane = new TitledPane( "Painting Layer", paintingCacheDirectoryBox );

	private final HBox paintingLayerPane = new HBox( paintingInfoPane );

	public OpenSourceDialog()
	{
		super();
		this.setTitle( "Open data set" );
		this.getDialogPane().getButtonTypes().addAll( ButtonType.CANCEL, ButtonType.OK );
		this.errorMessage = new Label( "" );
		this.errorInfo = new TitledPane( "", errorMessage );
		this.isError = Bindings.createBooleanBinding( () -> Optional.ofNullable( this.errorMessage.textProperty().get() ).orElse( "" ).length() > 0, this.errorMessage.textProperty() );
		errorInfo.textProperty().bind( Bindings.createStringBinding( () -> this.isError.get() ? "ERROR" : "", this.isError ) );

		this.getDialogPane().lookupButton( ButtonType.OK ).disableProperty().bind( this.isError );
		this.errorInfo.visibleProperty().bind( this.isError );

		this.grid = new GridPane();
		this.backendDialog = new StackPane();
		this.nameField.errorMessageProperty().addListener( ( obs, oldv, newv ) -> combineErrorMessages() );
		this.dialogContent = new VBox( 10, nameField.textField(), grid, metaPanel.getPane(), paintingLayerPane, errorInfo );
		this.setResizable( true );

		GridPane.setMargin( this.backendDialog, new Insets( 0, 0, 0, 30 ) );
		this.grid.add( this.backendDialog, 1, 0 );
		GridPane.setHgrow( this.backendDialog, Priority.ALWAYS );

		this.getDialogPane().setContent( dialogContent );
		final VBox choices = new VBox();
		this.backendChoice = new ComboBox<>( backendChoices );
		this.typeChoice = new ComboBox<>( typeChoices );
		this.metaPanel.bindDataTypeTo( this.typeChoice.valueProperty() );
		isLabelType = Bindings.createBooleanBinding( () -> Optional.ofNullable( typeChoice.getValue() ).map( b -> b.equals( TYPE.LABEL ) ).orElse( false ), this.typeChoice.valueProperty() );

		this.backendChoice.valueProperty().addListener( ( obs, oldv, newv ) -> {
			if ( this.currentBackend.get() != null )
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					final BackendDialog backendDialog = backendInfoDialogs.get( newv );
					this.backendDialog.getChildren().setAll( backendDialog.getDialogNode() );
					this.currentBackend.set( backendDialog );

					final DoubleProperty[] res = backendDialog.resolution();
					final DoubleProperty[] off = backendDialog.offset();
					this.metaPanel.listenOnResolution( res[ 0 ], res[ 1 ], res[ 2 ] );
					this.metaPanel.listenOnOffset( off[ 0 ], off[ 1 ], off[ 2 ] );
					this.metaPanel.listenOnMinMax( backendDialog.min(), backendDialog.max() );

					backendDialog.errorMessage().addListener( ( obsErr, oldErr, newErr ) -> combineErrorMessages() );
					backendDialog.nameProperty().addListener( ( obsName, oldName, newName ) -> Optional.ofNullable( newName ).ifPresent( nameField.textField().textProperty()::set ) );
					Optional.ofNullable( backendDialog.nameProperty().get() ).ifPresent( nameField.textField().textProperty()::set );
					combineErrorMessages();
				} );
		} );

		this.paintingLayerPane.visibleProperty().bind( isLabelType );
		this.usePaintingLayer.selectedProperty().addListener( ( obs, oldv, newv ) -> {
			if ( newv )
			{
				this.paintingInfoPane.setCollapsible( newv );
				this.paintingInfoPane.setExpanded( newv );
			}
			else
			{
				this.paintingInfoPane.setExpanded( newv );
				this.paintingInfoPane.setCollapsible( newv );
			}
		} );

		this.paintingCacheDirectoryChooserButton.setOnAction( event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File initDir = new File( paintingCacheDirectory.getText() );
			directoryChooser.setInitialDirectory( initDir.exists() && initDir.isDirectory() ? initDir : FileSystems.getDefault().getPath( "." ).toFile() );
			final File directory = directoryChooser.showDialog( grid.getScene().getWindow() );
			Optional.ofNullable( directory ).map( File::getAbsolutePath ).ifPresent( paintingCacheDirectory::setText );
		} );
		HBox.setHgrow( paintingInfoPane, Priority.ALWAYS );
		paintingInfoPane.setGraphic( this.usePaintingLayer );

		this.backendChoice.setValue( backendChoices.get( 0 ) );
		this.typeChoice.setValue( typeChoices.get( 0 ) );
		this.backendChoice.setMinWidth( 100 );
		this.typeChoice.setMinWidth( 100 );
		choices.getChildren().addAll( this.backendChoice, this.typeChoice );
		this.grid.add( choices, 0, 0 );
		this.setResultConverter( button -> button.equals( ButtonType.OK ) ? currentBackend.get() : null );
		combineErrorMessages();

	}

	public TYPE getType()
	{
		return typeChoice.getValue();
	}

	public String getName()
	{
		return nameField.getText();
	}

	public MetaPanel getMeta()
	{
		return this.metaPanel;
	}

	@Override
	public Collection< ObservableValue< String > > errorMessages()
	{
		return Arrays.asList( this.nameField.errorMessageProperty(), this.currentBackend.get().errorMessage() );
	}

	@Override
	public Consumer< Collection< String > > combiner()
	{
		return strings -> InvokeOnJavaFXApplicationThread.invoke( () -> this.errorMessage.setText( String.join( "\n", strings ) ) );
	}

	public boolean paint()
	{
		return this.usePaintingLayer.selectedProperty().get();
	}

	public String canvasCacheDirectory()
	{
		return this.paintingCacheDirectory.getText();
	}

}
