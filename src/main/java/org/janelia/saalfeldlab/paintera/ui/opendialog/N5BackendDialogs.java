package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud.GoogleCloudBrowseHandler;
import org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud.StorageAndBucket;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class N5BackendDialogs
{

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static File USER_HOME = new File(System.getProperty("user.home"));

	private static final String[] H5_EXTENSIONS = {"*.h5", "*.hdf", "*.hdf5"};

	public static GenericBackendDialogN5 fileSystem(
			final ExecutorService propagationExecutor)
	{
		final StringProperty                     root           = new SimpleStringProperty();
		final ObjectProperty<Supplier<N5Writer>> writerSupplier = new SimpleObjectProperty<>(() -> null);
		final TextField                          rootField      = new TextField();
		rootField.setMinWidth(0);
		rootField.setMaxWidth(Double.POSITIVE_INFINITY);
		rootField.setPromptText("N5 root");
		rootField.textProperty().bindBidirectional(root);

		final DirectoryChooser directoryChooser = new DirectoryChooser();

		final Consumer<Event> onClick = event -> {

			directoryChooser.setInitialDirectory(Optional
					.ofNullable(root.get())
					.map(File::new)
					.filter(File::exists)
					.filter(File::isDirectory)
					.orElse(USER_HOME));
			final File updatedRoot = directoryChooser.showDialog(rootField.getScene().getWindow());

			LOG.debug("Updated root to {}", updatedRoot);

			if (updatedRoot != null && updatedRoot.exists() && updatedRoot.isDirectory())
			{
				final String path = updatedRoot.getAbsolutePath();
				root.set(path);
				writerSupplier.set(MakeUnchecked.supplier(() -> new N5FSWriter(path)));
				LOG.debug("Updated root={} and writer supplier={}", root, writerSupplier);
			}
			Optional
					.ofNullable(updatedRoot)
					.filter(File::exists)
					.filter(File::isFile)
					.map(File::getAbsolutePath)
					.ifPresent(root::set);
		};
		return new GenericBackendDialogN5(rootField, onClick, "N5", writerSupplier, propagationExecutor);
	}

	public static GenericBackendDialogN5 hdf5(
			final ExecutorService propagationExecutor)
	{
		final StringProperty                     root           = new SimpleStringProperty();
		final ObjectProperty<Supplier<N5Writer>> writerSupplier = new SimpleObjectProperty<>(() -> null);
		final TextField                          rootField      = new TextField();
		rootField.setMinWidth(0);
		rootField.setMaxWidth(Double.POSITIVE_INFINITY);
		rootField.setPromptText("H5 file");
		rootField.textProperty().bindBidirectional(root);

		final FileChooser fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().setAll(new ExtensionFilter("h5", H5_EXTENSIONS));

		final int[] defaultBlockSize = {16, 16, 16};

		final boolean overrideBlockSize = false;

		final Consumer<Event> onClick = event -> {
			fileChooser.setInitialDirectory(Optional
					.ofNullable(root.get())
					.map(File::new)
					.filter(File::exists)
					.filter(File::isDirectory)
					.orElse(USER_HOME));
			final File updatedRoot = fileChooser.showOpenDialog(rootField.getScene().getWindow());
			if (updatedRoot != null && updatedRoot.exists() && updatedRoot.isFile())
			{
				root.set(updatedRoot.getAbsolutePath());
				// TODO what to do with block size?
				writerSupplier.set(MakeUnchecked.supplier(() -> new N5HDF5Writer(root.get(), defaultBlockSize)));
			}
			Optional
					.ofNullable(updatedRoot)
					.filter(File::exists)
					.filter(File::isFile)
					.map(File::getAbsolutePath)
					.ifPresent(root::set);
		};
		return new GenericBackendDialogN5(rootField, onClick, "HDF5", writerSupplier, propagationExecutor);
	}

	public static GenericBackendDialogN5 googleCloud(
			final ExecutorService propagationExecutor)
	{

		final ObjectProperty<Storage> storage          = new SimpleObjectProperty<>();
		final ObjectProperty<Bucket>  bucket           = new SimpleObjectProperty<>();
		final StorageAndBucket        storageAndBucket = new StorageAndBucket();
		storageAndBucket.storage.bindBidirectional(storage);
		storageAndBucket.bucket.bindBidirectional(bucket);

		final BooleanBinding isValid = storage.isNotNull().and(bucket.isNotNull());

		final Label storageLabel = new Label();
		final Label bucketLabel  = new Label();

		final ObservableValue<Supplier<N5Writer>> writerSupplier = Bindings.createObjectBinding(
				() -> isValid.get()
				      ? MakeUnchecked.supplier(() -> new N5GoogleCloudStorageWriter(
						storage.get(),
						bucket.get().getName()
				))
				      : (Supplier<N5Writer>) () -> null,
				isValid,
				storage,
				bucket
		                                                                                       );

		final StringBinding storageAsString = Bindings.createStringBinding(
				() -> Optional.ofNullable(storage.getValue()).map(Storage::toString).orElse(""),
				storage
		                                                                  );

		final StringBinding bucketAsString = Bindings.createStringBinding(
				() -> Optional.ofNullable(bucket.getValue()).map(Bucket::getName).orElse(""),
				bucket
		                                                                 );

		storageLabel.textProperty().bind(storageAsString);
		bucketLabel.textProperty().bind(bucketAsString);

		final GridPane grid = new GridPane();
		grid.add(storageLabel, 1, 0);
		grid.add(bucketLabel, 1, 1);

		grid.add(new Label("storage"), 0, 0);
		grid.add(new Label("bucket"), 0, 1);

		grid.setHgap(10);

		final Consumer<Event> onClick = event -> {
			{
				final GoogleCloudBrowseHandler        handler = new GoogleCloudBrowseHandler();
				final Optional<Pair<Storage, Bucket>> res     = handler.select(grid.getScene());
				LOG.debug("Got result from handler: {}", res);
				if (res.isPresent())
				{
					LOG.debug("Got result from handler: {} {}", res.get().getA(), res.get().getB());
					storageAndBucket.storage.set(res.get().getA());
					storageAndBucket.bucket.set(res.get().getB());
				}
			}
		};

		return new GenericBackendDialogN5(grid, onClick, "google", writerSupplier, propagationExecutor);
	}

}
