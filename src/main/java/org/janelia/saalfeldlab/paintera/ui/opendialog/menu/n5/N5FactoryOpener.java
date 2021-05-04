package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import com.google.common.collect.Lists;
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
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.util.PainteraCache;
import org.janelia.saalfeldlab.util.n5.universe.N5Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class N5FactoryOpener {

  private static final String DEFAULT_DIRECTORY = (String)PainteraConfigYaml
		  .getConfig(() -> PainteraConfigYaml.getConfig(() -> null, "data", "defaultDirectory"), "data", "n5", "defaultDirectory");

  private static final List<String> FAVORITES = Collections.unmodifiableList((List<String>)PainteraConfigYaml.getConfig(ArrayList::new, "data", "n5", "favorites"));

  private static final String[] H5_EXTENSIONS = {"*.h5", "*.hdf", "*.hdf5"};

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final N5Factory FACTORY = new N5Factory();

  static {
	FACTORY.hdf5DefaultBlockSize(64, 64, 64);
  }

  private final StringProperty container = new SimpleStringProperty();

  private final ObjectProperty<N5Writer> sourceWriter = new SimpleObjectProperty<>();
  private final ObjectProperty<N5Reader> sourceReader = new SimpleObjectProperty<>();

  {
	container.addListener((obs, oldv, newv) -> {
	  if (newv == null || newv.isBlank()) {
		sourceWriter.set(null);
		sourceReader.set(null);
		return;
	  }
	  /* Ok we don't want to do the writer first, even though it means we need to create a separate wrer in the case that it can have both.
	   * This is because if the path provided doesn't currently contain a writer, but it has permissions to create a writer, it will do so.
	   * In this case, we only want to create a writer if there is already an N5 container. To check, we create a reader first, and see if it
	   * exists. */
	  final N5Reader n5Reader;
	  try {
		n5Reader = new N5Factory().openReader(newv);
		final var n5ContainerExists = n5Reader.exists("");
		if (!n5ContainerExists) {
		  LOG.debug("Location at {} is not a valid N5 container", newv);
		  return;
		}
		/* Now, we can check for a writer, since we know the location is at least an N5 container now*/
		updateSourceWriter(newv);
	  } catch (IOException ioException) {
		LOG.debug("Unable to create N5Reader from {}", newv);
		return;
	  }

	  /* If we have a writer, use it as the reader also; If not, use the reader we create above.*/
	  if (sourceWriter.isNull().get()) {
		this.sourceReader.set(n5Reader);
		LOG.debug("Unable to set N5Writer from {}", newv);
	  } else {
		this.sourceReader.set(sourceWriter.get());
	  }
	});
	Optional.ofNullable(DEFAULT_DIRECTORY).ifPresent(defaultDir -> {
	  container.set(ThrowingSupplier.unchecked(Paths.get(defaultDir)::toRealPath).get().toString());
	});
  }

  public GenericBackendDialogN5 backendDialog() throws IOException {

	final ObjectField<String, StringProperty> containerField = ObjectField
			.stringField(container.get(), ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.ENTER_PRESSED);
	final TextField containerTextField = containerField.getTextField();
	containerField.valueProperty().bindBidirectional(container);
	containerTextField.setMinWidth(0);
	containerTextField.setMaxWidth(Double.POSITIVE_INFINITY);
	containerTextField.setPromptText("N5 container");

	final EventHandler<ActionEvent> onBrowseFoldersClicked = event -> {

	  final File initialDirectory = Optional
			  .ofNullable(container.get())
			  .map(File::new)
			  .filter(File::exists)
			  .orElse(Path.of(".").toAbsolutePath().toFile());
	  updateFromDirectoryChooser(initialDirectory, containerTextField.getScene().getWindow());

	};

	final EventHandler<ActionEvent> onBrowseFilesClicked = event -> {
	  final File initialDirectory = Optional
			  .ofNullable(container.get())
			  .map(File::new)
			  .map(f -> f.isFile() ? f.getParentFile() : f)
			  .filter(File::exists)
			  .orElse(Path.of(".").toAbsolutePath().toFile());
	  updateFromFileChooser(initialDirectory, containerTextField.getScene().getWindow());
	};

	final MenuButton menuButton = BrowseRecentFavorites
			.menuButton("_Find", Lists.reverse(PainteraCache.readLines(this.getClass(), "recent")), FAVORITES, onBrowseFoldersClicked, onBrowseFilesClicked,
					container::set);

	return new GenericBackendDialogN5(containerTextField, menuButton, "N5", sourceWriter, sourceReader);
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
   * Update {@link #sourceWriter} if {@code url} is a valid N5 container that we have write permission for.
   *
   * @param url location of the container we wish to open as an N5Writer.
   */
  private void updateSourceWriter(final String url) {

	try {
	  final var writer = FACTORY.openWriter(url);
	  sourceWriter.set(writer);
	  LOG.debug("{} was opened for writing as an N5 container.", url);
	} catch (Exception e) {
	  LOG.debug("{} cannot be opened as an N5Writer.", url);
	  sourceWriter.set(null);
	}
  }

  private static boolean isN5Container(final String pathToDirectory) {

	try {
	  final var reader = new N5Factory().openReader(pathToDirectory);
	  boolean isN5 = reader.listAttributes("/").containsKey("n5");
	  if (reader instanceof N5HDF5Reader)
		((N5HDF5Reader)reader).close();
	  return isN5;
	} catch (Exception e) {
	  return false;
	}
  }

  private void updateFromFileChooser(final File initialDirectory, final Window owner) {

	final FileChooser fileChooser = new FileChooser();
	fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("h5", H5_EXTENSIONS));
	fileChooser.setInitialDirectory(initialDirectory);
	final File updatedRoot = fileChooser.showOpenDialog(owner);

	LOG.debug("Updating root to {} (was {})", updatedRoot, container.get());

	if (updatedRoot != null && updatedRoot.exists() && updatedRoot.isFile())
	  container.set(updatedRoot.getAbsolutePath());
  }

  private void updateFromDirectoryChooser(final File initialDirectory, final Window ownerWindow) {

	final DirectoryChooser directoryChooser = new DirectoryChooser();
	Optional.of(initialDirectory)
			.map(x -> x.isDirectory() ? x : x.getParentFile())
			.ifPresent(directoryChooser::setInitialDirectory);
	Optional.ofNullable(directoryChooser.showDialog(ownerWindow)).ifPresent(updatedRoot -> {
	  LOG.debug("Updating root to {} (was {})", updatedRoot, container.get());

	  if (fileOpenableAsN5(updatedRoot)) {
		// set null first to make sure that container will be invalidated even if directory is the same
		String updatedAbsPath = updatedRoot.getAbsolutePath();
		if (container.get().equals(updatedAbsPath)) {
		  container.set(null);
		}
		container.set(updatedAbsPath);
	  }
	});

  }

  private boolean fileOpenableAsN5(File updatedRoot) {

	if (updatedRoot == null) {
	  /* They probably just canceled out of browse; just silently return false; */
	  return false;
	} else if (!isN5Container(updatedRoot.getAbsolutePath())) {
	  final Alert alert = PainteraAlerts.alert(Alert.AlertType.INFORMATION);
	  alert.setHeaderText("Selected path cannot be opened as an N5 container.");
	  final TextArea ta = new TextArea("The selected path is not a valid N5 container\n\n" + updatedRoot.getAbsolutePath() + "\n\n" +
			  "A valid N5 container is a directory that contains a file attributes.json with a key \"n5\".");
	  ta.setEditable(false);
	  ta.setWrapText(true);
	  alert.getDialogPane().setContent(ta);
	  alert.show();
	  return false;
	}
	return true;
  }

}
