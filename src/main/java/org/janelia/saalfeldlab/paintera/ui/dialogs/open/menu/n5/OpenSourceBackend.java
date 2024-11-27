package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState;
import se.sawano.java.text.AlphanumericComparator;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class OpenSourceBackend {

	public static final String DATASET_PROMPT = "_Dataset";

	private final Node node;

	final OpenSourceState openSourceState;

	BooleanProperty isBusyProperty = new SimpleBooleanProperty(false);

	public OpenSourceBackend(
			final OpenSourceState openSourceState,
			final Node containerLocationNode,
			final Node browseNode,
			final BooleanProperty isOpeningContainer) {

		this.openSourceState = openSourceState;
		node = initializeNode(containerLocationNode, DATASET_PROMPT, browseNode);
		isBusyProperty.bind(isOpeningContainer);
	}

	public ObservableValue<Boolean> getVisibleProperty() {

		return node.visibleProperty();
	}

	public Node getDialogNode() {

		return node;
	}

	private Node initializeNode(final Node rootNode, final String datasetPromptText, final Node browseNode) {

		/* Create the grid and add the root node */
		final GridPane grid = new GridPane();
		grid.add(rootNode, 0, 0);
		GridPane.setColumnSpan(rootNode, 2);
		GridPane.setHgrow(rootNode, Priority.ALWAYS);

		/* create and add the datasetDropdown Menu*/
		final MenuButton datasetDropDown = createDatasetDropdownMenu(datasetPromptText);
		grid.add(datasetDropDown, 1, 1);
		GridPane.setHgrow(datasetDropDown, Priority.ALWAYS);

		grid.add(browseNode, 2, 0);

		ProgressIndicator progressIndicator = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
		progressIndicator.setScaleX(.75);
		progressIndicator.setScaleY(.75);

		grid.add(progressIndicator, 2, 1);
		GridPane.setHgrow(progressIndicator, Priority.NEVER);
		GridPane.setVgrow(progressIndicator, Priority.NEVER);
		progressIndicator.visibleProperty().bind(isBusyProperty);

		return grid;
	}

	private MenuButton createDatasetDropdownMenu(String datasetPromptText) {

		final SimpleObjectProperty<N5TreeNode> activeN5Node = openSourceState.getActiveNodeProperty();
		final StringBinding datasetDropDownText = Bindings.createStringBinding(
				() -> openSourceState.getDatasetPath() == null || openSourceState.getDatasetPath().isEmpty() ? datasetPromptText : datasetPromptText + ": " + openSourceState.getDatasetPath(),
				activeN5Node);

		final ObservableList<String> choices = FXCollections.observableArrayList();

		final Consumer<String> onMatchFound = s -> activeN5Node.set(openSourceState.getValidDatasets().get().get(s));

		final var datasetDropDown = new MatchSelectionMenuButton(choices, datasetDropDownText.get(), null, onMatchFound);
		datasetDropDown.setCutoff(50);

		final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(
				() -> Optional.ofNullable(openSourceState.getDatasetPath()).map(Tooltip::new).orElse(null),
				activeN5Node);

		datasetDropDown.tooltipProperty().bind(datasetDropDownTooltip);
		/* disable when there are no choices */
		final var datasetDropDownDisable = Bindings.createBooleanBinding(() -> openSourceState.getValidDatasets().get().isEmpty(),
				openSourceState.getValidDatasets());
		datasetDropDown.disableProperty().bind(datasetDropDownDisable);
		datasetDropDown.textProperty().bind(datasetDropDownText);
		/* If the datasetchoices are changed, create new menuItems, and update*/

		openSourceState.getValidDatasets().subscribe(() -> {
			final Set<String> keys = openSourceState.getValidDatasets().get().keySet();
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				choices.setAll(keys);
				choices.sort(new AlphanumericComparator());
			});
		});
		return datasetDropDown;
	}

}
