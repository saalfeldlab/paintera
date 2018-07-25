package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.janelia.saalfeldlab.util.Colors;

public class CrosshairConfigNode
{

	private final TitledPane contents;

	private final CheckBox showCrosshairs = new CheckBox();

	private final ColorPicker onFocusColorPicker = new ColorPicker();

	private final ColorPicker outOfFocusColorPicker = new ColorPicker();

	public CrosshairConfigNode()
	{
		super();

		final GridPane grid = new GridPane();

		onFocusColorPicker.setMaxWidth(40);
		onFocusColorPicker.getCustomColors().addAll(Colors.cremi(1.0), Colors.cremi(0.5));

		outOfFocusColorPicker.setMaxWidth(40);
		outOfFocusColorPicker.getCustomColors().addAll(Colors.cremi(1.0), Colors.cremi(0.5));

		final Label onFocusLabel  = new Label("on focus");
		final Label offFocusLabel = new Label("off focus");
		grid.add(onFocusLabel, 0, 1);
		grid.add(offFocusLabel, 0, 2);

		grid.add(onFocusColorPicker, 1, 1);
		grid.add(outOfFocusColorPicker, 1, 2);

		GridPane.setHgrow(onFocusLabel, Priority.ALWAYS);
		GridPane.setHgrow(offFocusLabel, Priority.ALWAYS);

		contents = new TitledPane("Crosshair", grid);
		contents.setGraphic(showCrosshairs);
		contents.setExpanded(false);

	}

	public void bind(final CrosshairConfig config)
	{
		showCrosshairs.selectedProperty().bindBidirectional(config.showCrosshairsProperty());
		onFocusColorPicker.valueProperty().bindBidirectional(config.onFocusColorProperty());
		outOfFocusColorPicker.valueProperty().bindBidirectional(config.outOfFocusColorProperty());
	}

	public Node getContents()
	{
		return contents;
	}

}
