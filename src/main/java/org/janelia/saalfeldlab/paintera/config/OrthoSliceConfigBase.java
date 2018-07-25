package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;

public class OrthoSliceConfigBase
{

	private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showTopLeft = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showTopRight = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showBottomLeft = new SimpleBooleanProperty(true);

	private final SimpleLongProperty delayInNanoSeconds = new SimpleLongProperty(200);

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

	public LongProperty delayInNanoSeconds()
	{
		return this.delayInNanoSeconds;
	}

}
