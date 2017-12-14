package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.opendialog.OpenSourceDialog.TYPE;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentOnlyLocal;
import bdv.img.h5.H5Utils;
import bdv.util.volatiles.SharedQueue;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.effect.Effect;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class BackendDialogHDF5 implements BackendDialog, CombinesErrorMessages
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String RESOLUTION_KEY = "resolution";

	private static final String OFFSET_KEY = "offset";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	// path for the hdf file
	private final SimpleObjectProperty< String > hdf5 = new SimpleObjectProperty<>();

	// selected dataset
	private final SimpleObjectProperty< String > dataset = new SimpleObjectProperty<>();

	// error message for invalid data set
	private final SimpleObjectProperty< String > datasetError = new SimpleObjectProperty<>();

	// error message for invalid hdf5 selection
	private final SimpleObjectProperty< String > hdf5error = new SimpleObjectProperty<>();

	// combined error messages
	private final SimpleObjectProperty< String > errorMessage = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< Effect > hdf5errorEffect = new SimpleObjectProperty<>();

	private final Effect textFieldNoErrorEffect = new TextField().getEffect();

	// appearance of the text box when there is an error
	private final Effect textFieldErrorEffect = new InnerShadow( 10, Color.ORANGE );

	private final SimpleDoubleProperty resX = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty resY = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty resZ = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offX = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offY = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offZ = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty min = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty max = new SimpleDoubleProperty( Double.NaN );

	// all data sets inside the hdf file
	private final ObservableList< String > datasetChoices = FXCollections.observableArrayList();

	public BackendDialogHDF5()
	{
		hdf5.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null && new File( newv ).exists() )
			{
				this.hdf5error.set( null );
				final IHDF5Reader reader = HDF5Factory.openForReading( newv );
				final List< String > paths = new ArrayList<>();
				H5Utils.getAllDatasetPaths( reader, "/", paths );
				reader.close();

				if ( datasetChoices.size() == 0 )
					datasetChoices.add( "" );

				datasetChoices.setAll( paths.stream().collect( Collectors.toList() ) );
				if ( !oldv.equals( newv ) )
					this.dataset.set( null );
			}
			else
			{
				datasetChoices.clear();
				this.hdf5error.set( "No valid hdf5 file." );
			}
		} );

		dataset.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null && newv.length() > 0 )
			{
				datasetError.set( null );
				final IHDF5Reader reader = HDF5Factory.openForReading( hdf5.get() );

				if ( reader.object().hasAttribute( newv, RESOLUTION_KEY ) )
				{
					final double[] resolution = reader.float64().getArrayAttr( newv, RESOLUTION_KEY );
					if ( resolution.length == 3 )
					{
						resX.set( resolution[ 2 ] );
						resY.set( resolution[ 1 ] );
						resZ.set( resolution[ 0 ] );
					}
				}

				if ( reader.object().hasAttribute( newv, OFFSET_KEY ) )
				{
					final double[] offset = reader.float64().getArrayAttr( newv, OFFSET_KEY );
					if ( offset.length == 3 )
					{
						offX.set( offset[ 2 ] );
						offY.set( offset[ 1 ] );
						offZ.set( offset[ 0 ] );
					}
				}

				if ( reader.object().hasAttribute( newv, MIN_KEY ) )
					min.set( reader.object().hasAttribute( newv, MIN_KEY ) ? reader.float64().getAttr( newv, MIN_KEY ) : Double.NaN );
				if ( reader.object().hasAttribute( newv, MAX_KEY ) )
					max.set( reader.object().hasAttribute( newv, MAX_KEY ) ? reader.float64().getAttr( newv, MAX_KEY ) : Double.NaN );
			}
			else
				datasetError.set( "No hdf5 dataset selected" );
		} );

		hdf5error.addListener( ( obs, oldv, newv ) -> this.hdf5errorEffect.set( newv != null && newv.length() > 0 ? textFieldErrorEffect : textFieldNoErrorEffect ) );

		datasetChoices.addListener( ( ListChangeListener< String > ) change -> {
			while ( change.next() )
				if ( datasetChoices.size() == 0 )
					datasetError.set( "No datasets found for hdf file: " + hdf5.get() );
		} );

		this.errorMessages().forEach( em -> em.addListener( ( obs, oldv, newv ) -> combineErrorMessages() ) );

		hdf5.set( "" );
		dataset.set( "" );
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
			final File file = fileChooser.showOpenDialog( grid.getScene().getWindow() );
			Optional.ofNullable( file ).map( File::getAbsolutePath ).ifPresent( hdf5::set );
		} );
		grid.add( button, 1, 0 );

		this.hdf5errorEffect.addListener( ( obs, oldv, newv ) -> {
			if ( !hdf5Field.isFocused() )
				hdf5Field.setEffect( newv );
		} );

		hdf5Field.setEffect( this.hdf5errorEffect.get() );

		hdf5Field.focusedProperty().addListener( ( obs, oldv, newv ) -> {
			if ( newv )
				hdf5Field.setEffect( this.textFieldNoErrorEffect );
			else
				hdf5Field.setEffect( hdf5errorEffect.get() );
		} );
		return grid;
	}

	@Override
	public ObjectProperty< String > errorMessage()
	{
		return errorMessage;
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

		return Optional.of( DataSource.createH5RawSource( name, rawFile, rawDataset, getChunkSize( rawFile, rawDataset ), resolution, offset, sharedQueue, priority ) );
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
		final FragmentSegmentAssignmentOnlyLocal assignment = new FragmentSegmentAssignmentOnlyLocal();
		final int[] chunks = getChunkSize( labelsFile, labelsDataset );
		return Optional.of( LabelDataSource.createH5LabelSource( name, labelsFile, labelsDataset, chunks, resolution, offset, sharedQueue, priority, assignment ) );
	}

	@Override
	public void typeChanged( final TYPE type )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( hdf5.get() );
		final List< String > paths = new ArrayList<>();
		H5Utils.getAllDatasetPaths( reader, "/", paths );

		switch ( type )
		{
		case LABEL:
			updateLabelDataset( reader, paths );
			break;
		case RAW:
		default:
			updateRawDataset( paths );
			break;
		}

		reader.close();
	}

	private void updateRawDataset( final List< String > paths )
	{
		if ( datasetChoices.size() == 0 )
			datasetChoices.add( "" );

		datasetChoices.setAll( paths.stream().collect( Collectors.toList() ) );
	}

	private void updateLabelDataset( final IHDF5Reader reader, final List< String > paths )
	{
		if ( datasetChoices.size() == 0 )
			datasetChoices.add( "" );

		for ( int i = 0; i < paths.size(); i++ )
		{
			final Class< ? > dataType = reader.getDataSetInformation( paths.get( i ) ).getTypeInformation().tryGetJavaType();
			if ( !dataType.isAssignableFrom( int.class ) && !dataType.isAssignableFrom( long.class ) )
			{
				paths.remove( i );
				i--;
			}
		}

		datasetChoices.setAll( paths.stream().collect( Collectors.toList() ) );
	}

	@Override
	public DoubleProperty resolutionX()
	{
		return this.resX;
	}

	@Override
	public DoubleProperty resolutionY()
	{
		return this.resY;
	}

	@Override
	public DoubleProperty resolutionZ()
	{
		return this.resZ;
	}

	@Override
	public DoubleProperty offsetX()
	{
		return this.offX;
	}

	@Override
	public DoubleProperty offsetY()
	{
		return this.offY;
	}

	@Override
	public DoubleProperty offsetZ()
	{
		return this.offZ;
	}

	@Override
	public DoubleProperty min()
	{
		return this.min;
	}

	@Override
	public DoubleProperty max()
	{
		return this.max;
	}

	private static int[] getChunkSize( final String file, final String dataset )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( file );
		final HDF5DataSetInformation info = reader.getDataSetInformation( dataset );
		final Optional< int[] > chunksOptional = Optional.ofNullable( info.tryGetChunkSizes() );
		if ( chunksOptional.isPresent() )
			LOG.debug( "Found chunk size {} {} {}", chunksOptional.get()[ 0 ], chunksOptional.get()[ 1 ], chunksOptional.get()[ 2 ] );
		else
			LOG.warn( "No chunk size specified for {}/{} -- Using dataset dimensions instead.", file, dataset );
		final int[] chunks = chunksOptional.orElse( Arrays.stream( info.getDimensions() ).mapToInt( l -> ( int ) l ).toArray() );
		return IntStream.range( 0, chunks.length ).map( i -> chunks[ chunks.length - i - 1 ] ).toArray();
	}

	@Override
	public Collection< ObservableValue< String > > errorMessages()
	{
		return Arrays.asList( this.hdf5error, this.datasetError );
	}

	@Override
	public Consumer< Collection< String > > combiner()
	{
		return strings -> this.errorMessage.set( String.join( "\n", strings ) );
	}
}
