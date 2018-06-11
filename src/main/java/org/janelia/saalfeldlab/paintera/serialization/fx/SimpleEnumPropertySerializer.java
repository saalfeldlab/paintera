package org.janelia.saalfeldlab.paintera.serialization.fx;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import javafx.beans.property.SimpleObjectProperty;

public class SimpleEnumPropertySerializer< E > implements
		JsonSerializer< SimpleObjectProperty< E > >,
		JsonDeserializer< SimpleObjectProperty< E > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String CONTENT_TYPE_KEY = "type";

	private static final String CONTENT_DATA_KEY = "data";

	@Override
	public SimpleObjectProperty< E > deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
	{
		try
		{
			LOG.debug( "Deserializing {}", json );
			final Class< E > clazz = ( Class< E > ) Class.forName( json.getAsJsonObject().get( CONTENT_TYPE_KEY ).getAsString() );
			LOG.debug( "Got class {}", clazz );
			return new SimpleObjectProperty<>( context.deserialize( json.getAsJsonObject().get( CONTENT_DATA_KEY ), clazz ) );
		}
		catch ( final ClassNotFoundException e )
		{
			throw new JsonParseException( e );
		}
	}

	@Override
	public JsonElement serialize( final SimpleObjectProperty< E > src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final JsonObject map = new JsonObject();
		map.addProperty( CONTENT_TYPE_KEY, src.get().getClass().getName() );
		map.add( CONTENT_DATA_KEY, context.serialize( src.get() ) );
		return map;
	}

}
