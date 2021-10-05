package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import com.google.common.collect.Lists;
import com.pivovarit.function.ThrowingSupplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml;
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState;
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
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class N5FactoryOpener {

  private static final String DEFAULT_DIRECTORY = (String)PainteraConfigYaml.getConfig(
		  () -> PainteraConfigYaml.getConfig(() -> null, "data", "defaultDirectory"),
		  "data", "n5", "defaultDirectory"
  );

  private static final List<String> FAVORITES = Collections.unmodifiableList((List<String>)PainteraConfigYaml.getConfig(ArrayList::new, "data", "n5", "favorites"));

  private static final String[] H5_EXTENSIONS = {"*.h5", "*.hdf", "*.hdf5"};

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final N5Factory FACTORY = Paintera.getN5Factory();

  private static final HashMap<String, N5ContainerState> previousContainers = new HashMap<>();

  static {
	FACTORY.hdf5DefaultBlockSize(64, 64, 64);
  }

  private final StringProperty selectionProperty = new SimpleStringProperty();
  private final ObjectProperty<N5ContainerState> containerState = new SimpleObjectProperty<>();
  private BooleanProperty isOpeningContainer = new SimpleBooleanProperty(false);

  {
	selectionProperty.addListener(this::selectionChanged);
	Optional.ofNullable(DEFAULT_DIRECTORY).ifPresent(defaultDir -> {
	  selectionProperty.set(ThrowingSupplier.unchecked(Paths.get(defaultDir)::toRealPath).get().toString());
	});
  }

  public GenericBackendDialogN5 backendDialog() throws IOException {

	final ObjectField<String, StringProperty> containerField = ObjectField.stringField(selectionProperty.get(), ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.ENTER_PRESSED);
	final TextField containerTextField = containerField.getTextField();
	final var tooltipBinding = Bindings.createObjectBinding(() -> new Tooltip(containerTextField.getText()), containerTextField.textProperty());
	containerTextField.tooltipProperty().bind(tooltipBinding);
	containerField.valueProperty().bindBidirectional(selectionProperty);
	containerTextField.setMinWidth(0);
	containerTextField.setMaxWidth(Double.POSITIVE_INFINITY);
	containerTextField.setPromptText("N5 container");

	final EventHandler<ActionEvent> onBrowseFoldersClicked = event -> {

	  final File initialDirectory = Optional
			  .ofNullable(selectionProperty.get())
			  .map(File::new)
			  .filter(File::exists)
			  .orElse(Path.of(".").toAbsolutePath().toFile());
	  updateFromDirectoryChooser(initialDirectory, containerTextField.getScene().getWindow());

	};

	final EventHandler<ActionEvent> onBrowseFilesClicked = event -> {
	  final File initialDirectory = Optional
			  .ofNullable(selectionProperty.get())
			  .map(File::new)
			  .map(f -> f.isFile() ? f.getParentFile() : f)
			  .filter(File::exists)
			  .orElse(Path.of(".").toAbsolutePath().toFile());
	  updateFromFileChooser(initialDirectory, containerTextField.getScene().getWindow());
	};

	List<String> recentSelections = Lists.reverse(PainteraCache.readLines(this.getClass(), "recent"));
	final MenuButton menuButton = BrowseRecentFavorites.menuButton("_Find", recentSelections, FAVORITES, onBrowseFoldersClicked, onBrowseFilesClicked, selectionProperty::set);

	return new GenericBackendDialogN5(containerTextField, menuButton, "N5", containerState, isOpeningContainer);
  }

  public void selectionAccepted() {

	cacheCurrentSelectionAsRecent();
  }

  private void cacheCurrentSelectionAsRecent() {

	final String path = selectionProperty.get();
	if (path != null)
	  PainteraCache.appendLine(getClass(), "recent", path, 50);
  }

  /**
   * Open {@code url} as an  N5Reader if possible, else empty.
   *
   * @param url location of the container we wish to open as an N5Writer.
   * @return N5Reader of {@code url} if valid N5 container; else empty
   */
  private Optional<N5Reader> openN5Reader(final String url) {

	try {
	  final var reader = Paintera.getN5Factory().openReader(url);
	  if (!reader.exists("")) {
		LOG.debug("{} cannot be opened as an N5Reader.", url);
		return Optional.empty();
	  }
	  LOG.debug("{} was opened as an N5Reader.", url);
	  return Optional.of(reader);
	} catch (Exception e) {
	  LOG.debug("{} cannot be opened as an N5Reader.", url);
	}
	return Optional.empty();
  }

  /**
   * Open {@code url} as an  N5Writer if possible, else empty.
   *
   * @param url location of the container we wish to open as an N5Writer.
   * @return N5Writer of {@code url} if valid N5 container which we can write to; else empty
   */
  private Optional<N5Writer> openN5Writer(final String url) {

	try {
	  final var writer = Paintera.getN5Factory().openWriter(url);
	  LOG.debug("{} was opened as an N5Writer.", url);
	  return Optional.of(writer);
	} catch (Exception e) {
	  LOG.debug("{} cannot be opened as an N5Writer.", url);
	}
	return Optional.empty();
  }

  private static boolean isN5Container(final String pathToDirectory) {

	try {
	  final var reader = Paintera.getN5Factory().openReader(pathToDirectory);
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

	LOG.debug("Updating root to {} (was {})", updatedRoot, selectionProperty.get());

	if (updatedRoot != null && updatedRoot.exists() && updatedRoot.isFile())
	  selectionProperty.set(updatedRoot.getAbsolutePath());
  }

  private void updateFromDirectoryChooser(final File initialDirectory, final Window ownerWindow) {

	final DirectoryChooser directoryChooser = new DirectoryChooser();
	Optional.of(initialDirectory)
			.map(x -> x.isDirectory() ? x : x.getParentFile())
			.ifPresent(directoryChooser::setInitialDirectory);
	Optional.ofNullable(directoryChooser.showDialog(ownerWindow)).ifPresent(updatedRoot -> {
	  LOG.debug("Updating root to {} (was {})", updatedRoot, selectionProperty.get());

	  if (fileOpenableAsN5(updatedRoot)) {
		// set null first to make sure that selectionProperty will be invalidated even if directory is the same
		String updatedAbsPath = updatedRoot.getAbsolutePath();
		if (updatedAbsPath.equals(selectionProperty.get())) {
		  selectionProperty.set(null);
		}
		selectionProperty.set(updatedAbsPath);
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
			  "A valid N5 container is a directory that contains a file attributes.json with a key \"n5\"."); //FIXME meta need a more accurate message
	  ta.setEditable(false);
	  ta.setWrapText(true);
	  alert.getDialogPane().setContent(ta);
	  alert.show();
	  return false;
	}
	return true;
  }

  private void selectionChanged(ObservableValue<? extends String> obs, String oldSelection, String newSelection) {

	if (newSelection == null || newSelection.isBlank()) {
	  containerState.set(null);
	  return;
	}

	Tasks.createTask(
					task -> {
					  invoke(() -> this.isOpeningContainer.set(true));
					  final var newContainerState = Optional.ofNullable(previousContainers.get(newSelection)).orElseGet(() -> {

						/* Ok we don't want to do the writer first, even though it means we need to create a separate writer in the case that it can have both.
						 * This is because if the path provided doesn't currently contain a writer, but it has permissions to create a writer, it will do so.
						 * This means that if there is no N5 container, it will create one.
						 *
						 * In this case, we only want to create a writer if there is already an N5 container. To check, we create a reader first, and see if it
						 * exists. */
						final var reader = openN5Reader(newSelection);
						if (reader.isEmpty()) {
						  return null;
						}

						final Optional<N5Writer> writer = openN5Writer(newSelection);

						/* If we have a writer, use it as the reader also; If not, use the reader we create above.*/
						return writer
								.map(w -> new N5ContainerState(newSelection, w, w))
								.orElseGet(() -> new N5ContainerState(newSelection, reader.get(), null));
					  });
					  if (newContainerState == null)
						return false;

					  invoke(() -> containerState.set(newContainerState));
					  previousContainers.put(newSelection, newContainerState);
					  return true;
					})
			.onEnd(task -> invoke(() -> this.isOpeningContainer.set(false)))
			.submit();
  }
}
