package org.janelia.saalfeldlab.fx;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;

import java.util.Optional;

public class Menus {

	public static MenuItem menuItem(
			String text,
			EventHandler<ActionEvent> handler)
	{
		return menuItem(text, Optional.of(handler));
	}

	public static MenuItem menuItem(
			String text,
			Optional<EventHandler<ActionEvent>> handler
	)
	{
		MenuItem mi = new MenuItem(text);
		handler.ifPresent(mi::setOnAction);
		return mi;
	}

	public static MenuItem disabledItem(String title)
	{
		MenuItem item = new MenuItem(title);
		item.setDisable(true);
		return item;
	}

}
