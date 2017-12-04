package bdv.bigcat.viewer.atlas.opendialog;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.UnaryOperator;

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
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

	private final SimpleObjectProperty< String > name = new SimpleObjectProperty<>( "source name" );

	private final ObservableMap< BACKEND, BackendDialog > backendInfoDialogs = FXCollections.observableHashMap();
	{
		backendInfoDialogs.put( BACKEND.N5, new BackendDialogN5() );
		backendInfoDialogs.put( BACKEND.HDF5, new BackendDialogHDF5() );
		backendInfoDialogs.put( BACKEND.DVID, new BackendDialogDVID() );
	}

	private final TextField resX = new TextField( "1.0" );

	private final TextField resY = new TextField( "1.0" );

	private final TextField resZ = new TextField( "1.0" );

	private final TextField offX = new TextField( "0.0" );

	private final TextField offY = new TextField( "0.0" );

	private final TextField offZ = new TextField( "0.0" );

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
		final GridPane resolutionAndOffset = new GridPane();
		final TextField nameField = new TextField();
		nameField.textProperty().bindBidirectional( this.name );
		this.dialogContent = new VBox( 10, nameField, grid, errorInfo, resolutionAndOffset );
		this.setResizable( true );

		GridPane.setMargin( this.backendDialog, new Insets( 0, 0, 0, 30 ) );
		this.grid.add( this.backendDialog, 1, 0 );
		GridPane.setHgrow( this.backendDialog, Priority.ALWAYS );

		this.getDialogPane().setContent( dialogContent );
		final VBox choices = new VBox();
		this.backendChoice = new ComboBox<>( backendChoices );
		this.typeChoice = new ComboBox<>( typeChoices );

		this.backendChoice.valueProperty().addListener( ( obs, oldv, newv ) -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				final BackendDialog backendDialog = Optional.ofNullable( backendInfoDialogs.get( newv ) ).orElse( new BackendDialogInvalid( newv ) );
				this.backendDialog.getChildren().setAll( backendDialog.getDialogNode() );
				this.errorMessage.bind( backendDialog.errorMessage() );
				this.currentBackend.set( backendDialog );
//				this.getDialogPane().getScene().getWindow().sizeToScene();

				backendDialog.resolutionX().addListener( ( obsRes, oldRes, newRes ) -> {
					if ( Double.isFinite( newRes.doubleValue() ) )
						this.resX.setText( newRes.toString() );
				} );

				backendDialog.resolutionY().addListener( ( obsRes, oldRes, newRes ) -> {
					if ( Double.isFinite( newRes.doubleValue() ) )
						this.resY.setText( newRes.toString() );
				} );

				backendDialog.resolutionZ().addListener( ( obsRes, oldRes, newRes ) -> {
					if ( Double.isFinite( newRes.doubleValue() ) )
						this.resZ.setText( newRes.toString() );
				} );

				backendDialog.offsetX().addListener( ( obsRes, oldRes, newRes ) -> {
					if ( Double.isFinite( newRes.doubleValue() ) )
						this.offX.setText( newRes.toString() );
				} );

				backendDialog.offsetY().addListener( ( obsRes, oldRes, newRes ) -> {
					if ( Double.isFinite( newRes.doubleValue() ) )
						this.offY.setText( newRes.toString() );
				} );

				backendDialog.offsetZ().addListener( ( obsRes, oldRes, newRes ) -> {
					if ( Double.isFinite( newRes.doubleValue() ) )
						this.offZ.setText( newRes.toString() );
				} );

				if ( Double.isFinite( backendDialog.resolutionX().get() ) )
					this.resX.setText( Double.toString( backendDialog.resolutionX().get() ) );

				if ( Double.isFinite( backendDialog.resolutionY().get() ) )
					this.resY.setText( Double.toString( backendDialog.resolutionY().get() ) );

				if ( Double.isFinite( backendDialog.resolutionZ().get() ) )
					this.resZ.setText( Double.toString( backendDialog.resolutionZ().get() ) );

				if ( Double.isFinite( backendDialog.offsetX().get() ) )
					this.offX.setText( Double.toString( backendDialog.offsetX().get() ) );

				if ( Double.isFinite( backendDialog.resolutionY().get() ) )
					this.offY.setText( Double.toString( backendDialog.offsetY().get() ) );

				if ( Double.isFinite( backendDialog.resolutionZ().get() ) )
					this.offZ.setText( Double.toString( backendDialog.offsetZ().get() ) );

			} );
		} );

		this.backendChoice.setValue( backendChoices.get( 0 ) );
		this.typeChoice.setValue( typeChoices.get( 0 ) );
		this.backendChoice.setMinWidth( 100 );
		this.typeChoice.setMinWidth( 100 );
		choices.getChildren().addAll( this.backendChoice, this.typeChoice );
		this.grid.add( choices, 0, 0 );
		this.setResultConverter( button -> button.equals( ButtonType.OK ) ? currentBackend.get() : new BackendDialogInvalid( backendChoice.getValue() ) );

		resX.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		resY.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		resZ.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );

		offX.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		offY.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );
		offZ.setTextFormatter( new TextFormatter<>( new DoubleFilter() ) );

		final Label resolutionLabel = new Label();
		final Label offsetLabel = new Label();
		resolutionLabel.minWidthProperty().bindBidirectional( offsetLabel.minWidthProperty() );
		resolutionLabel.maxWidthProperty().bindBidirectional( offsetLabel.maxWidthProperty() );
		resolutionLabel.prefWidthProperty().bindBidirectional( offsetLabel.prefWidthProperty() );
		resolutionLabel.setText( "resolution" );
		offsetLabel.setText( "offset" );
		offsetLabel.setMinWidth( Region.USE_PREF_SIZE );
		resolutionAndOffset.add( resolutionLabel, 0, 0 );
		resolutionAndOffset.add( offsetLabel, 0, 1 );
		resolutionAndOffset.add( resX, 1, 0 );
		resolutionAndOffset.add( resY, 2, 0 );
		resolutionAndOffset.add( resZ, 3, 0 );
		resolutionAndOffset.add( offX, 1, 1 );
		resolutionAndOffset.add( offY, 2, 1 );
		resolutionAndOffset.add( offZ, 3, 1 );

	}

	public TYPE getType()
	{
		return typeChoice.getValue();
	}

	public static class DoubleFilter implements UnaryOperator< Change >
	{

		@Override
		public Change apply( final Change t )
		{
			final String input = t.getText();
			return input.matches( "\\d*(\\.\\d*)?" ) ? t : null;
		}
	}

	public double[] getResolution()
	{
		return Arrays
				.asList( resX, resY, resZ )
				.stream()
				.map( res -> res.getText() )
				.mapToDouble( text -> text.length() > 0 ? Double.parseDouble( text ) : 1.0 )
				.toArray();
	}

	public double[] getOffset()
	{
		return Arrays
				.asList( offX, offY, offZ )
				.stream()
				.map( res -> res.getText() )
				.mapToDouble( text -> text.length() > 0 ? Double.parseDouble( text ) : 0.0 )
				.toArray();
	}

	public String getName()
	{
		return name.get();
	}

}
