package org.janelia.saalfeldlab.fx.ui;

import java.util.Arrays;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class ExceptionNode
{

	private final Exception e;

	public ExceptionNode(final Exception e)
	{
		super();
		this.e = e;
	}

	public Pane getPane()
	{
		final AnchorPane typePane    = leftRight(new Label("Type"), new Label(e.getClass().getName()));
		final AnchorPane messagePane = leftRight(new Label("Message"), new Label(e.getMessage()));
		final TitledPane stackTrace  = new TitledPane("Stack Trace", new ScrollPane(fromStackTrace(e)));
		stackTrace.setExpanded(false);
		final VBox contents = new VBox(typePane, messagePane, stackTrace);

		return contents;
	}

	public static AnchorPane leftRight(final Node left, final Node right)
	{
		return leftRight(left, right, 0.0, 0.0);
	}

	public static AnchorPane leftRight(final Node left, final Node right, final double leftDistance, final double
			rightDistance)
	{
		final AnchorPane pane = new AnchorPane(left, right);
		AnchorPane.setLeftAnchor(left, leftDistance);
		AnchorPane.setRightAnchor(right, rightDistance);
		return pane;
	}

	public static Node fromStackTrace(final Exception e)
	{

		final TextArea label = new TextArea(
				String.join("\n", Arrays
						.stream(e.getStackTrace())
						.map(StackTraceElement::toString)
						.toArray(String[]::new)));
		label.setEditable(false);
		return label;

	}

	public static Dialog<Exception> exceptionDialog(final Exception e)
	{
		final Dialog<Exception> d      = new Dialog<>();
		final ExceptionNode     notify = new ExceptionNode(e);
		d.setTitle("Caught Exception");
		d.getDialogPane().setGraphic(notify.getPane());
		d.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
		d.setResizable(true);
		return d;
	}

}
