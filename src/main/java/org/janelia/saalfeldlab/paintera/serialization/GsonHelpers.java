package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.GsonBuilder;
import javafx.util.Pair;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public class GsonHelpers {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final Context context,
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) throws InstantiableException {
		final IntFunction<SourceState<?, ?>> dependencyFromIndex = index -> viewer.sourceInfo().getState(viewer
				.sourceInfo().trackSources().get(
						index));
		return builderWithAllRequiredDeserializers(context, new Arguments(viewer), projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final Context context,
			final Arguments arguments,
			final Supplier<String> projectDirectory,
			final IntFunction<SourceState<?, ?>> dependencyFromIndex) {

		final Map<Class<?>, List<Pair<PainteraSerialization.PainteraDeserializer, Double>>> deserializers = PainteraSerialization.getDeserializers(context);
		final Map<Class<?>, List<Pair<StatefulSerializer.DeserializerFactory, Double>>> deserializerFactories = StatefulSerializer.getDeserializers(context);

		final GsonBuilder builder = new GsonBuilder();

		for (final Map.Entry<Class<?>, List<Pair<PainteraSerialization.PainteraDeserializer, Double>>> d : deserializers.entrySet())
		{
			final PainteraSerialization.PainteraDeserializer<?> v = d.getValue().get(0).getKey();
			LOG.debug("Adding deserializer for class {} ({})", d.getKey(), v.isHierarchyAdapter());
			if (v.isHierarchyAdapter())
				builder.registerTypeHierarchyAdapter(d.getKey(), v);
			else
				builder.registerTypeAdapter(d.getKey(), v);
		}

		for (final Map.Entry<Class<?>, List<Pair<StatefulSerializer.DeserializerFactory, Double>>> d : deserializerFactories.entrySet())
		{
			final StatefulSerializer.DeserializerFactory<?, ?> v = d.getValue().get(0).getKey();
			LOG.debug("Adding deserializer factory for class {}", d.getKey());
			builder.registerTypeAdapter(d.getKey(), v.createDeserializer(arguments, projectDirectory, dependencyFromIndex));
		}

		return builder;

	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final Context context,
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) {
		final ToIntFunction<SourceState<?, ?>> dependencyFromIndex =
				state -> viewer.sourceInfo().trackSources().indexOf(state.getDataSource());
		return builderWithAllRequiredSerializers(context, projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final Context context,
			final Supplier<String> projectDirectory,
			final ToIntFunction<SourceState<?, ?>> dependencyToIndex) {
		LOG.debug("Creating builder with required serializers.");

		final Map<Class<?>, List<Pair<PainteraSerialization.PainteraSerializer, Double>>> serializers = PainteraSerialization.getSerializers(context);
		final Map<Class<?>, List<Pair<StatefulSerializer.SerializerFactory, Double>>> serializerFactories = StatefulSerializer.getSerializers(context);
		LOG.debug("Found serializer factories for these classes: {}", serializerFactories.keySet());

		final GsonBuilder builder = new GsonBuilder();

		for (final Map.Entry<Class<?>, List<Pair<PainteraSerialization.PainteraSerializer, Double>>> d : serializers.entrySet())
		{
			final PainteraSerialization.PainteraSerializer v = d.getValue().get(0).getKey();
			LOG.debug("Adding serializer for class {} ({})", d.getKey(), v.isHierarchyAdapter());
			if (v.isHierarchyAdapter())
				builder.registerTypeHierarchyAdapter(d.getKey(), v);
			else
				builder.registerTypeAdapter(d.getKey(), v);
		}

		for (final Map.Entry<Class<?>, List<Pair<StatefulSerializer.SerializerFactory, Double>>> d : serializerFactories.entrySet())
		{
			final StatefulSerializer.SerializerFactory v = d.getValue().get(0).getKey();
			LOG.debug("Adding serializer factory for class {}", d.getKey());
			builder.registerTypeAdapter(d.getKey(), v.createSerializer(projectDirectory, dependencyToIndex));
		}

		return builder;
	}

}
