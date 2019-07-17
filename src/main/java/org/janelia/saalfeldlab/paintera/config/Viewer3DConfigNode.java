package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class Viewer3DConfigNode
{

	private final TitledPane contents;

	private final CheckBox areMeshesEnabledCheckBox = new CheckBox();

	private final CheckBox showBlockBoundariesCheckBox = new CheckBox();

	public Viewer3DConfigNode()
	{
		final GridPane grid = new GridPane();

		final Label showBlockBoundariesLabel = new Label("Block outlines");

		grid.add(showBlockBoundariesLabel, 0, 0);
		grid.add(showBlockBoundariesCheckBox, 1, 0);

		GridPane.setHgrow(showBlockBoundariesLabel, Priority.ALWAYS);

		contents = new TitledPane("3D Viewer", grid);
		contents.setGraphic(areMeshesEnabledCheckBox);
		contents.setExpanded(false);
		contents.collapsibleProperty().bind(areMeshesEnabledCheckBox.selectedProperty());
	}

	public void bind(final Viewer3DConfig config)
	{
		areMeshesEnabledCheckBox.selectedProperty().bindBidirectional(config.areMeshesEnabledProperty());
		showBlockBoundariesCheckBox.selectedProperty().bindBidirectional(config.showBlockBoundariesProperty());
	}

	public Node getContents()
	{
		return contents;
	}

}
