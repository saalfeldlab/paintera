package org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.fx.ui.ExceptionNode;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.sun.javafx.application.PlatformImpl;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Callback;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class GoogleCloudBrowseHandler
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private GoogleCloudOAuth oauth;

	private final ObservableList< Project > projects = FXCollections.observableArrayList();

	private final ObservableList< Bucket > buckets = FXCollections.observableArrayList();

	public Optional< Pair< Storage, String > > select( final Scene scene )
	{
		try
		{
			oauth = GoogleCloudClientBuilder.createOAuth( googleCloudClientSecretsFromFileDialog( scene ) );
		}
		catch ( final Exception e )
		{
			ExceptionNode.exceptionDialog( e ).show();
			return Optional.empty();
		}

		// query a list of user's projects first
		final ResourceManager resourceManager = GoogleCloudClientBuilder.createResourceManager( oauth );
		projects.clear();
		final Iterator< Project > projectIterator = resourceManager.list().iterateAll().iterator();
		if ( !projectIterator.hasNext() )
		{
			LOG.warn( "No Google Cloud projects found for {}.", oauth );
			return null;
		}
		while ( projectIterator.hasNext() )
			projects.add( projectIterator.next() );

		// add project names as list items

		final Dialog< String > dialog = new Dialog<>();
		dialog.setTitle( "Google Cloud" );
		final GridPane contents = new GridPane();

		final ComboBox< Project > projectChoices = new ComboBox<>( projects );
		projectChoices.setPromptText( "Project" );
		final Callback< ListView< Project >, ListCell< Project > > projectCellFactory = lv -> new ListCell< Project >()
		{
			@Override
			protected void updateItem( final Project item, final boolean empty )
			{
				super.updateItem( item, empty );
				setText( empty ? "" : item.getName() );
			}
		};
		projectChoices.setCellFactory( projectCellFactory );

		final ComboBox< Bucket > bucketChoices = new ComboBox<>( buckets );
		bucketChoices.setPromptText( "Bucket" );
		final Callback< ListView< Bucket >, ListCell< Bucket > > bucketCellFactory = lv -> new ListCell< Bucket >()
		{
			@Override
			protected void updateItem( final Bucket item, final boolean empty )
			{
				super.updateItem( item, empty );
				setText( empty ? "" : item.getName() );
			}
		};
		bucketChoices.setCellFactory( bucketCellFactory );

		contents.add( projectChoices, 1, 0 );
		contents.add( bucketChoices, 1, 1 );

		dialog.getDialogPane().setContent( contents );

		dialog.setResultConverter( bt -> {
			if ( ButtonType.OK.equals( bt ) )
				return bucketChoices.getValue().getName();
			return null;
		} );

		dialog.setResizable( true );

		bucketChoices.prefWidthProperty().bindBidirectional( projectChoices.prefWidthProperty() );

		final ObjectBinding< Storage > storage = Bindings.createObjectBinding(
				() -> Optional
						.ofNullable( projectChoices.valueProperty().get() )
						.map( s -> GoogleCloudClientBuilder.createStorage( oauth, s.getProjectId() ) )
						.orElse( null ),
				projectChoices.valueProperty() );

		storage.addListener( ( obs, oldv, newv ) -> {
			if ( newv == null || newv.equals( oldv ) )
				return;
			final List< Bucket > buckets = new ArrayList<>();
			newv.list().iterateAll().forEach( buckets::add );
			this.buckets.setAll( buckets );
		} );

		dialog.getDialogPane().getButtonTypes().addAll( ButtonType.OK, ButtonType.CANCEL );
		final String selectedBucketName = dialog.showAndWait().orElse( null );

		if ( selectedBucketName == null )
			return Optional.empty();

		return Optional.of( new ValuePair<>( storage.get(), selectedBucketName ) );
		// String.format( "gs://%s/", selectedBucketName ) ) );
	}

	public static void main( final String[] args ) throws IOException, InterruptedException
	{

		final GoogleCloudBrowseHandler handler = new GoogleCloudBrowseHandler();

		PlatformImpl.startup( () -> {} );
		InvokeOnJavaFXApplicationThread.invoke( () -> {
			final Scene scene = new Scene( new StackPane( new Label( "test" ) ) );
			final Pair< Storage, String > sab = handler.select( scene ).orElse( new ValuePair<>( null, "<NO VALUE>" ) );
			LOG.warn( "storage={} bucket={}", sab.getA(), sab.getB() );
		} );

	}

	public static Supplier< InputStream > googleCloudClientSecretsFromFileDialog( final Scene scene )
	{
		final Dialog< InputStream > dialog = new Dialog<>();

		dialog.setTitle( "Google Cloud Client Secrets" );
		dialog.setResizable( true );
		dialog.getDialogPane().getButtonTypes().addAll( ButtonType.OK, ButtonType.CANCEL );
		dialog.setHeaderText( "Please specify path to google cloud client secrets file (json)" );
		final TextField tf = new TextField();
		final Button browseButton = new Button( "Browse" );
		dialog.getDialogPane().setContent( new HBox( tf, browseButton ) );
		HBox.setHgrow( tf, Priority.ALWAYS );

		final FileChooser chooser = new FileChooser();
		chooser.getExtensionFilters().addAll( new ExtensionFilter( "json files", "*.json" ) );

		dialog.setResultConverter( ( bt ) -> {
			if ( ButtonType.CANCEL.equals( bt ) )
				return null;
			final File f = new File( Optional.ofNullable( tf.getText() ).orElse( "" ) );

			if ( !f.exists() )
				throw new RuntimeException( String.format( "File %s does not exist!", f ) );

			if ( !f.isFile() )
				throw new RuntimeException( String.format( "File %s is not a regular file!", f ) );

			try
			{
				return new FileInputStream( f.getAbsolutePath() );
			}
			catch ( final FileNotFoundException e )
			{
				throw new RuntimeException( e );
			}
		} );

		tf.setPromptText( "google cloud client secrets file" );
		browseButton.setOnAction( event -> {
			final File currentSelection = new File( Optional.ofNullable( tf.getText() ).orElse( "" ) );
			chooser.setInitialDirectory( currentSelection.isFile()
					? currentSelection.getParentFile()
					: new File( System.getProperty( "user.home" ) ) );
			final File selection = chooser.showOpenDialog( scene.getWindow() );
			Optional.ofNullable( selection ).map( File::getAbsolutePath ).ifPresent( tf::setText );
		} );

		return () -> dialog.showAndWait().orElse( null );
	}

}
