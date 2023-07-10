package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.ui.NamedNode;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.ui.SpatialField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

import java.util.List;

public class MipMapLevel {

	final SpatialField<IntegerProperty> relativeDownsamplingFactors;

	final NumberField<IntegerProperty> maxNumberOfEntriesPerSet;

	final double fieldWidth;

	final Node node;

	final SimpleBooleanProperty showHeader = new SimpleBooleanProperty(false);

	public MipMapLevel(int downsamplingFactor, int maxNumEntries, double fieldWidth, final double nameWidth, ObjectField.SubmitOn... submitOn) {

		this(
				SpatialField.intField(downsamplingFactor, f -> f > 0, fieldWidth, submitOn),
				NumberField.intField(maxNumEntries, n -> true, submitOn),
				fieldWidth,
				nameWidth);
	}

	protected MipMapLevel(
			SpatialField<IntegerProperty> relativeDownsamplingFactors,
			NumberField<IntegerProperty> maxNumberOfEntriesPerSet,
			final double fieldWidth,
			final double nameWidth) {

		this.relativeDownsamplingFactors = relativeDownsamplingFactors;
		this.maxNumberOfEntriesPerSet = maxNumberOfEntriesPerSet;
		this.fieldWidth = fieldWidth;

		final HBox relativeFactorsHeader = createHeader("Relative Factors");
		final HBox maxEntriesHeader = createHeader("Max Entries");

		final HBox mipMapRow = new HBox(
				new VBox(relativeFactorsHeader, relativeDownsamplingFactors.getNode()),
				new VBox(maxEntriesHeader, maxNumberOfEntriesPerSet.getTextField())
		);


		mipMapRow.setPadding(new Insets(0, 10.0, 0, 10.0));
		mipMapRow.spacingProperty().setValue(10.0);
		this.node = mipMapRow;

	}

	private HBox createHeader(String headerText) {
		final Label label = new Label(headerText);
		final HBox header = new HBox(label);
		header.setAlignment(Pos.BOTTOM_CENTER);
		HBox.setHgrow(label, Priority.ALWAYS);
		header.setPadding(new Insets(0, 0, 3.0, 0));
		header.visibleProperty().bind(showHeader);
		header.managedProperty().bind(showHeader);
		return header;
	}

	public double[] downsamplingFactors() {

		return relativeDownsamplingFactors.asDoubleArray();
	}

	public int maxNumEntries() {

		return this.maxNumberOfEntriesPerSet.valueProperty().get();
	}

	public static Node makeNode(
			final ObservableList<MipMapLevel> levels,
			final double fieldWidth,
			final double nameWidth,
			final double buttonWidth,
			ObjectField.SubmitOn... submitOn) {

		final VBox levelsBox = new VBox();
		final ObservableList<Node> children = levelsBox.getChildren();
		levels.addListener((ListChangeListener<MipMapLevel>)change -> InvokeOnJavaFXApplicationThread.invoke(() -> {
			children.stream().filter(n -> n instanceof Pane).map(n -> (Pane)n).map(Pane::getChildren).forEach(List::clear);
			children.clear();
			for (int i = 0; i < levels.size(); i++) {
				final MipMapLevel level = levels.get(i);
				final Button removeButton = new Button("-");
				removeButton.setPrefWidth(buttonWidth);
				removeButton.setMinWidth(buttonWidth);
				removeButton.setMaxWidth(buttonWidth);
				removeButton.setOnAction(e -> {
					e.consume();
					levels.remove(level);
				});
				final Node filler = NamedNode.bufferNode();
				filler.minWidth(10);
				final Node filler2 = NamedNode.bufferNode();
				filler2.minWidth(10);
				HBox.setHgrow(filler, Priority.ALWAYS);
				final HBox scaleLevelRow = new HBox(new Label("Scale " + i + ": "), level.node, removeButton);
				children.add(scaleLevelRow);
			}
		}));

		final Button addButton = new Button("+");
		addButton.setPrefWidth(buttonWidth);
		addButton.setMinWidth(buttonWidth);
		addButton.setMaxWidth(buttonWidth);
		addButton.setOnAction(e -> {
			e.consume();
			levels.add(new MipMapLevel(2, -1, fieldWidth, nameWidth, submitOn));
		});

		return new VBox(addButton, levelsBox);
	}
}
