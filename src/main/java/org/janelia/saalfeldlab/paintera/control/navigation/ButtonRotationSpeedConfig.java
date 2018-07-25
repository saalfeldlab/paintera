package org.janelia.saalfeldlab.paintera.control.navigation;

import javafx.beans.property.SimpleDoubleProperty;

public class ButtonRotationSpeedConfig
{

	private static final double DEFAULT_SLOW = 0.5;

	private static final double DEFAULT_REGULAR = 5.0;

	private static final double DEFAULT_FAST = 45.0;

	public SimpleDoubleProperty slow = new SimpleDoubleProperty(DEFAULT_SLOW);

	public SimpleDoubleProperty regular = new SimpleDoubleProperty(DEFAULT_REGULAR);

	public SimpleDoubleProperty fast = new SimpleDoubleProperty(DEFAULT_FAST);

}
