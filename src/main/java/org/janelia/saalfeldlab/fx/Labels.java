package org.janelia.saalfeldlab.fx;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

public class Labels
{

	public static Label withTooltip(String labelText)
	{
		return withTooltip(labelText, labelText);
	}

	public static Label withTooltip(String labelText, String tooltipText)
	{
		Label label = new Label(labelText);
		label.setTooltip(new Tooltip(tooltipText));
		return label;
	}

}
