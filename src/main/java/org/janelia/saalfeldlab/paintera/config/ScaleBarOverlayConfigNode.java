package org.janelia.saalfeldlab.paintera.config;

import bdv.fx.viewer.scalebar.ScaleBarOverlayConfig;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;

public class ScaleBarOverlayConfigNode extends TitledPane {

	private final CheckBox isShowing = new CheckBox();

	private final NumberField<DoubleProperty> targetScaleBarLength = NumberField.doubleField(1.0, v -> true, ObjectField.SubmitOn.values());

	private final ColorPicker foregroundColorPicker = new ColorPicker();

	private final ColorPicker backgroundColorPicker = new ColorPicker();

	public ScaleBarOverlayConfigNode()
	{
		super("Scale Bar", null);
		final GridPane grid = new GridPane();
		setContent(grid);
		setGraphic(isShowing);
		setExpanded(false);
		grid.add(new Label("Scale Bar Size"), 0, 0);
		grid.add(targetScaleBarLength.textField(), 1, 0);
		grid.add(new Label("Foreground Color"), 0, 1);
		grid.add(new Label("Background Color"), 0, 2);
		grid.add(foregroundColorPicker, 1, 1);
		grid.add(backgroundColorPicker, 1, 2);
		grid.setHgap(5);

		GridPane.setHgrow(targetScaleBarLength.textField(), Priority.ALWAYS);
		GridPane.setHgrow(foregroundColorPicker, Priority.ALWAYS);
		GridPane.setHgrow(backgroundColorPicker, Priority.ALWAYS);

		foregroundColorPicker.setMaxWidth(Double.POSITIVE_INFINITY);
		backgroundColorPicker.setMaxWidth(Double.POSITIVE_INFINITY);
	}

	public void bindBidirectionalTo(final ScaleBarOverlayConfig config) {
		this.targetScaleBarLength.valueProperty().bindBidirectional(config.targetScaleBarLengthProperty());
		this.isShowing.selectedProperty().bindBidirectional(config.isShowingProperty());
		this.foregroundColorPicker.valueProperty().bindBidirectional(config.foregroundColorProperty());
		this.backgroundColorPicker.valueProperty().bindBidirectional(config.backgroundColorProperty());

		this.targetScaleBarLength.valueProperty().set(config.getTargetScaleBarLength());
		this.isShowing.setSelected(config.getIsShowing());
	}

}
