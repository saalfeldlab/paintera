package org.janelia.saalfeldlab.fx.event;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class MouseTracker implements EventHandler<MouseEvent>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private boolean isDragging;

	private double x;

	private double y;

	public double getX()
	{
		return x;
	}

	public double getY()
	{
		return y;
	}

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
		x = event.getX();
		y = event.getY();
		LOG.trace("Updated x={} y={}", x, y);
	}

	public boolean isDragging()
	{
		return this.isDragging;
	}

}
