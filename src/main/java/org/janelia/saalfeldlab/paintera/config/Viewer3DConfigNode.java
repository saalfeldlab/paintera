package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TitledPane;

public class Viewer3DConfigNode
{

	private final TitledPane contents = new TitledPane("3D Viewer", null);

	private final CheckBox areMeshesEnabledCheckBox = new CheckBox();

	public Viewer3DConfigNode()
	{
		contents.setGraphic(areMeshesEnabledCheckBox);
		contents.setExpanded(false);
		contents.collapsibleProperty().bind(areMeshesEnabledCheckBox.selectedProperty());
	}

	public void bind(final Viewer3DConfig config)
	{
		areMeshesEnabledCheckBox.selectedProperty().bindBidirectional(config.areMeshesenabledProperty());
	}

	public Node getContents()
	{
		return contents;
	}

}
