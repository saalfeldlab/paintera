package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.util.volatiles.SharedQueue;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class BackendDialogHDF5 implements BackendDialog
{
	private final SimpleObjectProperty< String > hdf5 = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > datasetRaw = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > datasetLabel = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > error = new SimpleObjectProperty<>();

	{
		datasetRaw.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				error.set( null );
			else
				error.set( "No raw dataset" );
		} );

		datasetLabel.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				error.set( null );
			else
				error.set( "No label dataset" );
		} );

		hdf5.set( "" );
		datasetRaw.set( "" );
		datasetLabel.set( "" );
		error.set( "" );
	}

	@Override
	public Node getDialogNode()
	{
		final TextField hdf5Field = new TextField( hdf5.get() );
		hdf5Field.setMinWidth( 0 );
		hdf5Field.setMaxWidth( Double.POSITIVE_INFINITY );
		hdf5Field.textProperty().bindBidirectional( hdf5 );

		final TextField rawDatasetField = new TextField( datasetRaw.get() );
		rawDatasetField.setMinWidth( 0 );
		rawDatasetField.setMaxWidth( Double.POSITIVE_INFINITY );
		rawDatasetField.textProperty().bindBidirectional( datasetRaw );

		final TextField labelDatasetField = new TextField( datasetLabel.get() );
		labelDatasetField.setMinWidth( 0 );
		labelDatasetField.setMaxWidth( Double.POSITIVE_INFINITY );
		labelDatasetField.textProperty().bindBidirectional( datasetLabel );

		final GridPane grid = new GridPane();
		grid.add( new Label( "hdf5" ), 0, 0 );

		// TODO: identify if the raw/label is selected on the dialog
		grid.add( new Label( "data set" ), 0, 1 );
		grid.setMinWidth( Region.USE_PREF_SIZE );
		grid.add( hdf5Field, 1, 0 );
		grid.add( rawDatasetField, 1, 1 );
		grid.setHgap( 10 );

		GridPane.setHgrow( hdf5Field, Priority.ALWAYS );
		GridPane.setHgrow( rawDatasetField, Priority.ALWAYS );

		final Button button = new Button( "Browse" );
		button.setMinWidth( Region.USE_PREF_SIZE );
		button.setOnAction( event -> {
			final FileChooser fileChooser = new FileChooser();
			fileChooser.getExtensionFilters().addAll(
					new ExtensionFilter( "HDF5 Files", "*.hdf" ) );
			File file = fileChooser.showOpenDialog( grid.getScene().getWindow() );
			Optional.ofNullable( file ).map( File::getAbsolutePath ).ifPresent( hdf5::set );
		} );
		grid.add( button, 2, 0 );
		return grid;
	}

	@Override
	public ObjectProperty< String > errorMessage()
	{
		return error;
	}

	@Override
	public < T extends RealType< T > & NativeType< T >, V extends RealType< V > > Optional< DataSource< T, V > > getRaw( final String name, final SharedQueue sharedQueue, final int priority ) throws IOException
	{
		final String rawFile = hdf5.get();
		final String rawDataset = this.datasetRaw.get();

		final int[] cellSize = { 1024, 1024, 1 };
		final double[] resolution = { 4, 4, 40 };

		return Optional.empty();
	}

	@Override
	public Optional< DataSource< ?, ? > > getLabels( final String name )
	{
		// TODO
		return Optional.empty();
	}

}
