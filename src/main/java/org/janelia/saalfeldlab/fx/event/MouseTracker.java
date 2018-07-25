package org.janelia.saalfeldlab.fx.event;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

public class MouseTracker implements EventHandler<MouseEvent>
{

	private boolean isDragging;

	@Override
	public void handle(final MouseEvent event)
	{
		if (event.getEventType() == MouseEvent.MOUSE_PRESSED)
		{
			this.isDragging = false;
		}
		else if (event.getEventType() == MouseEvent.DRAG_DETECTED)
		{
			this.isDragging = true;
		}
	}

	public boolean isDragging()
	{
		return this.isDragging;
	}

}
