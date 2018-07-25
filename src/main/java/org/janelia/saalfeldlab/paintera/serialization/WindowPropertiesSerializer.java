package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class WindowPropertiesSerializer implements JsonSerializer<WindowProperties>
{
	public static final String WIDTH_KEY = "width";

	public static final String HEIGHT_KEY = "height";

	@Override
	public JsonElement serialize(final WindowProperties src, final Type typeOfSrc, final JsonSerializationContext
			context)
	{
		final JsonObject obj = new JsonObject();
		obj.addProperty(WIDTH_KEY, src.widthProperty.get());
		obj.addProperty(HEIGHT_KEY, src.heightProperty.get());
		return obj;
	}

}
