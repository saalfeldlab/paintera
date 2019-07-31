package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import com.google.common.collect.Lists;
import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingSupplier;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.util.PainteraCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FileSystem {


	private static final String USER_HOME = System.getProperty("user.home");

	private static final String DEFAULT_DIRECTORY = (String) PainteraConfigYaml.getConfig(() -> PainteraConfigYaml.getConfig(() -> USER_HOME, "data", "defaultDirectory"), "data", "n5", "defaultDirectory");

	private static final List<String> FAVORITES = Collections.unmodifiableList((List < String >) PainteraConfigYaml.getConfig(ArrayList::new, "data", "n5", "favorites"));

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final StringProperty container = new SimpleStringProperty(ThrowingSupplier.unchecked(Paths.get(DEFAULT_DIRECTORY)::toRealPath).get().toString());

	private final ObjectProperty<Supplier<N5Writer>> writerSupplier = new SimpleObjectProperty<>(() -> null);

	{
		container.addListener((obs, oldv, newv) -> {
			try {
				updateWriterSupplier(newv);
			}
			catch (final IOException e) {
				LOG.debug("Unable to set N5FSWriter for path {}", newv, e);
			}
		});
	}

	public GenericBackendDialogN5 backendDialog(ExecutorService propagationExecutor) throws IOException {
		final ObjectField<String, StringProperty> containerField = ObjectField.stringField(container.get(), ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.ENTER_PRESSED);
		final TextField containerTextField = containerField.textField();
		containerField.valueProperty().bindBidirectional(container);
		containerTextField.setMinWidth(0);
		containerTextField.setMaxWidth(Double.POSITIVE_INFINITY);
		containerTextField.setPromptText("N5 container");

		final EventHandler<ActionEvent> onBrowseButtonClicked = event -> {

			final File initialDirectory = Optional
					.ofNullable(container.get())
					.map(File::new)
					.filter(File::exists)
					.filter(File::isDirectory)
					.orElse(new File(DEFAULT_DIRECTORY));
			updateFromDirectoryChooser(initialDirectory, containerTextField.getScene().getWindow());

		};

		final Consumer<String> processSelection = ThrowingConsumer.unchecked(selection -> {
			LOG.info("Got selection {}", selection);

			if (selection == null)
				return;

			if (isN5Container(selection)) {
				container.set(null);
				container.set(selection);
				return;
			}

			updateFromDirectoryChooser(Paths.get(selection).toFile(), containerTextField.getScene().getWindow());


		});

		final MenuButton menuButton = BrowseRecentFavorites.menuButton("_Find", Lists.reverse(PainteraCache.readLines(this.getClass(), "recent")), FAVORITES, onBrowseButtonClicked, processSelection);

		GenericBackendDialogN5 d = new GenericBackendDialogN5(containerTextField, menuButton, "N5", writerSupplier, propagationExecutor);
		final String path = container.get();
		updateWriterSupplier(path);
		return d;
	}

	public void containerAccepted() {
		cacheCurrentContainerAsRecent();
	}

	private void cacheCurrentContainerAsRecent() {
		final String path = container.get();
		if (path != null)
			PainteraCache.appendLine(getClass(), "recent", path, 50);
	}

	/**
	 * Update {@link #writerSupplier} if {@code pathToDirectory} is a valid N5 container, i.e. attributs.json has attribute "n5".
	 * @param pathToDirectory Path to directory that could be N5 container.
	 */
	private void updateWriterSupplier(final String pathToDirectory) throws IOException {
		if (isN5Container(pathToDirectory)) {
			LOG.debug("Path {} is a valid N5 container.", pathToDirectory);
			writerSupplier.set(ThrowingSupplier.unchecked(() -> new N5FSWriter(pathToDirectory)));
		} else {
			LOG.debug("Path {} is not a valid N5 container.", pathToDirectory);
			writerSupplier.set(() -> null);
		}
	}

	private static boolean isN5Container(final String pathToDirectory) throws IOException {
		return pathToDirectory != null
				&&  new File(pathToDirectory).isDirectory()
				&& new N5FSReader(pathToDirectory).getAttributes("/").containsKey("n5");
	}

	private void updateFromDirectoryChooser(final File initialDirectory, final Window ownerWindow) {

		final DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setInitialDirectory(initialDirectory);
		final File updatedRoot = directoryChooser.showDialog(ownerWindow);

		LOG.debug("Updating root to {} (was {})", updatedRoot, container.get());

		try {
			if (updatedRoot != null && !isN5Container(updatedRoot.getAbsolutePath())) {
				final Alert alert = PainteraAlerts.alert(Alert.AlertType.INFORMATION);
				alert.setHeaderText("Selected directory is not a valid N5 container.");
				final TextArea ta = new TextArea("The selected directory \n\n" + updatedRoot.getAbsolutePath() + "\n\n" +
						"A valid N5 container is a directory that contains a file attributes.json with a key \"n5\".");
				ta.setEditable(false);
				ta.setWrapText(true);
				alert.getDialogPane().setContent(ta);
				alert.show();
			}
		}
		catch (final IOException e) {
			LOG.error("Failed to notify about invalid N5 container: {}", updatedRoot.getAbsolutePath(), e);
		}

		if (updatedRoot != null && updatedRoot.exists() && updatedRoot.isDirectory()) {
			// set null first to make sure that container will be invalidated even if directory is the same
			container.set(null);
			container.set(updatedRoot.getAbsolutePath());
		}

	}

}
