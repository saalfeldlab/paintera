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

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import com.google.gson.JsonElement;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSourceFromDelegates;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentOnlyLocal;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
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
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class BackendDialogN5 implements BackendDialog, CombinesErrorMessages
{

	private static final String RESOLUTION_KEY = "resolution";

	private static final String OFFSET_KEY = "offset";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	private static final String AXIS_ORDER_KEY = "axisOrder";

	private final SimpleObjectProperty< String > n5 = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > dataset = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > n5error = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< Effect > n5errorEffect = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > datasetError = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > error = new SimpleObjectProperty<>();

	private final DatasetInfo datasetInfo = new DatasetInfo();

	private final ExecutorService singleThreadExecutorService = Executors.newFixedThreadPool( 1 );

	private final ArrayList< Future< Void > > directoryTraversalTasks = new ArrayList<>();

	private final SimpleBooleanProperty isTraversingDirectories = new SimpleBooleanProperty();

	private final BooleanBinding isValidN5 = Bindings.createBooleanBinding( () -> Optional.ofNullable( n5error.get() ).orElse( "" ).length() == 0, n5error );

	private final StringBinding traversalMessage =
			Bindings.createStringBinding( () -> isTraversingDirectories.get() ? "Discovering datasets" : "", isTraversingDirectories );

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
						findSubdirectories( new File( newv ), dir -> new File( dir, "attributes.json" ).exists(), files::add, () -> this.isTraversingDirectories.set( false ) );
						this.isTraversingDirectories.set( false );
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
					final N5FSReader reader = new N5FSReader( n5.get() );

					final DatasetAttributes dsAttrs = reader.getDatasetAttributes( newv );
					final int nDim = dsAttrs.getNumDimensions();

					final HashMap< String, JsonElement > attributes = reader.getAttributes( newv );

					if ( attributes.containsKey( AXIS_ORDER_KEY ) )
					{
						final AxisOrder ao = reader.getAttribute( newv, AXIS_ORDER_KEY, AxisOrder.class );
						this.datasetInfo.defaultAxisOrderProperty().set( ao );
						this.datasetInfo.selectedAxisOrderProperty().set( ao );
					}
					else
					{
						final Optional< AxisOrder > ao = AxisOrder.defaultOrder( nDim );
						if ( ao.isPresent() )
							this.datasetInfo.defaultAxisOrderProperty().set( ao.get() );
						if ( this.datasetInfo.selectedAxisOrderProperty().isNull().get() || this.datasetInfo.selectedAxisOrderProperty().get().numDimensions() != nDim )
							this.axisOrder().set( ao.get() );
					}

					Optional.ofNullable( reader.getAttribute( newv, RESOLUTION_KEY, double[].class ) ).ifPresent( r -> this.datasetInfo.setResolution( r ) );
					Optional.ofNullable( reader.getAttribute( newv, OFFSET_KEY, double[].class ) ).ifPresent( o -> this.datasetInfo.setOffset( o ) );
					Optional.ofNullable( reader.getAttribute( newv, MIN_KEY, Double.class ) ).ifPresent( m -> this.datasetInfo.minProperty().set( m ) );
					Optional.ofNullable( reader.getAttribute( newv, MAX_KEY, Double.class ) ).ifPresent( m -> this.datasetInfo.maxProperty().set( m ) );

				}
				catch ( final IOException e )
				{

				}

			}
			else
				datasetError.set( "No n5 dataset selected" );
		} );

		n5error.addListener( ( obs, oldv, newv ) -> this.n5errorEffect.set( newv != null && newv.length() > 0 ? textFieldErrorEffect : textFieldNoErrorEffect ) );

		this.isValidN5.addListener( ( obs, oldv, newv ) -> {
			synchronized ( directoryTraversalTasks )
			{
				directoryTraversalTasks.forEach( task -> task.cancel( !newv ) );
				directoryTraversalTasks.clear();
			}
		} );

		this.errorMessages().forEach( em -> em.addListener( ( obs, oldv, newv ) -> combineErrorMessages() ) );

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
		datasetDropDown.disableProperty().bind( this.isTraversingDirectories.or( this.isValidN5.not() ) );
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

	public static void findSubdirectories( final File file, final Predicate< File > check, final Consumer< File > action, final Runnable onInterruption )
	{
		if ( !Thread.currentThread().isInterrupted() )
		{
			if ( check.test( file ) )
				action.accept( file );
			else if ( file.exists() )
				// TODO come up with better filter than File::canWrite
				Optional.ofNullable( file.listFiles() ).ifPresent( files -> Arrays.stream( files ).filter( File::isDirectory ).filter( File::canRead ).forEach( f -> findSubdirectories( f, check, action, onInterruption ) ) );
		}
		else
			onInterruption.run();
	}

	@Override
	public < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > & NativeType< V > > Collection< DataSource< T, V > > getRaw(
			final String name,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = n5.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();
		final DatasetAttributes attributes = reader.getDatasetAttributes( dataset );
		final long[] dimensions = attributes.getDimensions();

		if ( axisOrder.hasChannels() )
		{
			final int channelAxis = axisOrder.channelAxis();
			final long numChannels = dimensions[ channelAxis ];
			final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
			final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
			final int[] componentMapping = axisOrder.spatialOnly().inversePermutation();
			final AffineTransform3D rawTransform = permutedSourceTransform( resolution, offset, componentMapping );
			final T t = Util.getTypeFromInterval( raw );
			@SuppressWarnings( "unchecked" )
			final V v = ( V ) VolatileTypeMatcher.getVolatileTypeForType( t );

			final ArrayList< DataSource< T, V > > sources = new ArrayList<>();

			for ( long pos = 0; pos < numChannels; ++pos )
			{
				final IntervalView< T > rawHS = Views.hyperSlice( raw, channelAxis, pos );
				final IntervalView< V > vrawHS = Views.hyperSlice( vraw, channelAxis, pos );
				final RandomAccessibleIntervalDataSource< T, V > source =
						new RandomAccessibleIntervalDataSource< T, V >(
								new RandomAccessibleInterval[] { rawHS },
								new RandomAccessibleInterval[] { vrawHS },
								new AffineTransform3D[] { rawTransform },
								interpolation -> new NearestNeighborInterpolatorFactory<>(),
								interpolation -> new NearestNeighborInterpolatorFactory<>(),
								t::createVariable,
								v::createVariable,
								name + " (" + pos + ")" );
				sources.add( source );
			}
			return sources;
		}

		final int[] componentMapping = axisOrder.spatialOnly().inversePermutation();
		final AffineTransform3D rawTransform = permutedSourceTransform( resolution, offset, componentMapping );
		return Arrays.asList( DataSource.createN5RawSource( name, reader, dataset, rawTransform, sharedQueue, priority ) );
	}

	@Override
	public Collection< LabelDataSource< ?, ? > > getLabels(
			final String name,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
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
				return Arrays.asList( ( LabelDataSource< ?, ? > ) getIntegerTypeSource( name, reader, dataset, resolution, offset, axisOrder, sharedQueue, priority ) );
			else if ( isLabelMultisetType( type ) )
				return new ArrayList<>();
			else
				return new ArrayList<>();
		}
		else
			return new ArrayList<>();
	}

	private static final < T extends IntegerType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > > LabelDataSource< T, V > getIntegerTypeSource(
			final String name,
			final N5FSReader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
		final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
		final T t = Util.getTypeFromInterval( raw );
		@SuppressWarnings( "unchecked" )
		final V v = ( V ) VolatileTypeMatcher.getVolatileTypeForType( t );

		final int[] componentMapping = axisOrder.spatialOnly().inversePermutation();
		final AffineTransform3D rawTransform = permutedSourceTransform( resolution, offset, componentMapping );

		// TODO permute axis or do it in the raw transform?
//		final DatasetAttributes datasetAttributes = reader.getDatasetAttributes( dataset );
//		final long[] dimensions = new long[ datasetAttributes.getNumDimensions() ];
//		for ( int d = 0; d < dimensions.length; ++d )
//			dimensions[ d ] = datasetAttributes.getDimensions()[ componentMapping[ d ] ];
//		final MixedTransform tf = new MixedTransform( 3, 3 );
//		tf.setComponentMapping( componentMapping );
//		final FinalInterval fi = new FinalInterval( dimensions );
//		System.out.println( Arrays.toString( componentMapping ) + " mapping " + Arrays.toString( dimensions ) + " " + Arrays.toString( datasetAttributes.getDimensions() ) + " " + isPermuted );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleIntervalDataSource< T, V > source =
				new RandomAccessibleIntervalDataSource< T, V >(
						new RandomAccessibleInterval[] { raw },
						new RandomAccessibleInterval[] { vraw },
						new AffineTransform3D[] { rawTransform },
						interpolation -> new NearestNeighborInterpolatorFactory<>(),
						interpolation -> new NearestNeighborInterpolatorFactory<>(),
						t::createVariable,
						v::createVariable,
						name );
		final FragmentSegmentAssignmentOnlyLocal assignment = new FragmentSegmentAssignmentOnlyLocal();
		final LabelDataSourceFromDelegates< T, V > delegated = new LabelDataSourceFromDelegates<>( source, assignment );
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
		return this.datasetInfo.spatialResolutionProperties()[ 0 ];
	}

	@Override
	public DoubleProperty resolutionY()
	{
		return this.datasetInfo.spatialResolutionProperties()[ 1 ];
	}

	@Override
	public DoubleProperty resolutionZ()
	{
		return this.datasetInfo.spatialResolutionProperties()[ 2 ];
	}

	@Override
	public DoubleProperty offsetX()
	{
		return this.datasetInfo.spatialOffsetProperties()[ 0 ];
	}

	@Override
	public DoubleProperty offsetY()
	{
		return this.datasetInfo.spatialOffsetProperties()[ 1 ];
	}

	@Override
	public DoubleProperty offsetZ()
	{
		return this.datasetInfo.spatialOffsetProperties()[ 2 ];
	}

	@Override
	public DoubleProperty min()
	{
		return this.datasetInfo.minProperty();
	}

	@Override
	public DoubleProperty max()
	{
		return this.datasetInfo.maxProperty();
	}

	@Override
	public Collection< ObservableValue< String > > errorMessages()
	{
		return Arrays.asList( this.n5error, this.traversalMessage, this.datasetError );
	}

	@Override
	public Consumer< Collection< String > > combiner()
	{
		return strings -> this.error.set( String.join( "\n", strings ) );
	}

	@Override
	public IntegerProperty numDimensions()
	{
		return this.numDimensions();
	}

	@Override
	public ObjectProperty< AxisOrder > axisOrder()
	{
		return this.datasetInfo.selectedAxisOrderProperty();
	}

	private static final AffineTransform3D permutedSourceTransform( final double[] resolution, final double[] offset, final int[] componentMapping )
	{
		final AffineTransform3D rawTransform = new AffineTransform3D();
		final double[] matrixContent = new double[ 12 ];
		for ( int i = 0, contentOffset = 0; i < offset.length; ++i, contentOffset += 4 )
		{
			matrixContent[ contentOffset + componentMapping[ i ] ] = resolution[ i ];
			matrixContent[ contentOffset + 3 ] = offset[ i ];
		}
		rawTransform.set( matrixContent );
		return rawTransform;
	}

	private final static class PermutedInfo
	{

		private final DoubleProperty resX = new SimpleDoubleProperty( Double.NaN );

		private final DoubleProperty resY = new SimpleDoubleProperty( Double.NaN );

		private final DoubleProperty resZ = new SimpleDoubleProperty( Double.NaN );

		DoubleProperty[] getInfo()
		{
			return new DoubleProperty[] { resX, resY, resZ };
		}

	}

}
