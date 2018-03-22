package bdv.bigcat.viewer.atlas.ui.source.mesh;

import java.io.File;
import java.util.Optional;

import org.controlsfx.control.StatusBar;

import bdv.bigcat.viewer.meshes.MeshExporter;
import bdv.bigcat.viewer.meshes.MeshExporterBinary;
import bdv.bigcat.viewer.meshes.MeshExporterObj;
import bdv.bigcat.viewer.meshes.MeshInfo;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public class MeshExporterDialog extends Dialog< ExportResult >
{
	private final TextField scale;

	private MeshExporter meshExporter;

	private final long segmentId;

	private final TextField filePath;

	public MeshExporterDialog( final MeshInfo meshInfo )
	{
		super();
		this.setTitle( "Export mesh..." );
		this.segmentId = meshInfo.segmentId();
		this.filePath = new TextField();
		scale = new TextField( Integer.toString( meshInfo.scaleLevelProperty().get() ) );

		setNumericTextField( scale, meshInfo.numScaleLevels() - 1 );
		setResultConverter( button -> {
			if ( button.getButtonData().isCancelButton() )
				return null;
			// here you can also check what button was pressed
			// and return things accordingly
			return new ExportResult( meshExporter, segmentId, Integer.parseInt( scale.getText() ), filePath.getText() );
		} );

		createDialog();

	}

	private void createDialog()
	{
		final VBox vbox = new VBox();
		final TitledPane pane = new TitledPane( null, vbox );

		final StatusBar statusBar = new StatusBar();
		// TODO come up with better way to ensure proper size of this!
		statusBar.setMinWidth( 200 );
		statusBar.setMaxWidth( 200 );
		statusBar.setPrefWidth( 200 );
		statusBar.setText( "Export mesh" );
		pane.setGraphic( statusBar );

		final GridPane contents = new GridPane();

		int row = 0;

		contents.add( new Label( "Scale" ), 0, row );
		contents.add( scale, 1, row );
		++row;

		contents.add( new Label( "Format" ), 0, row );

		ObservableList< String > options = FXCollections.observableArrayList( ".obj", "binary" );
		final ComboBox< String > formats = new ComboBox<>(options);
		formats.getSelectionModel().select( 0 );
		contents.add( formats, 1, row );
		++row;
		
		contents.add( new Label( "Directory" ), 0, row );
		contents.add( filePath, 1, row );

		final Button button = new Button( "Save to..." );
		button.setOnAction( event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File directory = directoryChooser.showDialog( contents.getScene().getWindow() );
			Optional.ofNullable( directory ).map( File::getAbsolutePath );

			String filename = directory + "/neuron" + segmentId;
			if ( formats.getSelectionModel().getSelectedIndex() == 0 )
			{
				filename = filename.concat( ".obj" );
				meshExporter = new MeshExporterObj();
			}
			else
			{
				filename = filename.concat( ".bin" );
				meshExporter = new MeshExporterBinary();
			}

			filePath.setText( filename );

		} );

		contents.add( button, 2, row );
		++row;

		vbox.getChildren().add( contents );

		this.getDialogPane().setContent( vbox );
		this.getDialogPane().getButtonTypes().addAll( ButtonType.CANCEL, ButtonType.OK );

	}

	private void setNumericTextField( final TextField textField, final int max )
	{
		// force the field to be numeric only
		textField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override
			public void changed( ObservableValue< ? extends String > observable, String oldValue,
					String newValue )
			{
				if ( !newValue.matches( "\\d*" ) )
				{
					textField.setText( newValue.replaceAll( "[^\\d]", "" ) );
				}

				if ( Integer.parseInt( textField.getText() ) > max )
				{
					textField.setText( Integer.toString( max ) );
				}
			}
		} );

	}
}
