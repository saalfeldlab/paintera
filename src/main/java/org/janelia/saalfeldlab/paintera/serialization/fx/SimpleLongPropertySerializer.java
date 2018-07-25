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
import javafx.beans.property.SimpleLongProperty;

public class SimpleLongPropertySerializer implements
                                          JsonSerializer<SimpleLongProperty>,
                                          JsonDeserializer<SimpleLongProperty>
{

	@Override
	public SimpleLongProperty deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		return new SimpleLongProperty(Optional.ofNullable((Long) context.deserialize(json, Long.class)).orElse(0l));
	}

	@Override
	public JsonElement serialize(final SimpleLongProperty src, final Type typeOfSrc, final JsonSerializationContext context)
	{
		return new JsonPrimitive(src.get());
	}

}
