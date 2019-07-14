package org.janelia.saalfeldlab.paintera.config;

import bdv.fx.viewer.scalebar.ScaleBarOverlayConfig;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;

public class ScaleBarOverlayConfigNode extends TitledPane {

	private final CheckBox isShowing = new CheckBox();

	private final NumberField<DoubleProperty> targetScaleBarLength = NumberField.doubleField(1.0, v -> true, ObjectField.SubmitOn.values());

	private final ColorPicker foregroundColorPicker = new ColorPicker();

	private final ColorPicker backgroundColorPicker = new ColorPicker();

	private final ObjectProperty<Font> font = new SimpleObjectProperty<>(new Font("SansSerif", 18.0));

	private final NumberField<DoubleProperty> fontSize = NumberField.doubleField(font.get().getSize(), v -> v > 0.0, ObjectField.SubmitOn.values());

	public ScaleBarOverlayConfigNode()
	{
		super("Scale Bar", null);
		final GridPane grid = new GridPane();
		setContent(grid);
		setGraphic(isShowing);
		setExpanded(false);
		grid.add(new Label("Scale Bar Size"), 0, 0);
		grid.add(targetScaleBarLength.textField(), 1, 0);
		grid.add(new Label("Font Size"), 0, 1);
		grid.add(fontSize.textField(), 1, 1);
		grid.add(new Label("Foreground Color"), 0, 2);
		grid.add(new Label("Background Color"), 0, 3);
		grid.add(foregroundColorPicker, 1, 2);
		grid.add(backgroundColorPicker, 1, 3);
		grid.setHgap(5);

		GridPane.setHgrow(targetScaleBarLength.textField(), Priority.ALWAYS);
		GridPane.setHgrow(fontSize.textField(), Priority.ALWAYS);
		GridPane.setHgrow(foregroundColorPicker, Priority.ALWAYS);
		GridPane.setHgrow(backgroundColorPicker, Priority.ALWAYS);

		foregroundColorPicker.setMaxWidth(Double.POSITIVE_INFINITY);
		backgroundColorPicker.setMaxWidth(Double.POSITIVE_INFINITY);

		font.addListener((obs, oldv, newv) -> fontSize.valueProperty().set(newv.getSize()));
		fontSize.valueProperty().addListener((obs, oldv, newv) -> font.set(new Font(font.get().getName(), newv.doubleValue())));
	}

	public void bindBidirectionalTo(final ScaleBarOverlayConfig config) {
		this.targetScaleBarLength.valueProperty().bindBidirectional(config.targetScaleBarLengthProperty());
		this.isShowing.selectedProperty().bindBidirectional(config.isShowingProperty());
		this.foregroundColorPicker.valueProperty().bindBidirectional(config.foregroundColorProperty());
		this.backgroundColorPicker.valueProperty().bindBidirectional(config.backgroundColorProperty());
		this.font.bindBidirectional(config.overlayFontProperty());

		this.targetScaleBarLength.valueProperty().set(config.getTargetScaleBarLength());
		this.isShowing.setSelected(config.getIsShowing());
		this.foregroundColorPicker.setValue(config.getForegroundColor());
		this.backgroundColorPicker.setValue(config.getBackgroundColor());
		this.font.set(config.getOverlayFont());
	}

}
