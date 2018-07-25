package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.ui.DoubleField;

public class NavigationConfigNode
{

	private final TitledPane contents = new TitledPane("Navigation", null);

	private final CheckBox allowRotationsCheckBox = new CheckBox();

	private final DoubleField keyRotationRegular = new DoubleField(0.0);

	private final DoubleField keyRotationFast = new DoubleField(0.0);

	private final DoubleField keyRotationSlow = new DoubleField(0.0);

	private final CoordinateConfigNode coordinateConfig;

	public NavigationConfigNode()
	{
		this.coordinateConfig = new CoordinateConfigNode();
		final VBox vbox = new VBox();

		vbox.getChildren().add(this.coordinateConfig.getContents());
		vbox.getChildren().add(rotationsConfig());

		contents.setContent(vbox);
		contents.setExpanded(false);

	}

	public void bind(final NavigationConfig config)
	{
		allowRotationsCheckBox.selectedProperty().bindBidirectional(config.allowRotationsProperty());
		keyRotationRegular.valueProperty().bindBidirectional(config.buttonRotationSpeeds().regular);
		keyRotationSlow.valueProperty().bindBidirectional(config.buttonRotationSpeeds().slow);
		keyRotationFast.valueProperty().bindBidirectional(config.buttonRotationSpeeds().fast);
	}

	public Node getContents()
	{
		return contents;
	}

	public CoordinateConfigNode coordinateConfigNode()
	{
		return this.coordinateConfig;
	}

	private Node rotationsConfig()
	{
		final VBox       contents  = new VBox();
		final TitledPane rotations = new TitledPane("Rotations", contents);
		rotations.setExpanded(false);
		rotations.setGraphic(allowRotationsCheckBox);
		rotations.collapsibleProperty().bind(allowRotationsCheckBox.selectedProperty());

		{
			final GridPane   grid         = new GridPane();
			final TitledPane keyRotations = new TitledPane("Key Rotation Speeds", grid);
			keyRotations.setExpanded(false);
			int          row             = 0;
			final double doubleFieldWith = 60;

			{
				final Label label = new Label("Slow");
				GridPane.setHgrow(label, Priority.ALWAYS);
				keyRotationSlow.textField().setMaxWidth(doubleFieldWith);
				grid.add(label, 0, row);
				grid.add(keyRotationSlow.textField(), 1, row);
				++row;
			}

			{
				final Label label = new Label("Regular");
				GridPane.setHgrow(label, Priority.ALWAYS);
				keyRotationRegular.textField().setMaxWidth(doubleFieldWith);
				grid.add(label, 0, row);
				grid.add(keyRotationRegular.textField(), 1, row);
				++row;
			}

			{
				final Label label = new Label("Fast");
				GridPane.setHgrow(label, Priority.ALWAYS);
				keyRotationFast.textField().setMaxWidth(doubleFieldWith);
				grid.add(label, 0, row);
				grid.add(keyRotationFast.textField(), 1, row);
				++row;
			}
			contents.getChildren().add(keyRotations);
		}
		return rotations;
	}

}
