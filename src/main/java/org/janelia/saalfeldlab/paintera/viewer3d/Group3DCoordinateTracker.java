package org.janelia.saalfeldlab.paintera.viewer3d;

import java.lang.invoke.MethodHandles;

import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Group3DCoordinateTracker
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Group group;

	private final double[] coordinate = new double[3];

	private boolean isValidCoordinate = false;

	public Group3DCoordinateTracker(final Group group)
	{
		super();
		this.group = group;
		this.group.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> isValidCoordinate = true);
		this.group.addEventFilter(MouseEvent.MOUSE_EXITED, e -> isValidCoordinate = false);
		this.group.addEventFilter(MouseEvent.MOUSE_MOVED, this::setCoordinate);
	}

	private void setCoordinate(final MouseEvent e)
	{
		LOG.trace("Updating coordinate {} with event {}", coordinate, e);
		coordinate[0] = e.getX();
		coordinate[1] = e.getY();
		coordinate[2] = e.getZ();
		LOG.trace("Updated coordinate {}", coordinate);
	}

	public boolean isValidCoordinate()
	{
		return this.isValidCoordinate;
	}

	public double[] getCoordinate()
	{
		return this.coordinate.clone();
	}

}
