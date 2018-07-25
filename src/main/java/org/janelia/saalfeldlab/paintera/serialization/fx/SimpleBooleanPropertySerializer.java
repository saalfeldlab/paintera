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
import javafx.beans.property.SimpleBooleanProperty;

public class SimpleBooleanPropertySerializer implements
                                             JsonSerializer<SimpleBooleanProperty>,
                                             JsonDeserializer<SimpleBooleanProperty>
{

	@Override
	public SimpleBooleanProperty deserialize(final JsonElement json, final Type typeOfT, final
	JsonDeserializationContext context)
	throws JsonParseException
	{
		return new SimpleBooleanProperty(Optional.ofNullable((Boolean) context.deserialize(json, boolean.class))
				.orElse(
				false));
	}

	@Override
	public JsonElement serialize(final SimpleBooleanProperty src, final Type typeOfSrc, final JsonSerializationContext context)
	{
		return new JsonPrimitive(src.get());
	}

}
