package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import javafx.scene.Group;
import javafx.util.Pair;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.util.SciJavaUtils;
import org.scijava.Context;
import org.scijava.InstantiableException;
import org.scijava.plugin.SciJavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

// TODO make service for this
public class StatefulSerializer
{

	public static class Arguments
	{
		public final ExecutorService generalPurposeExecutors;

		public final ExecutorService meshManagerExecutors;

		public final ExecutorService meshWorkersExecutors;

		public final ExecutorService propagationWorkers;

		public final Group meshesGroup;

		public final PainteraBaseView viewer;

		public Arguments(final PainteraBaseView viewer)
		{
			this.generalPurposeExecutors = viewer.generalPurposeExecutorService();
			this.meshManagerExecutors = viewer.getMeshManagerExecutorService();
			this.meshWorkersExecutors = viewer.getMeshWorkerExecutorService();
			this.propagationWorkers = viewer.getPropagationQueue();
			this.meshesGroup = viewer.viewer3D().meshesGroup();
			this.viewer = viewer;
		}
	}

	private static Map<Class<?>, List<Pair<SerializerFactory, Double>>> SERIALIZER_FACTORIES_SORTED_BY_PRIORITY = null;

	private static Map<Class<?>, List<Pair<DeserializerFactory, Double>>> DESERIALIZER_FACTORIES_SORTED_BY_PRIORITY = null;

	public interface SerializerFactory<T, S extends JsonSerializer<T>> extends SciJavaPlugin, SciJavaUtils.HasTargetClass<T>
	{
		S createSerializer(
				Supplier<String> projectDirectory,
				ToIntFunction<SourceState<?, ?>> stateToIndex);
	}

	public interface DeserializerFactory<T, S extends JsonDeserializer<T>> extends SciJavaPlugin, SciJavaUtils.HasTargetClass<T>
	{
		S createDeserializer(
				Arguments arguments,
				Supplier<String> projectDirectory,
				IntFunction<SourceState<?, ?>> dependencyFromIndex);
	}

	public interface SerializerAndDeserializer<T, D extends JsonDeserializer<T>, S extends JsonSerializer<T>>
			extends SerializerFactory<T, S>, DeserializerFactory<T, D>, SciJavaPlugin, SciJavaUtils.HasTargetClass<T>
	{

	}

	public static Map<Class<?>, List<Pair<SerializerFactory, Double>>> getSerializers(final Context context)
	{
		if (SERIALIZER_FACTORIES_SORTED_BY_PRIORITY == null) {
			try {
				SERIALIZER_FACTORIES_SORTED_BY_PRIORITY = Collections.unmodifiableMap(SciJavaUtils.byTargetClassSortedByPriorities(
						SerializerFactory.class,
						context));
			} catch (InstantiableException e) {
				throw new RuntimeException(e);
			}
		}
		return SERIALIZER_FACTORIES_SORTED_BY_PRIORITY;
	}

	public static Map<Class<?>, List<Pair<DeserializerFactory, Double>>> getDeserializers(final Context context)
	{
		if (DESERIALIZER_FACTORIES_SORTED_BY_PRIORITY == null) {
			try {
				DESERIALIZER_FACTORIES_SORTED_BY_PRIORITY = Collections.unmodifiableMap(SciJavaUtils.byTargetClassSortedByPriorities(
						DeserializerFactory.class,
						context));
			} catch (InstantiableException e) {
				throw new RuntimeException(e);
			}
		}
		return DESERIALIZER_FACTORIES_SORTED_BY_PRIORITY;
	}
}
