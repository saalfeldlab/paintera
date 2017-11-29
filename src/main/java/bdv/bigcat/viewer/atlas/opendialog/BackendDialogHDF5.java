package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetDataSource;
import bdv.img.cache.VolatileGlobalCellCache;
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

	private final SimpleObjectProperty< String > dataset = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > error = new SimpleObjectProperty<>();

	{
		dataset.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				error.set( null );
			else
				error.set( "No raw dataset" );
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

		final TextField rawDatasetField = new TextField( dataset.get() );
		rawDatasetField.setMinWidth( 0 );
		rawDatasetField.setMaxWidth( Double.POSITIVE_INFINITY );
		rawDatasetField.textProperty().bindBidirectional( dataset );

		final GridPane grid = new GridPane();
		grid.add( new Label( "hdf5" ), 0, 0 );
		grid.add( new Label( "data set path" ), 0, 1 );
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
		final String rawDataset = this.dataset.get();

		final int[] cellSize = { 192, 96, 7 };
		final double[] resolution = { 4, 4, 40 };

		return Optional.of( DataSource.createH5RawSource( name, rawFile, rawDataset, cellSize, resolution, sharedQueue, priority ) );
	}

	@Override
	public Optional< DataSource< ?, ? > > getLabels( final String name )
	{
		final String labelsFile = hdf5.get();
		final String labelsDataset = this.dataset.get();

		final int[] labelCellSize = { 79, 79, 4 };
		final VolatileGlobalCellCache cellCache = new VolatileGlobalCellCache( 1, 2 );

		HDF5LabelMultisetDataSource labelSpec2 = null;
		try
		{
			labelSpec2 = new HDF5LabelMultisetDataSource( labelsFile, labelsDataset, labelCellSize, "labels", cellCache, 1 );
		}
		catch ( IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return Optional.of( labelSpec2 );
	}

}
