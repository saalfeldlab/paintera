package org.janelia.saalfeldlab.paintera.serialization.converter;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class HighlightingStreamConverterSerializer implements
		JsonSerializer< HighlightingStreamConverter< ? > >, JsonDeserializer< HighlightingStreamConverter< ? > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final String TYPE_KEY = "converterType";

	public static final String STREAM_TYPE_KEY = "streamType";

	@Override
	public HighlightingStreamConverter< ? > deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
	{
		try
		{
			final JsonObject map = json.getAsJsonObject();
			@SuppressWarnings( "unchecked" )
			final Class< ? extends AbstractHighlightingARGBStream > streamClass =
					( Class< ? extends AbstractHighlightingARGBStream > ) Class.forName( map.get( STREAM_TYPE_KEY ).getAsString() );
			final AbstractHighlightingARGBStream stream = streamClass.newInstance();
			@SuppressWarnings( "unchecked" )
			final Class< ? extends HighlightingStreamConverter< ? > > converterClass =
					( Class< ? extends HighlightingStreamConverter< ? > > ) Class.forName( map.get( TYPE_KEY ).getAsString() );
			return converterClass.getConstructor( AbstractHighlightingARGBStream.class ).newInstance( stream );
		}
		catch ( InstantiationException
				| IllegalAccessException
				| IllegalArgumentException
				| InvocationTargetException
				| NoSuchMethodException
				| SecurityException
				| ClassNotFoundException e )
		{
			throw new JsonParseException( e );
		}
	}

	@Override
	public JsonElement serialize(
			final HighlightingStreamConverter< ? > src,
			final Type typeOfSrc,
			final JsonSerializationContext context )
	{
		final JsonObject map = new JsonObject();
		final AbstractHighlightingARGBStream stream = src.getStream();
		map.addProperty( TYPE_KEY, src.getClass().getName() );
		map.addProperty( STREAM_TYPE_KEY, stream.getClass().getName() );

		LOG.debug( "Returning serialized converter as {}", map );

		return map;
	}

}
