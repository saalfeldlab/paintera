package org.janelia.saalfeldlab.fx;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

public class Buttons {



	public static Button withTooltip(String labelText, EventHandler<ActionEvent> handler)
	{
		return withTooltip(labelText, labelText, handler);
	}

	public static Button withTooltip(
			String labelText,
			String tooltipText,
			EventHandler<ActionEvent> handler)
	{
		Button button = new Button(labelText);
		button.setTooltip(new Tooltip(tooltipText));
		button.setOnAction(handler);
		return button;
	}

}
