package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class N5BackendDialogs
{

	private static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static GenericBackendDialogN5 fileSystem()
	{
		final StringProperty root = new SimpleStringProperty();
		final ObjectProperty< Supplier< N5Writer > > writerSupplier = new SimpleObjectProperty<>( () -> null );
		final TextField rootField = new TextField();
		rootField.setMinWidth( 0 );
		rootField.setMaxWidth( Double.POSITIVE_INFINITY );
		rootField.setPromptText( "N5 root" );
		rootField.textProperty().bindBidirectional( root );

		final DirectoryChooser directoryChooser = new DirectoryChooser();

		final Consumer< Event > onClick = event -> {
			final File updatedRoot = directoryChooser.showDialog( rootField.getScene().getWindow() );

			LOG.warn( "Updated root to {}", updatedRoot );

			if ( updatedRoot != null && updatedRoot.exists() && updatedRoot.isDirectory() )
			{
				final String path = updatedRoot.getAbsolutePath();
				root.set( path );
				writerSupplier.set( MakeUnchecked.unchecked( () -> new N5FSWriter( path ) ) );
				LOG.warn( "Updated root={} and writer supplier={}", root, writerSupplier );
			}
			Optional
					.ofNullable( updatedRoot )
					.filter( File::exists )
					.filter( File::isFile )
					.map( File::getAbsolutePath )
					.ifPresent( root::set );
		};
		return new GenericBackendDialogN5( rootField, onClick, "N5", writerSupplier );
	}

	public static GenericBackendDialogN5 hdf5()
	{
		final StringProperty root = new SimpleStringProperty();
		final ObjectProperty< Supplier< N5Writer > > writerSupplier = new SimpleObjectProperty<>( () -> null );
		final TextField rootField = new TextField();
		rootField.setMinWidth( 0 );
		rootField.setMaxWidth( Double.POSITIVE_INFINITY );
		rootField.setPromptText( "H5 file" );
		rootField.textProperty().bindBidirectional( root );

		final FileChooser fileChooser = new FileChooser();

		final Consumer< Event > onClick = event -> {
			final File updatedRoot = fileChooser.showOpenDialog( rootField.getScene().getWindow() );
			if ( updatedRoot != null && updatedRoot.exists() && updatedRoot.isDirectory() )
			{
				root.set( updatedRoot.getAbsolutePath() );
				// TODO what to do with block size?
				writerSupplier.set( MakeUnchecked.unchecked( () -> new N5HDF5Writer( root.get(), 64, 64, 64 ) ) );
			}
			Optional
					.ofNullable( updatedRoot )
					.filter( File::exists )
					.filter( File::isFile )
					.map( File::getAbsolutePath )
					.ifPresent( root::set );
		};
		return new GenericBackendDialogN5( rootField, onClick, "HDF5", writerSupplier );
	}

}
