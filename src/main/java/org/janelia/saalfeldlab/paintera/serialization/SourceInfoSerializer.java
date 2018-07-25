package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import bdv.viewer.Source;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;
import net.imglib2.exception.IncompatibleTypeException;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.SourceStateSerialization;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceInfoSerializer implements JsonSerializer<SourceInfo>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String STATE_KEY = "state";

	private static final String DEPENDS_ON_KEY = SourceStateSerialization.DEPENDS_ON_KEY;// "dependsOn";

	private static final String NUM_SOURCES_KEY = "numSources";

	private static final String SOURCES_KEY = "sources";

	private static final String CURRENT_SOURCE_INDEX_KEY = "currentSourceIndex";

	private static final String STATE_TYPE_KEY = "type";

	@Override
	public JsonElement serialize(final SourceInfo src, final Type typeOfSrc, final JsonSerializationContext context)
	{
		final Map<String, Object> elements = new HashMap<>();
		final List<Source<?>>     sources  = new ArrayList<>(src.trackSources());

		LOG.debug("Serializing sources: {}", sources);

		final List<JsonElement> serializedSources = src
				.trackSources()
				.stream()
				.map(src::getState)
				.map(s -> {
					final JsonObject typeAndData = new JsonObject();
					typeAndData.addProperty(STATE_TYPE_KEY, s.getClass().getName());
					typeAndData.add(STATE_KEY, context.serialize(s, s.getClass()));
					return typeAndData;
				})
				.collect(Collectors.toList());
		LOG.debug("Serialized sources: {}", serializedSources);

		final int currentSourceIndex = src.currentSourceIndexProperty().get();
		elements.put(NUM_SOURCES_KEY, sources.size());
		elements.put(CURRENT_SOURCE_INDEX_KEY, currentSourceIndex);
		elements.put(SOURCES_KEY, serializedSources);
		return context.serialize(elements);
	}

	public static void populate(
			final Consumer<SourceState<?, ?>> addState,
			final IntConsumer currentSourceIndex,
			final JsonObject serializedSourceInfo,
			final BiConsumer<Integer, SourceState<?, ?>> logSourceForDependencies,
			final Gson gson) throws
			IncompatibleTypeException,
			ClassNotFoundException,
			JsonParseException,
			UndefinedDependency,
			HasCyclicDependencies,
			IOException
	{
		final SourceState<?, ?>[] states = makeStates(
				serializedSourceInfo.get(SOURCES_KEY).getAsJsonArray(),
				logSourceForDependencies,
				gson
		                                             );
		Arrays
				.stream(states)
				.forEach(addState::accept);
		currentSourceIndex.accept(serializedSourceInfo.get(CURRENT_SOURCE_INDEX_KEY).getAsInt());
	}

	public static SourceState<?, ?>[] makeStates(
			final JsonArray serializedStates,
			final BiConsumer<Integer, SourceState<?, ?>> logSourceForDependencies,
			final Gson gson) throws
			ClassNotFoundException,
			UndefinedDependency,
			HasCyclicDependencies,
			IncompatibleTypeException,
			JsonParseException,
			IOException
	{
		final int numStates           = serializedStates.size();
		final TIntHashSet[] dependsOn = new TIntHashSet[numStates];
		LOG.debug("Deserializing {}", serializedStates);
		for (int i = 0; i < numStates; ++i)
		{
			final JsonObject map = serializedStates.get(i).getAsJsonObject().get(STATE_KEY).getAsJsonObject();
			LOG.debug("Deserializing state {}: {}", i, map);
			final int[] depends = Optional
					.ofNullable(serializedStates.get(i).getAsJsonObject().get(DEPENDS_ON_KEY))
					.map(el -> gson.fromJson(el, int[].class))
					.orElseGet(() -> new int[] {});
			if (Arrays.stream(depends).filter(d -> d < 0 || d >= numStates).count() > 0)
			{
				throw new UndefinedDependency(depends, numStates);
			}
			dependsOn[i] = new TIntHashSet(depends);
		}

		if (hasCycles(dependsOn)) { throw new HasCyclicDependencies(dependsOn); }

		final SourceState<?, ?>[] sourceStates = new SourceState[numStates];

		for (int i = 0; i < numStates && Arrays.stream(sourceStates).filter(s -> s == null).count() > 0; ++i)
		{
			for (int k = 0; k < numStates; ++k)
			{
				if (sourceStates[k] == null)
				{
					final SourceState<?, ?>[] dependencies = IntStream.of(dependsOn[k].toArray()).mapToObj(m ->
							sourceStates[m]).toArray(
							SourceState[]::new);
					if (Stream.of(dependencies).filter(s -> s == null).count() == 0)
					{
						final JsonObject state = serializedStates.get(k).getAsJsonObject();
						@SuppressWarnings("unchecked") final Class<? extends SourceState<?, ?>> clazz = (Class<?
								extends SourceState<?, ?>>) Class.forName(
								state.get(STATE_TYPE_KEY).getAsString());
						LOG.debug("Deserializing state={}, class={}", state, clazz);
						sourceStates[k] = gson.fromJson(state.get(STATE_KEY), clazz);
						logSourceForDependencies.accept(k, sourceStates[k]);
					}
				}
			}
		}

		if (Arrays.stream(sourceStates).filter(s -> s == null).count() > 0) { throw new RuntimeException("OOPS!"); }

		return sourceStates;

	}

	private static boolean hasCycles(final TIntHashSet[] nodeEdgeMap)
	{
		final TIntHashSet visitedNodes = new TIntHashSet();
		for (int node = 0; node < nodeEdgeMap.length; ++node)
		{
			visit(nodeEdgeMap, node, visitedNodes);
		}
		return false;
	}

	private static boolean visit(
			final TIntHashSet[] nodeEdgeMap,
			final int node,
			final TIntHashSet hasVisited)
	{
		if (hasVisited.contains(node)) { return true; }
		hasVisited.add(node);
		for (final TIntIterator it = nodeEdgeMap[node].iterator(); it.hasNext(); )
		{
			final boolean foundAlreadyVisitedNode = visit(nodeEdgeMap, it.next(), hasVisited);
			if (foundAlreadyVisitedNode) { return foundAlreadyVisitedNode; }
		}
		return false;
	}

}
