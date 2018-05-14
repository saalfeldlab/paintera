package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class HighlightingStreamConverterSerializer implements JsonSerializer< HighlightingStreamConverter< ? > >, JsonDeserializer< HighlightingStreamConverter< ? > >
{

	@Override
	public HighlightingStreamConverter< ? > deserialize( final JsonElement arg0, final Type arg1, final JsonDeserializationContext arg2 ) throws JsonParseException
	{
		return null;
	}

	@Override
	public JsonElement serialize( final HighlightingStreamConverter< ? > arg0, final Type arg1, final JsonSerializationContext arg2 )
	{
		return new JsonObject();
	}

}
