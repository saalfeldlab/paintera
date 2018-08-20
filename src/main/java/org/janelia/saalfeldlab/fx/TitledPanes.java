package org.janelia.saalfeldlab.fx;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;

public class TitledPanes
{

	public static TitledPane createCollapsed(String title, Node contents)
	{
		final TitledPane tp = new TitledPane(title, contents);
		tp.setExpanded(false);
		return tp;
	}

}
