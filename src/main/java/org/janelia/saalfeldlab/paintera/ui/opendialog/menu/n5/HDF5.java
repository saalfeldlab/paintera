package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import com.google.common.collect.Lists;
import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingSupplier;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml;
import org.janelia.saalfeldlab.util.PainteraCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HDF5 {

  private static final String USER_HOME = System.getProperty("user.home");

  private static final String[] H5_EXTENSIONS = {"*.h5", "*.hdf", "*.hdf5"};

  private static final String DEFAULT_DIRECTORY = (String)PainteraConfigYaml
		  .getConfig(() -> PainteraConfigYaml.getConfig(() -> USER_HOME, "data", "defaultDirectory"), "data", "n5", "defaultDirectory");

  private static final List<String> FAVORITES = Collections
		  .unmodifiableList((List<String>)PainteraConfigYaml.getConfig(ArrayList::new, "data", "hdf5", "favorites"));

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final StringProperty container = new SimpleStringProperty(DEFAULT_DIRECTORY);

  private final ObjectProperty<Supplier<N5Writer>> writerSupplier = new SimpleObjectProperty<>(() -> null);

  {
	container.addListener((obs, oldv, newv) -> {
	  writerSupplier.set(ThrowingSupplier.unchecked(() -> new N5HDF5Writer(newv, 64, 64, 64)));
	});
  }

  public GenericBackendDialogN5 backendDialog(ExecutorService propagationExecutor) {

	final ObjectField<String, StringProperty> containerField = ObjectField
			.stringField(container.get(), ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.ENTER_PRESSED);
	final TextField containerTextField = containerField.getTextField();
	containerField.valueProperty().bindBidirectional(container);
	containerTextField.setMinWidth(0);
	containerTextField.setMaxWidth(Double.POSITIVE_INFINITY);
	containerTextField.setPromptText("HDF5 file");

	final Consumer<Event> onClick = event -> {
	  final File initialDirectory = Optional
			  .ofNullable(container.get())
			  .map(File::new)
			  .map(f -> f.isFile() ? f.getParentFile() : f)
			  .filter(File::exists)
			  .orElse(new File(USER_HOME));
	  updateFromFileChooser(initialDirectory, containerTextField.getScene().getWindow());
	};

	final Consumer<String> processSelection = ThrowingConsumer.unchecked(selection -> {
	  LOG.info("Got selection {}", selection);
	  if (selection == null)
		return;

	  if (isHDF5(selection)) {
		container.set(selection);
		return;
	  }
	  updateFromFileChooser(new File(selection), containerTextField.getScene().getWindow());
	});

	final MenuButton menuButton = BrowseRecentFavorites.menuButton(
			"_Find",
			Lists.reverse(PainteraCache.readLines(this.getClass(), "recent")),
			FAVORITES,
			onClick::accept,
			processSelection);

	GenericBackendDialogN5 d = new GenericBackendDialogN5(containerTextField, menuButton, "N5", writerSupplier, propagationExecutor);
	final String path = container.get();
	if (path != null && new File(path).isFile())
	  writerSupplier.set(ThrowingSupplier.unchecked(() -> new N5HDF5Writer(path, 64, 64, 64)));
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

  private static boolean isHDF5(final String path) {

	return new File(path).isFile() && (path.endsWith(".h5") || path.endsWith(".hdf"));
  }

  private void updateFromFileChooser(final File initialDirectory, final Window owner) {

	final FileChooser fileChooser = new FileChooser();
	fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("h5", H5_EXTENSIONS));
	fileChooser.setInitialDirectory(Optional
			.ofNullable(container.get())
			.map(File::new)
			.map(f -> f.isFile() ? f.getParentFile() : f)
			.filter(File::exists)
			.orElse(new File(USER_HOME)));
	final File updatedRoot = fileChooser.showOpenDialog(owner);

	LOG.debug("Updating root to {}", updatedRoot);

	if (updatedRoot != null && updatedRoot.exists() && updatedRoot.isFile())
	  container.set(updatedRoot.getAbsolutePath());
  }
}
