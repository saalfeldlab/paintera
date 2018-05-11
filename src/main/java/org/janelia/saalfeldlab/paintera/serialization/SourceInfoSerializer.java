package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import javafx.scene.Group;
import net.imglib2.exception.IncompatibleTypeException;

public class SourceInfoSerializer implements JsonSerializer< SourceInfo >
{

	private static final String NUM_SOURCES_KEY = "numSources";

	private static final String SOURCES_KEY = "sources";

	private static final String CURRENT_SOURCE_INDEX_KEY = "currentSourceIndex";

	@Override
	public JsonElement serialize( final SourceInfo src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final Map< String, Object > elements = new HashMap<>();
		final List< Source< ? > > sources = new ArrayList<>( src.trackSources() );

		final List< JsonElement > serializedSources = src
				.trackSources()
				.stream()
				.map( src::getState )
				.map( s -> context.serialize( new SourceStateWithIndexedDependencies<>( s, src ), SourceStateWithIndexedDependencies.class ) )
				.collect( Collectors.toList() );

		final int currentSourceIndex = src.currentSourceIndexProperty().get();
		elements.put( NUM_SOURCES_KEY, sources.size() );
		elements.put( CURRENT_SOURCE_INDEX_KEY, currentSourceIndex );
		elements.put( SOURCES_KEY, serializedSources );
		return context.serialize( elements );
	}

	public static void populate(
			final Consumer< SourceState< ?, ? > > addState,
			final IntConsumer currentSourceIndex,
			final JsonObject serializedSourceInfo,
			final SharedQueue queue,
			final int priority,
			final Group root,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final Gson gson ) throws IncompatibleTypeException, ClassNotFoundException, JsonParseException, UndefinedDependency, HasCyclicDependencies, IOException
	{
//		final SourceState< ?, ? >[] states = SourceStateSerializer.makeStates(
//				serializedSourceInfo.get( SOURCES_KEY ).getAsJsonArray(),
//				queue,
//				priority,
//				root,
//				propagationExecutor,
//				manager,
//				workers,
//				gson );
//		Arrays
//		.stream( states )
//		.forEach( addState::accept );
//		currentSourceIndex.accept( serializedSourceInfo.get( CURRENT_SOURCE_INDEX_KEY ).getAsInt() );
	}

}
