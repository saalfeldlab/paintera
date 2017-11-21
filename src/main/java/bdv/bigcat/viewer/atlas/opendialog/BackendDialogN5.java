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
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

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
	public < T extends RealType< T > & NativeType< T >, V extends RealType< V > > Optional< DataSource< T, V > > getRaw( final String name, final SharedQueue sharedQueue, final int priority ) throws IOException
	{
		final String group = n5.get();
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
