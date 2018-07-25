package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.janelia.saalfeldlab.fx.ui.LongField;

public class OrthoSliceConfigNode
{

	private static final long MIN_DELAY = 5;

	private final TitledPane contents;

	private final CheckBox topLeftCheckBox = new CheckBox();

	private final CheckBox topRightCheckBox = new CheckBox();

	private final CheckBox bottomLeftCheckBox = new CheckBox();

	private final CheckBox showOrthoViews = new CheckBox();

	private final LongField delay = new LongField(200);

	public OrthoSliceConfigNode()
	{
		super();

		final GridPane grid = new GridPane();

		final Label topLeftLabel    = new Label("top left");
		final Label topRightLabel   = new Label("top right");
		final Label bottomLeftLabel = new Label("bottom left");
		final Label delayLabel      = new Label("delay in ms");

		grid.add(topLeftLabel, 0, 0);
		grid.add(topRightLabel, 0, 1);
		grid.add(bottomLeftLabel, 0, 2);
		grid.add(delayLabel, 0, 3);

		grid.add(topLeftCheckBox, 1, 0);
		grid.add(topRightCheckBox, 1, 1);
		grid.add(bottomLeftCheckBox, 1, 2);
		grid.add(delay.textField(), 1, 3);
		delay.textField().setMinWidth(50);
		delay.textField().setMaxWidth(50);
		delay.valueProperty().addListener((oldv, obs, newv) -> {
			if (newv.longValue() < MIN_DELAY)
			{
				delay.valueProperty().set(MIN_DELAY);
			}
		});

		GridPane.setHgrow(topLeftLabel, Priority.ALWAYS);
		GridPane.setHgrow(topRightLabel, Priority.ALWAYS);
		GridPane.setHgrow(bottomLeftLabel, Priority.ALWAYS);
		GridPane.setHgrow(delayLabel, Priority.ALWAYS);

		contents = new TitledPane("Ortho-Views", grid);
		contents.setGraphic(showOrthoViews);
		contents.setExpanded(false);

	}

	public void bind(final OrthoSliceConfig config)
	{
		showOrthoViews.selectedProperty().bindBidirectional(config.enableProperty());
		topLeftCheckBox.selectedProperty().bindBidirectional(config.showTopLeftProperty());
		topRightCheckBox.selectedProperty().bindBidirectional(config.showTopRightProperty());
		bottomLeftCheckBox.selectedProperty().bindBidirectional(config.showBottomLeftProperty());
		delay.valueProperty().bindBidirectional(config.delayInNanoSeconds());
	}

	public Node getContents()
	{
		return contents;
	}

}
