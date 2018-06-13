package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.paintera.control.Navigation;
import org.janelia.saalfeldlab.paintera.control.navigation.ButtonRotationSpeedConfig;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class NavigationConfig
{

	private final SimpleBooleanProperty allowRotations = new SimpleBooleanProperty( true );

	private final ButtonRotationSpeedConfig buttonRotationSpeeds = new ButtonRotationSpeedConfig();

	public BooleanProperty allowRotationsProperty()
	{
		return this.allowRotations;
	}

	public void bindNavigationToConfig( final Navigation navigation )
	{
		navigation.allowRotationsProperty().bind( this.allowRotations );
	}

	public ButtonRotationSpeedConfig buttonRotationSpeeds()
	{
		return this.buttonRotationSpeeds;
	}

}
