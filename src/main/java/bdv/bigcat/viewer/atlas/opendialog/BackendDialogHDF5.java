package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.util.volatiles.SharedQueue;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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

	private final SimpleObjectProperty< String > dataset = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > error = new SimpleObjectProperty<>();

	private final ObservableList< String > datasetChoices = FXCollections.observableArrayList();
	{
		dataset.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				error.set( null );
			else
				error.set( "No hdf5 dataset found at " + hdf5 + " " + dataset );
		} );

		datasetChoices.addListener( ( ListChangeListener< String > ) change -> {
			while ( change.next() )
				if ( datasetChoices.size() == 0 )
					error.set( "No datasets found for hdf5 root: " + hdf5.get() );
		} );

		hdf5.set( "" );
		dataset.set( "" );
		error.set( "" );
	}

	@Override
	public Node getDialogNode()
	{
		final TextField hdf5Field = new TextField( hdf5.get() );
		hdf5Field.setMinWidth( 0 );
		hdf5Field.setMaxWidth( Double.POSITIVE_INFINITY );
		hdf5Field.textProperty().bindBidirectional( hdf5 );

		final TextField datasetField = new TextField( dataset.get() );
		datasetField.setMinWidth( 0 );
		datasetField.setMaxWidth( Double.POSITIVE_INFINITY );
		datasetField.textProperty().bindBidirectional( dataset );

		final GridPane grid = new GridPane();
		grid.add( new Label( "hdf5" ), 0, 0 );
		grid.add( new Label( "data set" ), 0, 1 );
		grid.setMinWidth( Region.USE_PREF_SIZE );
		grid.add( hdf5Field, 1, 0 );
		grid.add( datasetField, 1, 1 );
		grid.setHgap( 10 );

		GridPane.setHgrow( hdf5Field, Priority.ALWAYS );
		GridPane.setHgrow( datasetField, Priority.ALWAYS );

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
		final String group = hdf5.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();

		N5Utils.open( reader, dataset );
		return Optional.of( DataSource.createN5RawSource( name, reader, dataset, new double[] { 1, 1, 1 }, sharedQueue, priority ) );
	}

	@Override
	public Optional< DataSource< ?, ? > > getLabels( final String name )
	{
		// TODO
		return Optional.empty();
	}

}
