package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class OrthoSliceConfigBase
{

	private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showTopLeft = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showTopRight = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showBottomLeft = new SimpleBooleanProperty(true);

	private final SimpleDoubleProperty opacity = new SimpleDoubleProperty(1.0);

	public BooleanProperty isEnabledProperty()
	{
		return this.enabled;
	}

	public BooleanProperty showTopLeftProperty()
	{
		return this.showTopLeft;
	}

	public BooleanProperty showTopRightProperty()
	{
		return this.showTopRight;
	}

	public BooleanProperty showBottomLeftProperty()
	{
		return this.showBottomLeft;
	}

	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}
}
