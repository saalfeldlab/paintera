package org.janelia.saalfeldlab.paintera.serialization;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableBooleanValue;

public final class WindowProperties
{
	private final IntegerProperty initialWidth = new SimpleIntegerProperty(800);

	private final IntegerProperty initialHeight = new SimpleIntegerProperty(600);

	public final IntegerProperty widthProperty = new SimpleIntegerProperty(initialWidth.get());

	public final IntegerProperty heightProperty = new SimpleIntegerProperty(initialWidth.get());

	public ObservableBooleanValue hasChanged = widthProperty
			.isNotEqualTo(initialWidth)
			.or(heightProperty.isNotEqualTo(initialHeight));

	public void clean()
	{
		initialWidth.set(widthProperty.get());
		initialHeight.set(heightProperty.get());
	}

	public void populate(JsonObject serializedWindowProperties)
	{
		Optional.ofNullable(serializedWindowProperties.get(WindowPropertiesSerializer.WIDTH_KEY)).map
				(JsonElement::getAsInt).ifPresent(
				widthProperty::set);
		Optional.ofNullable(serializedWindowProperties.get(WindowPropertiesSerializer.HEIGHT_KEY)).map
				(JsonElement::getAsInt).ifPresent(
				heightProperty::set);
		clean();
	}

}
