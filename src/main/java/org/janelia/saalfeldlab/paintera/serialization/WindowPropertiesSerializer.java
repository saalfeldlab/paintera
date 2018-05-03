package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;
import java.util.Optional;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class WindowPropertiesSerializer implements JsonSerializer< WindowProperties >, JsonDeserializer< WindowProperties >
{
	private static final String WIDTH_KEY = "width";

	private static final String HEIGHT_KEY = "height";

	@Override
	public WindowProperties deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
	{
		final JsonObject obj = json.getAsJsonObject();
		final WindowProperties properties = new WindowProperties();
		Optional.ofNullable( obj.get( WIDTH_KEY ) ).map( JsonElement::getAsInt ).ifPresent( properties.widthProperty::set );
		Optional.ofNullable( obj.get( HEIGHT_KEY ) ).map( JsonElement::getAsInt ).ifPresent( properties.heightProperty::set );
		properties.clean();
		return properties;
	}

	@Override
	public JsonElement serialize( final WindowProperties src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final JsonObject obj = new JsonObject();
		obj.addProperty( WIDTH_KEY, src.widthProperty.get() );
		obj.addProperty( HEIGHT_KEY, src.heightProperty.get() );
		return obj;
	}

}
