package org.janelia.saalfeldlab.paintera.serialization;

import java.util.concurrent.ExecutorService;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import bdv.util.volatiles.SharedQueue;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import javafx.scene.Group;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.state.SourceState;

public class StatefulSerializer
{

	public static class Arguments
	{
		public final GlobalCache globalCache;

		public final ExecutorService generalPurposeExecutors;

		public final ExecutorService meshManagerExecutors;

		public final ExecutorService meshWorkersExecutors;

		public final ExecutorService propagationWorkers;

		public final Group meshesGroup;

		public Arguments(final PainteraBaseView viewer)
		{
			this.globalCache = viewer.getGlobalCache();
			this.generalPurposeExecutors = viewer.generalPurposeExecutorService();
			this.meshManagerExecutors = viewer.getMeshManagerExecutorService();
			this.meshWorkersExecutors = viewer.getMeshWorkerExecutorService();
			this.propagationWorkers = viewer.getPropagationQueue();
			this.meshesGroup = viewer.viewer3D().meshesGroup();
		}
	}

	public static interface Serializer<T, S extends JsonSerializer<T>>
	{
		public S createSerializer(
				Supplier<String> projectDirectory,
				ToIntFunction<SourceState<?, ?>> stateToIndex);
	}

	public static interface Deserializer<T, S extends JsonDeserializer<T>>
	{
		public S createDeserializer(
				Arguments arguments,
				Supplier<String> projectDirectory,
				IntFunction<SourceState<?, ?>> dependencyFromIndex);
	}

	public static interface SerializerAndDeserializer<T, D extends JsonDeserializer<T>, S extends JsonSerializer<T>>
			extends Serializer<T, S>, Deserializer<T, D>
	{

	}

}
