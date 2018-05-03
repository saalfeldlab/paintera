package org.janelia.saalfeldlab.paintera.serialization;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableBooleanValue;

public final class WindowProperties
{
	private final IntegerProperty initialWidth = new SimpleIntegerProperty( 800 );

	private final IntegerProperty initialHeight = new SimpleIntegerProperty( 600 );

	public final IntegerProperty widthProperty = new SimpleIntegerProperty( initialWidth.get() );

	public final IntegerProperty heightProperty = new SimpleIntegerProperty( initialWidth.get() );

	public ObservableBooleanValue hasChanged = widthProperty
			.isNotEqualTo( initialWidth )
			.or( heightProperty.isNotEqualTo( initialHeight ) );

	public void clean()
	{
		initialWidth.set( widthProperty.get() );
		initialHeight.set( heightProperty.get() );
	}

}
