package org.janelia.saalfeldlab.paintera.serialization;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;
import javafx.scene.Group;
import net.imglib2.Volatile;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.SourceStateSerialization;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class SourceInfoSerializer implements PainteraSerialization.PainteraSerializer<SourceInfo>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String STATE_KEY = "state";

	private static final String DEPENDS_ON_KEY = SourceStateSerialization.DEPENDS_ON_KEY;// "dependsOn";

	private static final String NUM_SOURCES_KEY = "numSources";

	private static final String SOURCES_KEY = "sources";

	private static final String CURRENT_SOURCE_INDEX_KEY = "currentSourceIndex";

	private static final String STATE_TYPE_KEY = "type";

	private static final String SOURCE_TYPE_KEY = "sourceType";

	private static final String SOURCE_KEY = "source";

	private static final String UNDERLYING_SOURCE_KEY = "source";

	private static final String UNDERLYING_SOURCE_CLASS_KEY = "sourceClass";

	private static final String N5_META_TYPE_KEY = "metaType";

	private static final String N5_META_KEY = "meta";

	private static final String STATE_NAME_KEY = "name";

	private static final String TRANSFORM_KEY = "transform";

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
					final SourceState<?, ?>[] dependencies = IntStream
							.of(dependsOn[k].toArray())
							.mapToObj(m -> sourceStates[m])
							.toArray(SourceState[]::new);
					if (Stream.of(dependencies).filter(s -> s == null).count() == 0)
					{
						final JsonObject state = serializedStates.get(k).getAsJsonObject();
						@SuppressWarnings("unchecked") final Class<? extends SourceState<?, ?>> clazz = (Class<? extends SourceState<?, ?>>) Class.forName(state.get(STATE_TYPE_KEY).getAsString());
						LOG.debug("Deserializing state={}, class={}", state, clazz);
						if (LabelSourceState.class.equals(clazz)) {
							LOG.info("Trying to de-serialize deprecated LabelSourceState into ConnectomicsLabelState");
							// TODO get this to work!!
							final ConnectomicsLabelState<?, ?> connectomicsState = null;
//							final ConnectomicsLabelState<?, ?> connectomicsState =
//									connectomicsLabelStateFromLabelSourceState(
//											state.get(STATE_KEY).getAsJsonObject(),
//											gson,
//											);
							final boolean wasUnableToConvertState = connectomicsState == null;
							// TODO
							if (wasUnableToConvertState)
								sourceStates[k] = gson.fromJson(state.get(STATE_KEY), clazz);
							else
								sourceStates[k] = null;
						} else {
							sourceStates[k] = gson.fromJson(state.get(STATE_KEY), clazz);
						}
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

	@Override
	public Class<SourceInfo> getTargetClass() {
		return SourceInfo.class;
	}

	private static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & NativeType<T>> ConnectomicsLabelState<D, T> connectomicsLabelStateFromLabelSourceState(
			final JsonObject stateData,
			final Gson gson,
			final SharedQueue queue,
			final Supplier<String> projectDirectory,
			final ExecutorService propagationQueue,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors) throws IOException, ClassNotFoundException {
		final boolean isMaskedSource = MaskedSource.class.getName().equals(stateData.get(SOURCE_TYPE_KEY).getAsString());
		if (isMaskedSource)
			return connectomicsLabelStateFromLabelSourceStateMaskedSource(
					stateData,
					gson,
					queue,
					projectDirectory,
					propagationQueue,
					meshesGroup,
					meshManagerExecutors,
					meshWorkersExecutors);
		// only makes sense if we have masked source, so skip it here
		return null;
	}

	private static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & NativeType<T>> ConnectomicsLabelState<D, T> connectomicsLabelStateFromLabelSourceStateMaskedSource(
			final JsonObject stateData,
			final Gson gson,
			final SharedQueue queue,
			final Supplier<String> projectDirectory,
			final ExecutorService propagationQueue,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors) throws ClassNotFoundException, IOException {
		final JsonObject sourceData = stateData.get(SOURCE_KEY).getAsJsonObject();
		// only makes sense for N5DataSource
		if (!N5DataSource.class.getName().equals(sourceData.get(UNDERLYING_SOURCE_CLASS_KEY).getAsString()))
			return null;
		final JsonObject underlyingSourceData = sourceData.get(UNDERLYING_SOURCE_KEY).getAsJsonObject();
		final AffineTransform3D transform = gson.fromJson(underlyingSourceData.get(TRANSFORM_KEY), AffineTransform3D.class);
		final double[] resolution = transform == null
				? new double[] {1.0, 1.0, 1.0}
				: new double[] {transform.get(0, 0), transform.get(1, 1), transform.get(2, 2)};
		final double[] offset = transform == null
				? new double[] {0.0, 0.0, 0.0}
				: new double[] {transform.get(0, 3), transform.get(1, 3), transform.get(2, 3)};
		final N5Meta meta = gson.<N5Meta>fromJson(
				underlyingSourceData.get(N5_META_KEY),
				Class.forName(underlyingSourceData.get(N5_META_TYPE_KEY).getAsString()));
		final N5Backend<D, T> backend = N5Backend.createFrom(
				meta.getWriter(),
				meta.getDataset(),
				queue,
				0,
				stateData.get(STATE_NAME_KEY).getAsString(),
				projectDirectory,
				propagationQueue,
				resolution,
				offset);
		try {
			// TODO make sure that selection, assignments etc is preserved
			return new ConnectomicsLabelState<>(backend, meshesGroup, meshManagerExecutors, meshWorkersExecutors);
		} catch (Exception e) {
			LOG.info("Unable to create ConnectomicLabelState from LabelSourceState for state {}", stateData, e);
			return null;
		}
	}
}
