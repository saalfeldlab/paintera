package org.janelia.saalfeldlab.fx.ui;

import java.util.stream.Stream;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class NamedNode
{

	private final Label name;

	private final HBox contents;

	private NamedNode(String name, double nameWidth, boolean growNodes, Node... nodes)
	{
		this.name = new Label(name);
		this.contents = new HBox(this.name);
		this.contents.getChildren().addAll(nodes);
		if (growNodes)
			Stream.of(nodes).forEach( n -> HBox.setHgrow(n, Priority.ALWAYS));
		this.name.setPrefWidth(nameWidth);
		this.name.setMinWidth(nameWidth);
		this.name.setMaxWidth(nameWidth);
	}

	public void addNameToolTip(final Tooltip tooltip)
	{
		this.name.setTooltip(tooltip);
	}

	public static Node nameIt(String name, double nameWidth, boolean growNodes, Node... nodes)
	{
		return new NamedNode(name, nameWidth, growNodes, nodes).contents;
	}

	public static Node bufferNode(Node n)
	{
		HBox.setHgrow(n, Priority.ALWAYS);
		return n;
	}
}
