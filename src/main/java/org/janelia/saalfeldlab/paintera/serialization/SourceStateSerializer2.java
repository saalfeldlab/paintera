package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class SourceStateSerializer2 implements JsonDeserializer< SourceStateWithIndexedDependencies< ?, ? > >
{

	@Override
	public SourceStateWithIndexedDependencies< ?, ? > deserialize( JsonElement arg0, Type arg1, JsonDeserializationContext arg2 ) throws JsonParseException
	{
		// TODO Auto-generated method stub
		return null;
	}



}
