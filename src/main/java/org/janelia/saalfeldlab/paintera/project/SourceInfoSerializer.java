package org.janelia.saalfeldlab.paintera.project;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
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
		final Map< String, Object > elements = context.deserialize( json, typeOfT );
		final List< JsonElement > sources = ( List< JsonElement > ) elements.get( SOURCES_KEY );
		final int currentSourceIndex = ( int ) elements.get( CURRENT_SOURCE_INDEX_KEY );

		final List< SourceState< ?, ? > > sourceStates = new ArrayList<>();
		for ( final JsonElement jsonSource : sources )
		{
			final SourceState< ?, ? > state = context.deserialize( jsonSource, SourceState.class );
			sourceStates.add( state );
		}

		final SourceInfo sourceInfo = new SourceInfo();
		for ( final SourceState state : sourceStates )
			sourceInfo.addState( state );

		sourceInfo.currentSourceIndexProperty().set( currentSourceIndex );

		return sourceInfo;
	}

	@Override
	public JsonElement serialize( final SourceInfo src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		System.out.println( 1 );
		final Map< String, Object > elements = new HashMap<>();
		System.out.println( 2 );
		final List< Source< ? > > sources = new ArrayList<>( src.trackSources() );
		System.out.println( 3 );
		final List< JsonElement > serializedSources = new ArrayList<>();

		System.out.println( 4 );
		final int currentSourceIndex = src.currentSourceIndexProperty().get();
		System.out.println( 5 );
		elements.put( NUM_SOURCES_KEY, sources.size() );
		System.out.println( 6 );
		elements.put( CURRENT_SOURCE_INDEX_KEY, currentSourceIndex );
		System.out.println( 7 );
		elements.put( SOURCES_KEY, serializedSources );
		System.out.println( 8 );
		return context.serialize( elements );
	}

}
