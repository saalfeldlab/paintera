package org.janelia.saalfeldlab.fx.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * @author Philipp Hanslovsky
 */
public class SingleChildStackPane extends StackPane
{
	public void setChild(final Node child)
	{
		if (child == null)
			super.getChildren().clear();
		else
			super.getChildren().setAll(child);
	}

	@Override
	public ObservableList<Node> getChildren()
	{
		return FXCollections.unmodifiableObservableList(super.getChildren());
	}

}
