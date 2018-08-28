package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

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
import javafx.stage.FileChooser;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud.GoogleCloudBrowseHandler;
import org.janelia.saalfeldlab.paintera.ui.opendialog.googlecloud.StorageAndBucket;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GoogleCloud {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ObjectProperty<Storage> storage = new SimpleObjectProperty<>();

	private final ObjectProperty<Bucket> bucket = new SimpleObjectProperty<>();

	private final StorageAndBucket storageAndBucket = new StorageAndBucket();
	{
		storageAndBucket.storage.bindBidirectional(storage);
		storageAndBucket.bucket.bindBidirectional(bucket);
	}

	final BooleanBinding isValid = storage.isNotNull().and(bucket.isNotNull());

	private final ObservableValue<Supplier<N5Writer>> writerSupplier = Bindings.createObjectBinding(
			() -> isValid.get()
					? MakeUnchecked.supplier(() -> new N5GoogleCloudStorageWriter(
							storage.get(),
							bucket.get().getName()))
					: (Supplier<N5Writer>) () -> null,
			isValid,
			storage,
			bucket
	);

	public GenericBackendDialogN5 backendDialog(ExecutorService propagationExecutor) {
		final Label storageLabel = new Label();
		final Label bucketLabel  = new Label();
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
				final GoogleCloudBrowseHandler handler = new GoogleCloudBrowseHandler();
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
//
//	final GridPane grid = new GridPane();
//		grid.add(storageLabel, 1, 0);
//		grid.add(bucketLabel, 1, 1);
//
//		grid.add(new Label("storage"), 0, 0);
//		grid.add(new Label("bucket"), 0, 1);
//
//		grid.setHgap(10);
//
//	final Consumer<Event> onClick = event -> {
//		{
//			final GoogleCloudBrowseHandler        handler = new GoogleCloudBrowseHandler();
//			final Optional<Pair<Storage, Bucket>> res     = handler.select(grid.getScene());
//			LOG.debug("Got result from handler: {}", res);
//			if (res.isPresent())
//			{
//				LOG.debug("Got result from handler: {} {}", res.get().getA(), res.get().getB());
//				storageAndBucket.storage.set(res.get().getA());
//				storageAndBucket.bucket.set(res.get().getB());
//			}
//		}
//	};
//
//		return new GenericBackendDialogN5(grid, onClick, "google", writerSupplier, propagationExecutor);
}
