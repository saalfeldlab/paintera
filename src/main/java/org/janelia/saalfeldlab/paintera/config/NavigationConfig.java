package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.janelia.saalfeldlab.paintera.control.Navigation;
import org.janelia.saalfeldlab.paintera.control.navigation.ButtonRotationSpeedConfig;

public class NavigationConfig
{

	private final SimpleBooleanProperty allowRotations = new SimpleBooleanProperty(true);

	private final ButtonRotationSpeedConfig buttonRotationSpeeds = new ButtonRotationSpeedConfig();

	public BooleanProperty allowRotationsProperty()
	{
		return this.allowRotations;
	}

	public void bindNavigationToConfig(final Navigation navigation)
	{
		navigation.allowRotationsProperty().bind(this.allowRotations);
		navigation.bindTo(this.buttonRotationSpeeds);
	}

	public ButtonRotationSpeedConfig buttonRotationSpeeds()
	{
		return this.buttonRotationSpeeds;
	}

	public void set(final NavigationConfig that)
	{
		this.allowRotations.set(that.allowRotations.get());
		this.buttonRotationSpeeds.slow.set(that.buttonRotationSpeeds.slow.get());
		this.buttonRotationSpeeds.fast.set(that.buttonRotationSpeeds.fast.get());
		this.buttonRotationSpeeds.regular.set(that.buttonRotationSpeeds.regular.get());
	}

}
