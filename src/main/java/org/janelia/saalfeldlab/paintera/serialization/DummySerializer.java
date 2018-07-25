package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class DummySerializer implements JsonSerializer<Object>
{

	@Override
	public JsonElement serialize(final Object src, final Type typeOfSrc, final JsonSerializationContext context)
	{
		System.out.println("DOING SHIT YAW!");
		return context.serialize(src.toString());
	}

}
