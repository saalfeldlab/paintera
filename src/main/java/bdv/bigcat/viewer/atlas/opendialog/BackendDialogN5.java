package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import com.google.gson.JsonElement;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSourceFromDelegates;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import gnu.trove.map.hash.TLongLongHashMap;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.util.Util;

public class BackendDialogN5 implements BackendDialog, CombinesErrorMessages
{

	private static final String RESOLUTION_KEY = "resolution";

	private static final String OFFSET_KEY = "offset";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	private final SimpleObjectProperty< String > n5 = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > dataset = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > n5error = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< Effect > n5errorEffect = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > datasetError = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > error = new SimpleObjectProperty<>();

	private final SimpleDoubleProperty resX = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty resY = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty resZ = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offX = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offY = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty offZ = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty min = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty max = new SimpleDoubleProperty( Double.NaN );

	private final ExecutorService singleThreadExecutorService = Executors.newFixedThreadPool( 1 );

	private final ArrayList< Future< Void > > directoryTraversalTasks = new ArrayList<>();

	private final SimpleBooleanProperty isTraversingDirectories = new SimpleBooleanProperty();

	private final Effect textFieldNoErrorEffect = new TextField().getEffect();

	private final Effect textFieldErrorEffect = new InnerShadow( 10, Color.ORANGE );

	private final ObservableList< String > datasetChoices = FXCollections.observableArrayList();
	{
		n5.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null && new File( newv ).exists() )
			{
				this.n5error.set( null );
				synchronized ( directoryTraversalTasks )
				{
					directoryTraversalTasks.forEach( f -> f.cancel( true ) );
					directoryTraversalTasks.add( singleThreadExecutorService.submit( () -> {
						final List< File > files = new ArrayList<>();
						this.isTraversingDirectories.set( true );
						findSubdirectories( new File( newv ), dir -> new File( dir, "attributes.json" ).exists(), files::add );
						this.isTraversingDirectories.set( false );
//						if ( datasetChoices.size() == 0 )
//							datasetChoices.add( "" );
						final URI baseURI = new File( newv ).toURI();
						if ( !Thread.currentThread().isInterrupted() )
						{
							InvokeOnJavaFXApplicationThread.invoke( () -> datasetChoices.setAll( files.stream().map( File::toURI ).map( baseURI::relativize ).map( URI::getPath ).collect( Collectors.toList() ) ) );
							if ( !oldv.equals( newv ) )
								InvokeOnJavaFXApplicationThread.invoke( () -> this.dataset.set( null ) );
						}
						return null;
					} ) );
				}
			}
			else
			{
				datasetChoices.clear();
				this.n5error.set( "No valid path for n5 root." );
			}
		} );
		dataset.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null && newv.length() > 0 )
			{
				datasetError.set( null );
				try
				{
					final HashMap< String, JsonElement > attributes = new N5FSReader( n5.get() ).getAttributes( newv );
					final double[] resolution = attributes.containsKey( RESOLUTION_KEY ) ? IntStream.range( 0, 3 ).mapToDouble( i -> attributes.get( RESOLUTION_KEY ).getAsJsonArray().get( i ).getAsDouble() ).toArray() : DoubleStream.generate( () -> Double.NaN ).limit( 3 ).toArray();
					resX.set( resolution[ 0 ] );
					resY.set( resolution[ 1 ] );
					resZ.set( resolution[ 2 ] );

					final double[] offset = attributes.containsKey( OFFSET_KEY ) ? IntStream.range( 0, 3 ).mapToDouble( i -> attributes.get( OFFSET_KEY ).getAsJsonArray().get( i ).getAsDouble() ).toArray() : DoubleStream.generate( () -> Double.NaN ).limit( 3 ).toArray();
					offX.set( offset[ 0 ] );
					offY.set( offset[ 1 ] );
					offZ.set( offset[ 2 ] );

					min.set( attributes.containsKey( MIN_KEY ) ? attributes.get( MIN_KEY ).getAsDouble() : Double.NaN );
					max.set( attributes.containsKey( MAX_KEY ) ? attributes.get( MAX_KEY ).getAsDouble() : Double.NaN );

				}
				catch ( final IOException e )
				{

				}

			}
			else if ( Optional.of( n5.get() ).orElse( "" ).length() > 0 )
				datasetError.set( "No n5 dataset selected" );
		} );
		datasetChoices.addListener( ( ListChangeListener< String > ) change -> {
			while ( change.next() )
				if ( datasetChoices.size() == 0 )
					datasetError.set( "No datasets found for n5 root: " + n5.get() );
				else
					datasetError.set( null );
		} );

		n5error.addListener( ( obs, oldv, newv ) -> combineErrorMessages() );
		n5error.addListener( ( obs, oldv, newv ) -> this.n5errorEffect.set( newv != null && newv.length() > 0 ? textFieldErrorEffect : textFieldNoErrorEffect ) );
		datasetError.addListener( ( obs, oldv, newv ) -> combineErrorMessages() );

		n5.set( "" );
		dataset.set( "" );
	}

	@Override
	public Node getDialogNode()
	{
		final TextField n5Field = new TextField( n5.get() );
		n5Field.setMinWidth( 0 );
		n5Field.setMaxWidth( Double.POSITIVE_INFINITY );
		n5Field.setPromptText( "n5 group" );
		n5Field.textProperty().bindBidirectional( n5 );
		final ComboBox< String > datasetDropDown = new ComboBox<>( datasetChoices );
		datasetDropDown.setPromptText( "Choose Dataset..." );
		datasetDropDown.setEditable( false );
		datasetDropDown.valueProperty().bindBidirectional( dataset );
		datasetDropDown.setMinWidth( n5Field.getMinWidth() );
		datasetDropDown.setPrefWidth( n5Field.getPrefWidth() );
		datasetDropDown.setMaxWidth( n5Field.getMaxWidth() );
		datasetDropDown.disableProperty().bind( this.isTraversingDirectories );
		final GridPane grid = new GridPane();
		grid.add( n5Field, 0, 0 );
		grid.add( datasetDropDown, 0, 1 );
		GridPane.setHgrow( n5Field, Priority.ALWAYS );
		GridPane.setHgrow( datasetDropDown, Priority.ALWAYS );
		final Button button = new Button( "Browse" );
		button.setOnAction( event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File initDir = new File( n5.get() );
			directoryChooser.setInitialDirectory( initDir.exists() && initDir.isDirectory() ? initDir : FileSystems.getDefault().getPath( "." ).toFile() );
			final File directory = directoryChooser.showDialog( grid.getScene().getWindow() );
			Optional.ofNullable( directory ).map( File::getAbsolutePath ).ifPresent( n5::set );
		} );
		grid.add( button, 1, 0 );

		this.n5errorEffect.addListener( ( obs, oldv, newv ) -> {
			if ( !n5Field.isFocused() )
				n5Field.setEffect( newv );
		} );

		n5Field.setEffect( this.n5errorEffect.get() );

		n5Field.focusedProperty().addListener( ( obs, oldv, newv ) -> {
			if ( newv )
				n5Field.setEffect( this.textFieldNoErrorEffect );
			else
				n5Field.setEffect( n5errorEffect.get() );
		} );

		return grid;
	}

	@Override
	public ObjectProperty< String > errorMessage()
	{
		return error;
	}

	public static void findSubdirectories( final File file, final Predicate< File > check, final Consumer< File > action )
	{
		if ( !Thread.currentThread().isInterrupted() )
			if ( check.test( file ) )
				action.accept( file );
			else if ( file.exists() )
				// TODO come up with better filter than File::canWrite
				Optional.ofNullable( file.listFiles() ).ifPresent( files -> Arrays.stream( files ).filter( File::isDirectory ).filter( File::canRead ).forEach( f -> findSubdirectories( f, check, action ) ) );
	}

	@Override
	public < T extends RealType< T > & NativeType< T >, V extends RealType< V > > Optional< DataSource< T, V > > getRaw(
			final String name,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = n5.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();

		return Optional.of( DataSource.createN5RawSource( name, reader, dataset, resolution, offset, sharedQueue, priority ) );
	}

	@Override
	public Optional< LabelDataSource< ?, ? > > getLabels(
			final String name,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = n5.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();
//		final DataSource< ?, ? > source = DataSource.createN5RawSource( name, reader, dataset, new double[] { 1, 1, 1 }, sharedQueue, priority );
		final DataType type = reader.getDatasetAttributes( dataset ).getDataType();
		if ( isLabelType( type ) )
		{
			if ( isIntegerType( type ) )
				return Optional.of( ( LabelDataSource< ?, ? > ) getIntegerTypeSource( name, reader, dataset, resolution, offset, sharedQueue, priority ) );
			else if ( isLabelMultisetType( type ) )
				return Optional.empty();
			else
				return Optional.empty();
		}
		else
			return Optional.empty();
	}

	private static final < T extends IntegerType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > > LabelDataSource< T, V > getIntegerTypeSource(
			final String name,
			final N5FSReader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
		final T t = Util.getTypeFromInterval( raw );
		@SuppressWarnings( "unchecked" )
		final V v = ( V ) VolatileTypeMatcher.getVolatileTypeForType( t );

		final AffineTransform3D rawTransform = new AffineTransform3D();
		rawTransform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleIntervalDataSource< T, V > source =
				new RandomAccessibleIntervalDataSource< T, V >(
						new RandomAccessibleInterval[] { raw },
						new RandomAccessibleInterval[] { VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) ) },
						new AffineTransform3D[] { rawTransform },
						interpolation -> new NearestNeighborInterpolatorFactory<>(),
						interpolation -> new NearestNeighborInterpolatorFactory<>(),
						t::createVariable,
						v::createVariable,
						name );
		final FragmentSegmentAssignmentWithHistory frag = new FragmentSegmentAssignmentWithHistory( new TLongLongHashMap(), action -> {}, () -> {
			try
			{
				Thread.sleep( 1000 );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}
			return null;
		} );
		final LabelDataSourceFromDelegates< T, V > delegated = new LabelDataSourceFromDelegates<>( source, frag );
		return delegated;
	}

	private static boolean isLabelType( final DataType type )
	{
		return isLabelMultisetType( type ) || isIntegerType( type );
	}

	private static boolean isLabelMultisetType( final DataType type )
	{
		return false;
	}

	private static boolean isIntegerType( final DataType type )
	{
		switch ( type )
		{
		case INT8:
		case INT16:
		case INT32:
		case INT64:
		case UINT8:
		case UINT16:
		case UINT32:
		case UINT64:
			return true;
		default:
			return false;
		}
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

	@Override
	public Collection< ObservableValue< String > > errorMessages()
	{
		return Arrays.asList( this.n5error, this.datasetError );
	}

	@Override
	public Consumer< Collection< String > > combiner()
	{
		return strings -> this.error.set( String.join( "\n", strings ) );
	}

}
