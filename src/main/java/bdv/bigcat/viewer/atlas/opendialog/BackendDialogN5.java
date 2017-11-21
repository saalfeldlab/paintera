package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class BackendDialogN5 implements BackendDialog
{

	private final SimpleObjectProperty< String > n5 = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > dataset = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > error = new SimpleObjectProperty<>();

	private final ObservableList< String > datasetChoices = FXCollections.observableArrayList();
	{
		n5.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null && new File( newv ).exists() )
			{
				this.error.set( null );
				final List< File > files = new ArrayList<>();
				findSubdirectories( new File( newv ), dir -> new File( dir, "attributes.json" ).exists(), files::add );
				if ( datasetChoices.size() == 0 )
					datasetChoices.add( "" );
				final URI baseURI = new File( newv ).toURI();
				datasetChoices.setAll( files.stream().map( File::toURI ).map( baseURI::relativize ).map( URI::getPath ).collect( Collectors.toList() ) );
			}
			else
			{
				datasetChoices.clear();
				error.set( "No valid path for n5 root." );
			}
		} );
		dataset.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				error.set( null );
			else
				error.set( "No n5 dataset found at " + n5 + " " + dataset );
		} );
		datasetChoices.addListener( ( ListChangeListener< String > ) change -> {
			while ( change.next() )
				if ( datasetChoices.size() == 0 )
					error.set( "No datasets found for n5 root: " + n5.get() );
		} );
		n5.set( "" );
	}

	@Override
	public Node getDialogNode()
	{
		final TextField n5Field = new TextField( n5.get() );
		n5Field.setMinWidth( 0 );
		n5Field.setMaxWidth( Double.POSITIVE_INFINITY );
		final ComboBox< String > datasetDropDown = new ComboBox<>( datasetChoices );
		n5Field.textProperty().bindBidirectional( n5 );
		datasetDropDown.valueProperty().bindBidirectional( dataset );
		datasetDropDown.setMinWidth( n5Field.getMinWidth() );
		datasetDropDown.setPrefWidth( n5Field.getPrefWidth() );
		datasetDropDown.setMaxWidth( n5Field.getMaxWidth() );
		final GridPane grid = new GridPane();
		grid.add( new Label( "n5" ), 0, 0 );
		grid.add( new Label( "data set" ), 0, 1 );
		grid.add( n5Field, 1, 0 );
		grid.add( datasetDropDown, 1, 1 );
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
		grid.add( button, 2, 0 );
		return grid;
	}

	@Override
	public ObjectProperty< String > errorMessage()
	{
		return error;
	}

	public static void findSubdirectories( final File file, final Predicate< File > check, final Consumer< File > action )
	{
		if ( check.test( file ) )
			action.accept( file );
		else
			Arrays.stream( file.listFiles() ).filter( File::isDirectory ).forEach( f -> findSubdirectories( f, check, action ) );
	}

	@Override
	public Optional< DataSource< ? extends RealType< ? >, ? extends RealType< ? > > > getRaw( final String name ) throws IOException
	{
		final String group = n5.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();
		final DatasetAttributes attributes = reader.getDatasetAttributes( dataset );
		final DataType type = attributes.getDataType();

		final Supplier< RealType > t = getType( type );
		final Supplier< AbstractVolatileRealType > v = getVolatileType( type );

		if ( t != null && v != null )
		{
			final RandomAccessibleInterval< ? >[] rai = { N5Utils.openVolatile( reader, dataset ) };
			final RandomAccessibleInterval< ? >[] volatileRai = { VolatileViews.wrapAsVolatile( rai[ 0 ] ) };
			final AffineTransform3D[] mipmapTransforms = { new AffineTransform3D() };
			final Function< Interpolation, InterpolatorFactory > tInterpolation = method -> method.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory() : new NearestNeighborInterpolatorFactory();
			final Function< Interpolation, InterpolatorFactory > vInterpolation = method -> method.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory() : new NearestNeighborInterpolatorFactory();
			return Optional.of( new RandomAccessibleIntervalDataSource( rai, volatileRai, mipmapTransforms, tInterpolation, vInterpolation, t, v, name ) );
		}
//		 VolatileViews.wrapAsVolatile( mipmap, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, i, true ) ) );
//		DataSource.createN5MipmapRawSource( name, n5, group, resolution, sharedQueue, typeSupplier, volatileTypeSupplier )
		return Optional.empty();
	}

	@Override
	public Optional< DataSource< ?, ? > > getLabels( final String name )
	{
		// TODO
		return Optional.empty();
	}

	private static < T extends RealType< T > > Supplier< T > getType( final DataType t )
	{
		switch ( t )
		{
		case UINT8:
			return () -> ( T ) new UnsignedByteType();
		default:
			return null;
		}
	}

	private static < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > > Supplier< V > getVolatileType( final DataType t )
	{
		switch ( t )
		{
		case UINT8:
			return () -> ( V ) new VolatileUnsignedByteType();
		default:
			return null;
		}
	}

	private static < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileNativeRealType< T, V > >
			Optional< Pair< Supplier< T >, Supplier< V > > > getTypes( final DataType t )
	{
		switch ( t )
		{
		case UINT8:
			return Optional.of( new ValuePair<>( ( Supplier< T > ) () -> ( T ) new UnsignedByteType(), ( Supplier< V > ) () -> ( V ) new VolatileUnsignedByteType() ) );
		default:
			return Optional.empty();
		}
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > getFromLoader( final N5Reader reader, final String dataset, final T t ) throws IOException
	{
		return N5Utils.openVolatile( reader, dataset );
	}

}
