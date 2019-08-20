package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
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
import javafx.scene.control.Tooltip;
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

	private final NumericSliderWithField numElementsPerFrameSlider;

	private final NumericSliderWithField frameDelayMsecSlider;

	private final ColorPicker backgroundColorPicker = new ColorPicker(Color.BLACK);

	public Viewer3DConfigNode()
	{
		int row = 0;

		rendererBlockSizeField = NumberField.intField(
				Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE,
				value -> value >= Viewer3DConfig.RENDERER_BLOCK_SIZE_MIN_VALUE && value <= Viewer3DConfig.RENDERER_BLOCK_SIZE_MAX_VALUE,
				SubmitOn.ENTER_PRESSED
			);

		final GridPane grid = new GridPane();
		grid.setVgap(5.0);
		grid.setPadding(Insets.EMPTY);

		final Label showBlockBoundariesLabel = new Label("Block outlines");
		GridPane.setHgrow(showBlockBoundariesLabel, Priority.ALWAYS);
		grid.add(showBlockBoundariesLabel, 0, row);
		grid.add(showBlockBoundariesCheckBox, 1, row);
		GridPane.setHalignment(showBlockBoundariesCheckBox, HPos.RIGHT);
		++row;

		final Label rendererBlockSizeLabel = new Label("Renderer block size");
		GridPane.setHgrow(rendererBlockSizeLabel, Priority.ALWAYS);
		grid.add(rendererBlockSizeLabel, 0, row);
		final TextField rendererBlockSizeText = rendererBlockSizeField.textField();
		grid.add(rendererBlockSizeText, 1, row);
		rendererBlockSizeText.setPrefWidth(60);
		rendererBlockSizeText.setMaxWidth(Control.USE_PREF_SIZE);
		UIUtils.setNumericTextField(rendererBlockSizeText, Viewer3DConfig.RENDERER_BLOCK_SIZE_MAX_VALUE);
		++row;

		numElementsPerFrameSlider = new NumericSliderWithField(
				Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_MIN_VALUE,
				Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_MAX_VALUE,
				Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE
			);
		grid.add(Labels.withTooltip("Elements per frame"), 0, row);
		grid.add(numElementsPerFrameSlider.slider(), 1, row);
		grid.add(numElementsPerFrameSlider.textField(), 2, row);
		numElementsPerFrameSlider.slider().setShowTickLabels(true);
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
		grid.add(frameDelayMsecSlider.textField(), 2, row);
		frameDelayMsecSlider.slider().setShowTickLabels(true);
		frameDelayMsecSlider.slider().setTooltip(new Tooltip("Delay between two consecutive frames."));
		frameDelayMsecSlider.textField().setPrefWidth(PREF_CELL_WIDTH);
		frameDelayMsecSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(frameDelayMsecSlider.slider(), Priority.ALWAYS);
		++row;

		final Label backgroundColorLabel = Labels.withTooltip("Background", "Set background color of 3D viewer.");
		grid.add(backgroundColorLabel, 0, row);
		grid.add(backgroundColorPicker, 1, row);
		++row;

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
		numElementsPerFrameSlider.slider().valueProperty().bindBidirectional(config.numElementsPerFrameProperty());
		frameDelayMsecSlider.slider().valueProperty().bindBidirectional(config.frameDelayMsecProperty());
		backgroundColorPicker.valueProperty().bindBidirectional(config.backgroundColorProperty());
	}

	public Node getContents()
	{
		return contents;
	}

}
