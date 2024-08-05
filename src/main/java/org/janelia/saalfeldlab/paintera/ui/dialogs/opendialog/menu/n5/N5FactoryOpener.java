package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5;

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
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml;
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState;
import org.janelia.saalfeldlab.util.PainteraCache;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn.*;
import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class N5FactoryOpener {

	private static final String DEFAULT_DIRECTORY = (String)PainteraConfigYaml.getConfig(
			() -> PainteraConfigYaml.getConfig(() -> null, "data", "defaultDirectory"),
			"data", "n5", "defaultDirectory"
	);

	private static final List<String> FAVORITES = Collections.unmodifiableList(
			(List<String>)PainteraConfigYaml.getConfig(ArrayList::new, "data", "n5", "favorites"));

	private static final String[] H5_EXTENSIONS = {"*.h5", "*.hdf", "*.hdf5"};

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final HashMap<String, N5ContainerState> n5ContainerStateCache = new HashMap<>();

	private final StringProperty selectionProperty = new SimpleStringProperty();
	private final ObjectProperty<N5ContainerState> containerState = new SimpleObjectProperty<>();
	private BooleanProperty isOpeningContainer = new SimpleBooleanProperty(false);

	{
		selectionProperty.addListener(this::selectionChanged);
		Optional.ofNullable(DEFAULT_DIRECTORY).ifPresent(defaultDir -> {
			selectionProperty.set(ThrowingSupplier.unchecked(Paths.get(defaultDir)::toRealPath).get().toString());
		});
	}

	public GenericBackendDialogN5 backendDialog() {

		final ObjectField<String, StringProperty> containerField = ObjectField.stringField(selectionProperty.get(), ENTER_PRESSED, FOCUS_LOST);
		containerField.getTextField().addEventHandler(KeyEvent.KEY_PRESSED, createCachedContainerResetHandler());
		final TextField containerTextField = containerField.getTextField();
		final var tooltipBinding = Bindings.createObjectBinding(() -> new Tooltip(containerTextField.getText()), containerTextField.textProperty());
		containerTextField.tooltipProperty().bind(tooltipBinding);
		containerField.valueProperty().bindBidirectional(selectionProperty);
		containerTextField.setMinWidth(0);
		containerTextField.setMaxWidth(Double.POSITIVE_INFINITY);
		containerTextField.setPromptText("N5 container");

		final EventHandler<ActionEvent> onBrowseFoldersClicked = _ -> {

			final File initialDirectory = Optional
					.ofNullable(selectionProperty.get())
					.map(File::new)
					.filter(File::exists)
					.orElse(Path.of(".").toAbsolutePath().toFile());
			updateFromDirectoryChooser(initialDirectory, containerTextField.getScene().getWindow());
		};

		final EventHandler<ActionEvent> onBrowseFilesClicked = _ -> {
			final File initialDirectory = Optional
					.ofNullable(selectionProperty.get())
					.map(File::new)
					.map(f -> f.isFile() ? f.getParentFile() : f)
					.filter(File::exists)
					.orElse(Path.of(".").toAbsolutePath().toFile());
			updateFromFileChooser(initialDirectory, containerTextField.getScene().getWindow());
		};

		List<String> recentSelections = Lists.reverse(PainteraCache.readLines(this.getClass(), "recent"));
		final MenuButton menuButton = BrowseRecentFavorites.menuButton(
				"_Find",
				recentSelections,
				FAVORITES,
				onBrowseFoldersClicked,
				onBrowseFilesClicked,
				selectionProperty::set);

		return new GenericBackendDialogN5(containerTextField, menuButton, "N5", containerState, isOpeningContainer);
	}

	@NotNull private EventHandler<KeyEvent> createCachedContainerResetHandler() {

		return event -> {
			if (event.getCode() == KeyCode.ENTER) {
				final String url = selectionProperty.get();
				final N5ContainerState oldContainer = n5ContainerStateCache.remove(url);
				containerState.set(null);
				GenericBackendDialogN5.previousContainerChoices.remove(oldContainer);
				selectionChanged(null, null, url);
			}
		};
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
			if (Paintera.getN5Factory().openReaderOrNull(updatedRoot.getAbsolutePath()) != null) {
				// set null first to make sure that selectionProperty will be invalidated even if directory is the same
				String updatedAbsPath = updatedRoot.getAbsolutePath();
				if (updatedAbsPath.equals(selectionProperty.get())) {
					selectionProperty.set(null);
				}
				selectionProperty.set(updatedAbsPath);
			}
		});
	}

	private void selectionChanged(ObservableValue<? extends String> obs, String oldSelection, String newSelection) {

		if (newSelection == null || newSelection.isBlank()) {
			containerState.set(null);
			return;
		}

		Tasks.createTask(() -> {
					invoke(() -> this.isOpeningContainer.set(true));
					final var newContainerState = Optional.ofNullable(n5ContainerStateCache.get(newSelection)).orElseGet(() -> {

						var container = Paintera.getN5Factory().openReaderOrNull(newSelection);
						if (container == null)
							return null;
						if (container instanceof N5HDF5Reader) {
							container.close();
							container = Paintera.getN5Factory().openWriterElseOpenReader(newSelection);
						}
						return new N5ContainerState(container);
					});
					if (newContainerState == null)
						return false;

					invoke(() -> containerState.set(newContainerState));
					n5ContainerStateCache.put(newSelection, newContainerState);
					return true;
				})
				.onEnd((result, error) -> invoke(() -> this.isOpeningContainer.set(false)));
	}
}
