package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import java.util.List;

import javafx.beans.property.IntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.ui.NamedNode;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.ui.SpatialField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

public class MipMapLevel
{

	final SpatialField<IntegerProperty> relativeDownsamplingFactors;

	final NumberField<IntegerProperty> maxNumberOfEntriesPerSet;

	final double fieldWidth;

	final Node node;

	public MipMapLevel(int downsamplingFactor, int maxNumEntries, double fieldWidth, final double nameWidth, ObjectField.SubmitOn... submitOn)
	{
		this(
				SpatialField.intField(downsamplingFactor, f -> f > 0, fieldWidth, submitOn),
				NumberField.intField(maxNumEntries, n -> true, submitOn),
				fieldWidth,
				nameWidth);
	}

	private MipMapLevel(
			SpatialField<IntegerProperty> relativeDownsamplingFactors,
			NumberField<IntegerProperty> maxNumberOfEntriesPerSet,
			final double fieldWidth,
			final double nameWidth)
	{
		this.relativeDownsamplingFactors = relativeDownsamplingFactors;
		this.maxNumberOfEntriesPerSet = maxNumberOfEntriesPerSet;
		this.fieldWidth = fieldWidth;

		this.node = new HBox(
				NamedNode.nameIt("Relative factors", nameWidth, false, relativeDownsamplingFactors.getNode()),
				NamedNode.nameIt( "Max Num Entries", nameWidth, false, maxNumberOfEntriesPerSet.textField())
		                );
	}

	public double[] downsamplingFactors()
	{
		return relativeDownsamplingFactors.getAs(new double[3]);
	}

	public int maxNumEntries()
	{
		return this.maxNumberOfEntriesPerSet.valueProperty().get();
	}

	public static Node makeNode(
			final ObservableList<MipMapLevel> levels,
			final double fieldWidth,
			final double nameWidth,
			final double buttonWidth,
			ObjectField.SubmitOn... submitOn)
	{

		final VBox                 levelsBox = new VBox();
		final ObservableList<Node> children  = levelsBox.getChildren();
		levels.addListener((ListChangeListener<MipMapLevel>)change -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				children.stream().filter(n -> n instanceof Pane).map(n -> (Pane)n).map(Pane::getChildren).forEach(List::clear);
				children.clear();
				for (MipMapLevel l : levels)
				{
					final Button removeButton = new Button("-");
					removeButton.setPrefWidth(buttonWidth);
					removeButton.setMinWidth(buttonWidth);
					removeButton.setMaxWidth(buttonWidth);
					removeButton.setOnAction( e -> {
						e.consume();
						levels.remove(l);
					});
					final Region filler = new Region();
					filler.setMinWidth(10);
					HBox.setHgrow(filler, Priority.ALWAYS);
					final HBox b = new HBox(l.node, filler, removeButton);
					children.add(b);
				}
			} );

		});

		final Button addButton = new Button("+");
		addButton.setPrefWidth(buttonWidth);
		addButton.setMinWidth(buttonWidth);
		addButton.setMaxWidth(buttonWidth);
		final Region filler = new Region();
		HBox.setHgrow(filler, Priority.ALWAYS);
		final HBox addBox = new HBox(filler, addButton);
		addButton.setOnAction( e -> {
			e.consume();
			levels.add(new MipMapLevel(2, -1, fieldWidth, nameWidth, submitOn));
		});

		return new VBox(addBox, levelsBox);
	}
}
