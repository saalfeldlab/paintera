package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetDataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5Utils;
import bdv.util.volatiles.SharedQueue;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

	private final SimpleDoubleProperty resX = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty resY = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty resZ = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offX = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offY = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offZ = new SimpleDoubleProperty( Double.NaN );

	private final ObservableList< String > datasetChoices = FXCollections.observableArrayList();
	{
		hdf5.set( "" );
		dataset.set( "" );
		error.set( "" );

		hdf5.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null && new File( newv ).exists() )
			{
				this.error.set( null );
				IHDF5Reader reader = HDF5Factory.openForReading( newv );
				List< String > paths = new ArrayList< String >();
				H5Utils.getAllDatasets( reader, "/", paths );

				if ( datasetChoices.size() == 0 )
					datasetChoices.add( "" );

				datasetChoices.setAll( paths.stream().collect( Collectors.toList() ) );
				if ( !oldv.equals( newv ) )
					this.dataset.set( null );
			}
			else
			{
				datasetChoices.clear();
				error.set( "No valid sources for hdf5 file." );
			}
		} );

		datasetChoices.addListener( ( ListChangeListener< String > ) change -> {
			while ( change.next() )
				if ( datasetChoices.size() == 0 )
					error.set( "No datasets found for hdf file: " + hdf5.get() );
		} );

//		dataset.addListener( ( obs, oldv, newv ) -> {
//			if ( newv != null )
//				error.set( null );
//			else
//				error.set( "No dataset" );
//		} );
	}

	@Override
	public Node getDialogNode()
	{
		final TextField hdf5Field = new TextField( hdf5.get() );
		hdf5Field.setMinWidth( 0 );
		hdf5Field.setMaxWidth( Double.POSITIVE_INFINITY );
		hdf5Field.setPromptText( "hdf file" );
		hdf5Field.textProperty().bindBidirectional( hdf5 );

		final ComboBox< String > datasetDropDown = new ComboBox<>( datasetChoices );
		datasetDropDown.setPromptText( "Choose Dataset..." );
		datasetDropDown.setEditable( false );
		datasetDropDown.valueProperty().bindBidirectional( dataset );
		datasetDropDown.setMinWidth( hdf5Field.getMinWidth() );
		datasetDropDown.setPrefWidth( hdf5Field.getPrefWidth() );
		datasetDropDown.setMaxWidth( hdf5Field.getMaxWidth() );

		final GridPane grid = new GridPane();
		grid.add( hdf5Field, 0, 0 );
		grid.add( datasetDropDown, 0, 1 );
		GridPane.setHgrow( hdf5Field, Priority.ALWAYS );
		GridPane.setHgrow( datasetDropDown, Priority.ALWAYS );

		final Button button = new Button( "Browse" );
		button.setMinWidth( Region.USE_PREF_SIZE );
		button.setOnAction( event -> {
			final FileChooser fileChooser = new FileChooser();
			fileChooser.getExtensionFilters().addAll(
					new ExtensionFilter( "HDF5 Files", "*.hdf" ) );
			File file = fileChooser.showOpenDialog( grid.getScene().getWindow() );
			Optional.ofNullable( file ).map( File::getAbsolutePath ).ifPresent( hdf5::set );
		} );
		grid.add( button, 1, 0 );
		return grid;
	}

	@Override
	public ObjectProperty< String > errorMessage()
	{
		return error;
	}

	@Override
	public < T extends RealType< T > & NativeType< T >, V extends RealType< V > > Optional< DataSource< T, V > > getRaw(
			final String name,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String rawFile = hdf5.get();
		final String rawDataset = this.dataset.get();

		final int[] cellSize = { 192, 96, 7 };

		return Optional.of( DataSource.createH5RawSource( name, rawFile, rawDataset, cellSize, resolution, sharedQueue, priority ) );
	}

	@Override
	public Optional< LabelDataSource< ?, ? > > getLabels(
			final String name,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
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
