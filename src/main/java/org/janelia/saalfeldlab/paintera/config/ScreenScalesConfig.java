package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class ScreenScalesConfig {

	private final ObjectProperty<double[]> screenScales = new SimpleObjectProperty<>();

	public ScreenScalesConfig(final double[] initialScales)
	{
		this.screenScales.set(initialScales.clone());
	}

	public ObjectProperty<double[]> screenScalersProperty()
	{
		return this.screenScales;
	}

	public void set(final ScreenScalesConfig that)
	{
		this.screenScales.set(that.screenScales.get());
	}

}
