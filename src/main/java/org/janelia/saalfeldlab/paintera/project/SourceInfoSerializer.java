package org.janelia.saalfeldlab.paintera.project;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import bdv.viewer.Source;

public class SourceInfoSerializer implements JsonSerializer< SourceInfo >, JsonDeserializer< SourceInfo >
{

	private static final String NUM_SOURCES_KEY = "numSources";

	private static final String SOURCES_KEY = "sources";

	private static final String CURRENT_SOURCE_INDEX_KEY = "currentSourceIndex";

	@Override
	public SourceInfo deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
	{

		final SourceInfo sourceInfo = new SourceInfo();
		final JsonObject elements = json.getAsJsonObject();
		final JsonArray sources = elements.get( SOURCES_KEY ).getAsJsonArray();
		final int currentSourceIndex = elements.get( CURRENT_SOURCE_INDEX_KEY ).getAsInt();

		final List< SourceState< ?, ? > > sourceStates = new ArrayList<>();
		for ( final JsonElement jsonSource : sources )
		{
			final SourceState< ?, ? > state = context.deserialize( jsonSource, SourceState.class );
			sourceInfo.addState( ( SourceState ) state );
		}
		sourceInfo.currentSourceIndexProperty().set( currentSourceIndex );
		return sourceInfo;
	}

	@Override
	public JsonElement serialize( final SourceInfo src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final Map< String, Object > elements = new HashMap<>();
		final List< Source< ? > > sources = new ArrayList<>( src.trackSources() );

		final List< JsonElement > serializedSources = src
				.trackSources()
				.stream()
				.map( src::getState )
				.map( s -> context.serialize( s, SourceState.class ) )
				.collect( Collectors.toList() );

		final int currentSourceIndex = src.currentSourceIndexProperty().get();
		elements.put( NUM_SOURCES_KEY, sources.size() );
		elements.put( CURRENT_SOURCE_INDEX_KEY, currentSourceIndex );
		elements.put( SOURCES_KEY, serializedSources );
		return context.serialize( elements );
	}

}
