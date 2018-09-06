package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FileSystem {


	private static final String USER_HOME = System.getProperty("user.home");

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final StringProperty container = new SimpleStringProperty(USER_HOME);
	private final ObjectProperty<Supplier<N5Writer>> writerSupplier = new SimpleObjectProperty<>(MakeUnchecked.supplier(() -> null));

	{
		container.addListener((obs, oldv, newv) -> {
			if (!new File(newv).isDirectory())
				container.set(oldv);
			else
				writerSupplier.set(MakeUnchecked.supplier(() -> new N5FSWriter(newv)));
		});
	}

	public GenericBackendDialogN5 backendDialog(ExecutorService propagationExecutor) {
		final ObjectField<String, StringProperty> containerField = ObjectField.stringField(container.get(), ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.ENTER_PRESSED);
		final TextField containerTextField = containerField.textField();
		containerField.valueProperty().bindBidirectional(container);
		containerTextField.setMinWidth(0);
		containerTextField.setMaxWidth(Double.POSITIVE_INFINITY);
		containerTextField.setPromptText("N5 container");

		final DirectoryChooser directoryChooser = new DirectoryChooser();

		final Consumer<Event> onClick = event -> {

			directoryChooser.setInitialDirectory(Optional
					.ofNullable(container.get())
					.map(File::new)
					.filter(File::exists)
					.filter(File::isDirectory)
					.orElse(new File(USER_HOME)));
			final File updatedRoot = directoryChooser.showDialog(containerTextField.getScene().getWindow());

			LOG.debug("Updating root to {}", updatedRoot);

			if (updatedRoot != null && updatedRoot.exists() && updatedRoot.isDirectory())
				container.set(updatedRoot.getAbsolutePath());
		};
		GenericBackendDialogN5 d = new GenericBackendDialogN5(containerTextField, onClick, "N5", writerSupplier, propagationExecutor);
		final String path = container.get();
		if (path != null && new File(path).isDirectory())
			writerSupplier.set(MakeUnchecked.supplier(() -> new N5FSWriter(path)));
		return d;
	}
}
