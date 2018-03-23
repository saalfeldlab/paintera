package bdv.bigcat.viewer.atlas.ui.source.mesh;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.controlsfx.control.CheckListView;

import bdv.bigcat.viewer.meshes.MeshExporter;
import bdv.bigcat.viewer.meshes.MeshExporterBinary;
import bdv.bigcat.viewer.meshes.MeshExporterObj;
import bdv.bigcat.viewer.meshes.MeshInfo;
import bdv.bigcat.viewer.meshes.MeshInfos;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public class MeshExporterDialog extends Dialog< ExportResult >
{

	public static enum FILETYPE
	{
		obj, binary
	};

	final int LIST_CELL_HEIGHT = 25;

	private final TextField scale;

	private final TextField filePath;

	private final String[] filePaths;

	private MeshExporter meshExporter;

	private long[] segmentIds;

	private ComboBox< String > fileFormats;

	private CheckListView< String > checkListView;

	private final BooleanBinding isError;

	public MeshExporterDialog( final MeshInfo meshInfo )
	{
		super();
		this.segmentIds = new long[] { meshInfo.segmentId() };
		this.filePath = new TextField();
		this.filePaths = new String[] { "" };
		this.setTitle( "Export mesh " + this.segmentIds[ 0 ] );
		this.isError = ( Bindings.createBooleanBinding( () -> filePath.getText().isEmpty(), filePath.textProperty() ) );
		scale = new TextField( Integer.toString( meshInfo.scaleLevelProperty().get() ) );

		setNumericTextField( scale, meshInfo.numScaleLevels() - 1 );
		setResultConverter( button -> {
			if ( button.getButtonData().isCancelButton() )
				return null;
			return new ExportResult( meshExporter, segmentIds, Integer.parseInt( scale.getText() ), filePaths );
		} );

		createDialog();

	}

	public MeshExporterDialog( final MeshInfos meshInfos )
	{
		super();
		ObservableList< MeshInfo > meshInfoList = meshInfos.readOnlyInfos();
		this.filePath = new TextField();
		this.setTitle( "Export mesh " );
		this.segmentIds = new long[ meshInfoList.size() ];
		this.filePaths = new String[ meshInfoList.size() ];
		this.checkListView = new CheckListView<>();
		this.isError = ( Bindings.createBooleanBinding( () -> filePath.getText().isEmpty() || checkListView.getItems().isEmpty(), filePath.textProperty(),
				checkListView.itemsProperty() ) );

		int minCommonScaleLevels = Integer.MAX_VALUE;
		int minCommonScale = Integer.MAX_VALUE;
		final ObservableList< String > ids = FXCollections.observableArrayList();
		for ( int i = 0; i < meshInfoList.size(); i++ )
		{
			MeshInfo info = meshInfoList.get( i );
			this.segmentIds[ i ] = info.segmentId();
			ids.add( Long.toString( info.segmentId() ) );

			if ( minCommonScaleLevels > info.numScaleLevels() )
				minCommonScaleLevels = info.numScaleLevels();

			if ( minCommonScale > info.scaleLevelProperty().get() )
				minCommonScale = info.scaleLevelProperty().get();
		}

		scale = new TextField( Integer.toString( minCommonScale ) );
		setNumericTextField( scale, minCommonScaleLevels - 1 );

		setResultConverter( button -> {
			if ( button.getButtonData().isCancelButton() )
				return null;
			return new ExportResult( meshExporter, segmentIds, Integer.parseInt( scale.getText() ), filePaths );
		} );

		createMultiIdsDialog( ids );
	}

	private void createDialog()
	{
		final VBox vbox = new VBox();
		final GridPane contents = new GridPane();

		int row = createCommonDialog( contents );

		final Button button = new Button( "Browse" );
		button.setOnAction( event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File directory = directoryChooser.showDialog( contents.getScene().getWindow() );
			Optional.ofNullable( directory ).map( File::getAbsolutePath );
			filePath.setText( directory.getPath() );

			String extension = getExtension( fileFormats );

			for ( int i = 0; i < segmentIds.length; i++ )
			{
				filePaths[ i ] = directory + "/neuron" + segmentIds[ i ] + extension;
			}

			createMeshExporter( extension );
		} );

		contents.add( button, 2, row );
		++row;

		vbox.getChildren().add( contents );
		this.getDialogPane().setContent( vbox );
	}

	private void createMultiIdsDialog( ObservableList< String > ids )
	{
		final VBox vbox = new VBox();
		final GridPane contents = new GridPane();
		int row = createCommonDialog( contents );

		checkListView.setItems( ids );
		checkListView.prefHeightProperty().bind( Bindings.size( checkListView.itemsProperty().get() ).multiply( LIST_CELL_HEIGHT ) );

		final Button button = new Button( "Browse" );
		button.setOnAction( event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File directory = directoryChooser.showDialog( contents.getScene().getWindow() );
			Optional.ofNullable( directory ).map( File::getAbsolutePath );
			filePath.setText( directory.getPath() );

			// recover selected ids
			final List< Long > selectedIds = new ArrayList<>();

			if ( checkListView.getItems().size() == 0 )
				return;

			for ( int i = 0; i < checkListView.getItems().size(); i++ )
			{
				if ( checkListView.getItemBooleanProperty( i ).get() == true )
				{
					selectedIds.add( Long.parseLong( checkListView.getItems().get( i ) ) );
				}
			}

			segmentIds = selectedIds.stream().mapToLong( l -> l ).toArray();

			String extension = getExtension( fileFormats );

			for ( int i = 0; i < selectedIds.size(); i++ )
			{
				filePaths[ i ] = directory + "/neuron" + selectedIds.get( i ) + extension;
			}

			createMeshExporter( extension );
		} );

		contents.add( button, 2, row );
		++row;

		vbox.getChildren().add( checkListView );
		vbox.getChildren().add( contents );
		this.getDialogPane().setContent( vbox );
	}

	private int createCommonDialog( GridPane contents )
	{
		int row = 0;

		contents.add( new Label( "Scale" ), 0, row );
		contents.add( scale, 1, row );
		GridPane.setFillWidth( scale, true );
		++row;

		contents.add( new Label( "Format" ), 0, row );

		List< String > typeNames = Stream.of( FILETYPE.values() ).map( FILETYPE::name ).collect( Collectors.toList() );
		ObservableList< String > options = FXCollections.observableArrayList( typeNames );
		fileFormats = new ComboBox<>( options );
		fileFormats.getSelectionModel().select( 0 );
		fileFormats.setMinWidth( 0 );
		fileFormats.setMaxWidth( Double.POSITIVE_INFINITY );
		contents.add( fileFormats, 1, row );
		fileFormats.maxWidth( 300 );
		GridPane.setFillWidth( fileFormats, true );
		GridPane.setHgrow( fileFormats, Priority.ALWAYS );

		++row;

		contents.add( new Label( "Save to:" ), 0, row );
		contents.add( filePath, 1, row );

		this.getDialogPane().getButtonTypes().addAll( ButtonType.CANCEL, ButtonType.OK );
		this.getDialogPane().lookupButton( ButtonType.OK ).disableProperty().bind( this.isError );

		return row;
	}

	private String getExtension( ComboBox< String > fileFormats )
	{
		if ( fileFormats.getSelectionModel().getSelectedIndex() == 0 ) { return ".obj"; }

		return ".bin";
	}

	private void createMeshExporter( String extension )
	{
		switch ( extension )
		{
		case ".bin":
			meshExporter = new MeshExporterBinary();
			break;
		case ".obj":
		default:
			meshExporter = new MeshExporterObj();
			break;
		}
	}

	private void setNumericTextField( final TextField textField, final int max )
	{
		// force the field to be numeric only
		textField.textProperty().addListener( new ChangeListener< String >()
		{
			@Override
			public void changed( ObservableValue< ? extends String > observable, String oldValue,
					String newValue )
			{
				if ( !newValue.matches( "\\d*" ) )
				{
					textField.setText( newValue.replaceAll( "[^\\d]", "" ) );
				}

				if ( Integer.parseInt( textField.getText() ) > max )
				{
					textField.setText( Integer.toString( max ) );
				}
			}
		} );
	}
}
