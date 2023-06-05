package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.GsonBuilder;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public class GsonHelpers {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final Context context,
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) throws InstantiableException {

		final IntFunction<SourceState<?, ?>> dependencyFromIndex = index -> viewer
				.sourceInfo()
				.getState(viewer.sourceInfo().trackSources().get(index));
		return builderWithAllRequiredDeserializers(context, new Arguments(viewer), projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredDeserializers(
			final Context context,
			final Arguments arguments,
			final Supplier<String> projectDirectory,
			final IntFunction<SourceState<?, ?>> dependencyFromIndex) {

		LOG.debug("Creating builder with required deserializers.");

		final var classToDeserializersMap = PainteraSerialization.getDeserializers(context);
		final var classToDeserializerFactoriesMap = StatefulSerializer.getDeserializers(context);

		final GsonBuilder builder = new GsonBuilder();

		for (final var entry : classToDeserializersMap.entrySet()) {
			final var cls = entry.getKey();
			final var firstDeserializer = entry.getValue().get(0).getKey();
			LOG.trace("Adding deserializer for class {} ({})", cls, firstDeserializer.isHierarchyAdapter());

			if (firstDeserializer.isHierarchyAdapter())
				builder.registerTypeHierarchyAdapter(cls, firstDeserializer);
			else
				builder.registerTypeAdapter(cls, firstDeserializer);
		}

		for (final var entry : classToDeserializerFactoriesMap.entrySet()) {
			final var firstDeserializerFactory = entry.getValue().get(0).getKey();
			final var cls = entry.getKey();
			LOG.trace("Adding deserializer factory for class {}", cls);
			//noinspection unchecked
			builder.registerTypeAdapter(cls, firstDeserializerFactory.createDeserializer(arguments, projectDirectory, dependencyFromIndex));
		}

		return builder;

	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final Context context,
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) {

		final ToIntFunction<SourceState<?, ?>> dependencyFromIndex = state -> viewer.sourceInfo().trackSources().indexOf(state.getDataSource());
		return builderWithAllRequiredSerializers(context, projectDirectory, dependencyFromIndex);
	}

	public static GsonBuilder builderWithAllRequiredSerializers(
			final Context context,
			final Supplier<String> projectDirectory,
			final ToIntFunction<SourceState<?, ?>> dependencyToIndex) {

		LOG.debug("Creating builder with required serializers.");

		final var classToSerializersMap = PainteraSerialization.getSerializers(context);
		final var classToSerializerFactoriesMap = StatefulSerializer.getSerializers(context);
		LOG.trace("Found serializer factories for these classes: {}", classToSerializerFactoriesMap.keySet());

		final GsonBuilder builder = new GsonBuilder();

		for (final var entry : classToSerializersMap.entrySet()) {
			final var firstSerialzier = entry.getValue().get(0).getKey();
			final var cls = entry.getKey();
			LOG.trace("Adding serializer for class {} ({})", cls, firstSerialzier.isHierarchyAdapter());
			if (firstSerialzier.isHierarchyAdapter())
				builder.registerTypeHierarchyAdapter(cls, firstSerialzier);
			else
				builder.registerTypeAdapter(cls, firstSerialzier);
		}

		for (final var entry : classToSerializerFactoriesMap.entrySet()) {
			final var firstSerializerFactory = entry.getValue().get(0).getKey();
			final var cls = entry.getKey();
			LOG.trace("Adding serializer factory for class {}", cls);
			//noinspection unchecked
			builder.registerTypeAdapter(cls, firstSerializerFactory.createSerializer(projectDirectory, dependencyToIndex));
		}

		return builder;
	}

}
