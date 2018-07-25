package org.janelia.saalfeldlab.paintera.serialization.fx;

import java.lang.reflect.Type;
import java.util.Optional;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import javafx.beans.property.SimpleIntegerProperty;

public class SimpleIntegerPropertySerializer implements
                                             JsonSerializer<SimpleIntegerProperty>,
                                             JsonDeserializer<SimpleIntegerProperty>
{

	@Override
	public SimpleIntegerProperty deserialize(final JsonElement json, final Type typeOfT, final
	JsonDeserializationContext context)
	throws JsonParseException
	{
		return new SimpleIntegerProperty(Optional.ofNullable((Integer) context.deserialize(json, Integer.class))
				.orElse(
				0));
	}

	@Override
	public JsonElement serialize(final SimpleIntegerProperty src, final Type typeOfSrc, final JsonSerializationContext context)
	{
		return new JsonPrimitive(src.get());
	}

}
