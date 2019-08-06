package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn;
import org.janelia.saalfeldlab.util.fx.UIUtils;

import javafx.beans.property.IntegerProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

public class Viewer3DConfigNode
{

	private static final double PREF_CELL_WIDTH = 50.0;

	private final TitledPane contents;

	private final CheckBox areMeshesEnabledCheckBox = new CheckBox();

	private final CheckBox showBlockBoundariesCheckBox = new CheckBox();

	private final NumberField<IntegerProperty> rendererBlockSizeField;

	private final ColorPicker backgroundColorPicker = new ColorPicker(Color.BLACK);

	public Viewer3DConfigNode()
	{
		rendererBlockSizeField = NumberField.intField(
				64,
				value -> value >= Viewer3DConfig.RENDERER_BLOCK_SIZE_MIN_VALUE && value <= Viewer3DConfig.RENDERER_BLOCK_SIZE_MAX_VALUE,
				SubmitOn.ENTER_PRESSED
			);

		final GridPane grid = new GridPane();
		grid.setVgap(5.0);
		grid.setPadding(Insets.EMPTY);

		final Label showBlockBoundariesLabel = new Label("Block outlines");
		GridPane.setHgrow(showBlockBoundariesLabel, Priority.ALWAYS);
		grid.add(showBlockBoundariesLabel, 0, 0);
		grid.add(showBlockBoundariesCheckBox, 1, 0);
		GridPane.setHalignment(showBlockBoundariesCheckBox, HPos.RIGHT);

		final Label rendererBlockSizeLabel = new Label("Renderer block size");
		GridPane.setHgrow(rendererBlockSizeLabel, Priority.ALWAYS);
		grid.add(rendererBlockSizeLabel, 0, 1);

		final TextField rendererBlockSizeText = rendererBlockSizeField.textField();
		grid.add(rendererBlockSizeText, 1, 1);
		rendererBlockSizeText.setPrefWidth(60);
		rendererBlockSizeText.setMaxWidth(Control.USE_PREF_SIZE);
		UIUtils.setNumericTextField(rendererBlockSizeText, Viewer3DConfig.RENDERER_BLOCK_SIZE_MAX_VALUE);

		final Label backgroundColorLabel = Labels.withTooltip("Background", "Set background color of 3D viewer.");
		grid.add(backgroundColorLabel, 0, 2);
		grid.add(backgroundColorPicker, 1, 2);

		GridPane.setHgrow(backgroundColorLabel, Priority.ALWAYS);
		backgroundColorPicker.setPrefWidth(PREF_CELL_WIDTH);

		contents = new TitledPane("3D Viewer", grid);
		contents.setGraphic(areMeshesEnabledCheckBox);
		contents.setExpanded(false);
		contents.collapsibleProperty().bind(areMeshesEnabledCheckBox.selectedProperty());
		contents.setPadding(Insets.EMPTY);
	}

	public void bind(final Viewer3DConfig config)
	{
		areMeshesEnabledCheckBox.selectedProperty().bindBidirectional(config.areMeshesEnabledProperty());
		showBlockBoundariesCheckBox.selectedProperty().bindBidirectional(config.showBlockBoundariesProperty());
		rendererBlockSizeField.valueProperty().bindBidirectional(config.rendererBlockSizeProperty());
		backgroundColorPicker.valueProperty().bindBidirectional(config.backgroundColorProperty());
	}

	public Node getContents()
	{
		return contents;
	}

}
