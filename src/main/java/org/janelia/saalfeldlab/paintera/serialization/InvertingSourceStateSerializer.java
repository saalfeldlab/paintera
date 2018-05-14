package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.IntFunction;

import org.janelia.saalfeldlab.paintera.state.InvertingRawSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class InvertingSourceStateSerializer implements JsonSerializer< InvertingRawSourceState< ?, ? > >, JsonDeserializer< InvertingRawSourceState< ?, ? > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final String NAME_KEY = "name";

	private final IntFunction< SourceState< ?, ? > > dependsOn;

	public InvertingSourceStateSerializer( final IntFunction< SourceState< ?, ? > > dependsOn )
	{
		super();
		this.dependsOn = dependsOn;
	}

	@Override
	public JsonObject serialize( final InvertingRawSourceState< ?, ? > state, final Type type, final JsonSerializationContext context )
	{
		final JsonObject map = new JsonObject();
		map.addProperty( NAME_KEY, state.nameProperty().get() );
		return map;
	}

	@SuppressWarnings( "unchecked" )
	@Override
	public InvertingRawSourceState< ?, ? > deserialize( final JsonElement el, final Type type, final JsonDeserializationContext context ) throws JsonParseException
	{
		final JsonObject map = el.getAsJsonObject();
		LOG.warn( "Deserializing {}", map );
		final int[] dependsOn = context.deserialize( map.get( SourceStateSerializer.DEPENDS_ON_KEY ), int[].class );

		if ( dependsOn.length != 1 ) {
			throw new JsonParseException( "Expected exactly one dependency, got: " + map.get( SourceStateSerializer.DEPENDS_ON_KEY ) );
		}

		final SourceState< ?, ? > dependsOnState = this.dependsOn.apply( dependsOn[ 0 ] );

		if ( !(dependsOnState instanceof RawSourceState< ?, ? >) ) {
			throw new JsonParseException( "Expected " + RawSourceState.class.getName() + " as dependency but got " + dependsOnState.getClass().getName() + " instead." );
		}

		final InvertingRawSourceState< ?, ? > state = new InvertingRawSourceState<>(
				map.get( SourceStateSerializer.STATE_KEY ).getAsJsonObject().get( NAME_KEY ).getAsString(),
				(RawSourceState< ?, ? >) dependsOnState );

		return state;
	}


}
