package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

public class Viewer3DConfigNode
{

	private static final double PREF_CELL_WIDTH = 60.0;

	private final TitledPane contents;

	private final CheckBox areMeshesEnabledCheckBox = new CheckBox();

	private final CheckBox showBlockBoundariesCheckBox = new CheckBox();

	private final NumericSliderWithField rendererBlockSizeSlider;

	private final NumericSliderWithField numElementsPerFrameSlider;

	private final NumericSliderWithField frameDelayMsecSlider;

	private final NumericSliderWithField sceneUpdateDelayMsecSlider;

	private final ColorPicker backgroundColorPicker = new ColorPicker(Color.BLACK);

	public Viewer3DConfigNode()
	{
		int row = 0;

		final GridPane grid = new GridPane();
		grid.setVgap(5.0);
		grid.setPadding(Insets.EMPTY);

		// arrange the grid as 4 columns to fine-tune size and layout of the elements
		for (int i = 0; i < 3; ++i)
			grid.getColumnConstraints().add(new ColumnConstraints());
		grid.getColumnConstraints().add(new ColumnConstraints(PREF_CELL_WIDTH));

		final Label showBlockBoundariesLabel = new Label("Block outlines");
		grid.add(showBlockBoundariesLabel, 0, row);
		grid.add(showBlockBoundariesCheckBox, 1, row);
		GridPane.setColumnSpan(showBlockBoundariesCheckBox, 3);
		GridPane.setHalignment(showBlockBoundariesCheckBox, HPos.RIGHT);
		++row;

		rendererBlockSizeSlider = new NumericSliderWithField(
				Viewer3DConfig.RENDERER_BLOCK_SIZE_MIN_VALUE,
				Viewer3DConfig.RENDERER_BLOCK_SIZE_MAX_VALUE,
				Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE
			);
		grid.add(Labels.withTooltip("Renderer block size"), 0, row);
		grid.add(rendererBlockSizeSlider.slider(), 1, row);
		GridPane.setColumnSpan(rendererBlockSizeSlider.slider(), 2);
		grid.add(rendererBlockSizeSlider.textField(), 3, row);
		rendererBlockSizeSlider.slider().setShowTickLabels(false);
		rendererBlockSizeSlider.slider().setShowTickMarks(true);
		rendererBlockSizeSlider.slider().setMajorTickUnit((rendererBlockSizeSlider.slider().getMax() - rendererBlockSizeSlider.slider().getMin() + 1) / 4);
		rendererBlockSizeSlider.slider().setMinorTickCount(0);
		rendererBlockSizeSlider.slider().setTooltip(new Tooltip("Sets the length of the block side for meshes."));
		rendererBlockSizeSlider.textField().setPrefWidth(PREF_CELL_WIDTH);
		rendererBlockSizeSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(rendererBlockSizeSlider.slider(), Priority.ALWAYS);
		++row;

		numElementsPerFrameSlider = new NumericSliderWithField(
				Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_MIN_VALUE,
				Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_MAX_VALUE,
				Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE
			);
		grid.add(Labels.withTooltip("Elements per frame"), 0, row);
		grid.add(numElementsPerFrameSlider.slider(), 1, row);
		GridPane.setColumnSpan(numElementsPerFrameSlider.slider(), 2);
		grid.add(numElementsPerFrameSlider.textField(), 3, row);
		numElementsPerFrameSlider.slider().setShowTickLabels(false);
		numElementsPerFrameSlider.slider().setShowTickMarks(true);
		numElementsPerFrameSlider.slider().setMajorTickUnit((numElementsPerFrameSlider.slider().getMax() - numElementsPerFrameSlider.slider().getMin() + 1) / 4);
		numElementsPerFrameSlider.slider().setMinorTickCount(0);
		numElementsPerFrameSlider.slider().setTooltip(new Tooltip("Limits the number of mesh elements updated per frame."));
		numElementsPerFrameSlider.textField().setPrefWidth(PREF_CELL_WIDTH);
		numElementsPerFrameSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(numElementsPerFrameSlider.slider(), Priority.ALWAYS);
		++row;

		frameDelayMsecSlider = new NumericSliderWithField(
				Viewer3DConfig.FRAME_DELAY_MSEC_MIN_VALUE,
				Viewer3DConfig.FRAME_DELAY_MSEC_MAX_VALUE,
				Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE
			);
		grid.add(Labels.withTooltip("Frame delay (ms)"), 0, row);
		grid.add(frameDelayMsecSlider.slider(), 1, row);
		GridPane.setColumnSpan(frameDelayMsecSlider.slider(), 2);
		grid.add(frameDelayMsecSlider.textField(), 3, row);
		frameDelayMsecSlider.slider().setShowTickLabels(false);
		frameDelayMsecSlider.slider().setShowTickMarks(true);
		frameDelayMsecSlider.slider().setMajorTickUnit((frameDelayMsecSlider.slider().getMax() - frameDelayMsecSlider.slider().getMin() + 1) / 4);
		frameDelayMsecSlider.slider().setMinorTickCount(0);
		frameDelayMsecSlider.slider().setTooltip(new Tooltip("Delay between two consecutive frames."));
		frameDelayMsecSlider.textField().setPrefWidth(PREF_CELL_WIDTH);
		frameDelayMsecSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(frameDelayMsecSlider.slider(), Priority.ALWAYS);
		++row;

		sceneUpdateDelayMsecSlider = new NumericSliderWithField(
				Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_MIN_VALUE,
				Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_MAX_VALUE,
				Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE
			);
		grid.add(Labels.withTooltip("Update delay (ms)"), 0, row);
		grid.add(sceneUpdateDelayMsecSlider.slider(), 1, row);
		GridPane.setColumnSpan(sceneUpdateDelayMsecSlider.slider(), 2);
		grid.add(sceneUpdateDelayMsecSlider.textField(), 3, row);
		sceneUpdateDelayMsecSlider.slider().setShowTickLabels(false);
		sceneUpdateDelayMsecSlider.slider().setShowTickMarks(true);
		sceneUpdateDelayMsecSlider.slider().setMajorTickUnit((sceneUpdateDelayMsecSlider.slider().getMax() - sceneUpdateDelayMsecSlider.slider().getMin() + 1) / 4);
		sceneUpdateDelayMsecSlider.slider().setMinorTickCount(0);
		sceneUpdateDelayMsecSlider.slider().setTooltip(new Tooltip("How soon to initiate scene update after navigating."));
		sceneUpdateDelayMsecSlider.textField().setPrefWidth(PREF_CELL_WIDTH);
		sceneUpdateDelayMsecSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(sceneUpdateDelayMsecSlider.slider(), Priority.ALWAYS);
		++row;

		final Label backgroundColorLabel = Labels.withTooltip("Background", "Set background color of 3D viewer.");
		grid.add(backgroundColorLabel, 0, row);
		grid.add(backgroundColorPicker, 1, row);
		backgroundColorPicker.setPrefWidth(PREF_CELL_WIDTH);
		GridPane.setColumnSpan(backgroundColorPicker, 3);
		GridPane.setHalignment(backgroundColorPicker, HPos.RIGHT);
		++row;

		contents = new TitledPane("3D Viewer", grid);
		contents.setGraphic(areMeshesEnabledCheckBox);
		contents.setExpanded(false);
		contents.collapsibleProperty().bind(areMeshesEnabledCheckBox.selectedProperty());
		contents.setPadding(Insets.EMPTY);
	}

	public Viewer3DConfigNode(final Viewer3DConfig config) {
		this();
		bind(config);
	}

	public void bind(final Viewer3DConfig config)
	{
		areMeshesEnabledCheckBox.selectedProperty().bindBidirectional(config.areMeshesEnabledProperty());
		showBlockBoundariesCheckBox.selectedProperty().bindBidirectional(config.showBlockBoundariesProperty());
		rendererBlockSizeSlider.slider().valueProperty().bindBidirectional(config.rendererBlockSizeProperty());
		numElementsPerFrameSlider.slider().valueProperty().bindBidirectional(config.numElementsPerFrameProperty());
		frameDelayMsecSlider.slider().valueProperty().bindBidirectional(config.frameDelayMsecProperty());
		sceneUpdateDelayMsecSlider.slider().valueProperty().bindBidirectional(config.sceneUpdateDelayMsecProperty());
		backgroundColorPicker.valueProperty().bindBidirectional(config.backgroundColorProperty());
	}

	public Node getContents()
	{
		return contents;
	}

}
