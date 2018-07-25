package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class GenericRootAndDatasetStructure<T>
{

	private final String datasetPromptText;

	private final Property<String> dataset;

	private final ObservableList<String> datasetChoices;

	private final ObservableValue<Boolean> isDropDownReady;

	private final Consumer<Scene> onBrowseClicked;

	private final Supplier<Node> rootNode;

	private final Node node;

	public GenericRootAndDatasetStructure(
			final String datasetPromptText,
			final Property<T> group,
			final Property<String> dataset,
			final ObservableList<String> datasetChoices,
			final ObservableValue<Boolean> isDropDownReady,
			final Consumer<Scene> onBrowseClicked,
			final Supplier<Node> groupNode)
	{
		super();
		this.datasetPromptText = datasetPromptText;
		this.dataset = dataset;
		this.datasetChoices = datasetChoices;
		this.isDropDownReady = isDropDownReady;
		this.onBrowseClicked = onBrowseClicked;
		this.rootNode = groupNode;
		this.node = createNode();
	}

	private Node createNode()
	{
		final Node             groupField      = rootNode.get();
		final ComboBox<String> datasetDropDown = new ComboBox<>(datasetChoices);
		datasetDropDown.setPromptText(datasetPromptText);
		datasetDropDown.setEditable(false);
		datasetDropDown.valueProperty().bindBidirectional(dataset);
		datasetDropDown.disableProperty().bind(this.isDropDownReady);
		final GridPane grid = new GridPane();
		grid.add(groupField, 0, 0);
		grid.add(datasetDropDown, 0, 1);
		GridPane.setHgrow(groupField, Priority.ALWAYS);
		GridPane.setHgrow(datasetDropDown, Priority.ALWAYS);
		final Button button = new Button("Browse");
		button.setOnAction(event -> onBrowseClicked.accept(grid.getScene()));
		grid.add(button, 1, 0);

		return grid;
	}

	public Node getNode()
	{
		return this.node;
	}

}
