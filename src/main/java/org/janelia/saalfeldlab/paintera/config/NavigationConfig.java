package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.paintera.control.Navigation;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class NavigationConfig
{

	private final BooleanProperty allowRotations = new SimpleBooleanProperty( true );

	public BooleanProperty allowRotationsProperty()
	{
		return this.allowRotations;
	}

	public void bindNavigationToConfig( final Navigation navigation )
	{
		navigation.allowRotationsProperty().bind( this.allowRotations );
	}

}
