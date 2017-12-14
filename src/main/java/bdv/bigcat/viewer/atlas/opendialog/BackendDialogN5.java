package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import com.google.gson.JsonElement;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentOnlyLocal;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.effect.Effect;
import javafx.scene.effect.InnerShadow;
import javafx.scene.paint.Color;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.type.NativeType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class BackendDialogN5 implements SourceFromRAI, CombinesErrorMessages
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

	private final GroupAndDatasetStructure nodeCreator = new GroupAndDatasetStructure(
			"N5 group",
			"Choose Dataset...",
			n5,
			dataset,
			datasetChoices,
			this.isTraversingDirectories.or( this.isValidN5.not() ) );
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

					this.datasetInfo.setResolution( Optional.ofNullable( reader.getAttribute( newv, RESOLUTION_KEY, double[].class ) ).orElse( DoubleStream.generate( () -> 1.0 ).limit( nDim ).toArray() ) );
					this.datasetInfo.setOffset( Optional.ofNullable( reader.getAttribute( newv, OFFSET_KEY, double[].class ) ).orElse( new double[ nDim ] ) );
					this.datasetInfo.minProperty().set( Optional.ofNullable( reader.getAttribute( newv, MIN_KEY, Double.class ) ).orElse( minForType( dsAttrs.getDataType() ) ) );
					this.datasetInfo.maxProperty().set( Optional.ofNullable( reader.getAttribute( newv, MAX_KEY, Double.class ) ).orElse( maxForType( dsAttrs.getDataType() ) ) );

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
		return nodeCreator.createNode();
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

	private static double minForType( final DataType t )
	{
		// TODO ever return non-zero here?
		switch ( t )
		{
		default:
			return 0.0;
		}
	}

	private static double maxForType( final DataType t )
	{
		switch ( t )
		{
		case UINT8:
			return 0xff;
		case UINT16:
			return 0xffff;
		case UINT32:
			return 0xffffffffl;
		case UINT64:
			return 2.0 * Long.MAX_VALUE;
		case INT8:
			return Byte.MAX_VALUE;
		case INT16:
			return Short.MAX_VALUE;
		case INT32:
			return Integer.MAX_VALUE;
		case INT64:
			return Long.MAX_VALUE;
		case FLOAT32:
		case FLOAT64:
			return 1.0;
		default:
			return 1.0;
		}
	}

	@Override
	public < T extends NativeType< T >, V extends Volatile< T > > Pair< RandomAccessibleInterval< T >, RandomAccessibleInterval< V > > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = n5.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();
		final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
		final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
		return new ValuePair<>( raw, vraw );
	}

	@Override
	public boolean isLabelType() throws IOException
	{
		return isLabelType( new N5FSReader( n5.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public boolean isLabelMultisetType() throws IOException
	{
		return isLabelMultisetType( new N5FSReader( n5.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public boolean isIntegerType() throws IOException
	{
		return isIntegerType( new N5FSReader( n5.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public Iterator< ? extends FragmentSegmentAssignmentState< ? > > assignments()
	{
		return Stream.generate( FragmentSegmentAssignmentOnlyLocal::new ).iterator();
	}

}
