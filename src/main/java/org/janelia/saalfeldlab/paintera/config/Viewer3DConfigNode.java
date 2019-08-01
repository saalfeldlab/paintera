package org.janelia.saalfeldlab.paintera.config;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.fx.Labels;

public class Viewer3DConfigNode
{

	private static final double PREF_CELL_WIDTH = 50.0;

	private final TitledPane contents = new TitledPane("3D Viewer", null);

	private final CheckBox areMeshesEnabledCheckBox = new CheckBox();

	private final ColorPicker backgroundColorPicker = new ColorPicker(Color.BLACK);

	public Viewer3DConfigNode()
	{
		contents.setGraphic(areMeshesEnabledCheckBox);
		contents.setExpanded(false);
		contents.collapsibleProperty().bind(areMeshesEnabledCheckBox.selectedProperty());
		final GridPane settingsGrid = new GridPane();
		final Label backgroundColorLabel = Labels.withTooltip("Background", "Set background color of 3D viewer.");
		settingsGrid.add(backgroundColorLabel, 0, 0);
		settingsGrid.add(backgroundColorPicker, 1, 0);

		settingsGrid.setPadding(Insets.EMPTY);
		GridPane.setHgrow(backgroundColorLabel, Priority.ALWAYS);
		backgroundColorPicker.setPrefWidth(PREF_CELL_WIDTH);

		contents.setPadding(Insets.EMPTY);
		contents.setContent(settingsGrid);
	}

	public void bind(final Viewer3DConfig config)
	{
		areMeshesEnabledCheckBox.selectedProperty().bindBidirectional(config.areMeshesEnabledProperty());
		backgroundColorPicker.valueProperty().bindBidirectional(config.backgroundColorProperty());
	}

	public Node getContents()
	{
		return contents;
	}

}
