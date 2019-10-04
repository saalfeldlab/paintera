package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.*
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier
import java.util.function.ToIntFunction

class N5FSMetaPainteraAdapter(val projectDirectory: Supplier<String>) : JsonSerializer<N5FSMeta>, JsonDeserializer<N5FSMeta> {

	override fun serialize(
			src: N5FSMeta,
			typeOfSrc: Type,
			context: JsonSerializationContext): JsonElement {
		val projectDirectory = this.projectDirectory.get()
		return JsonObject()
				.also { it.addProperty(DATASET_KEY, src.dataset()) }
				.also { m -> src.basePath().takeUnless { it == projectDirectory }?.let { m.addProperty(CONTAINER_PATH_KEY, it) } }
	}

	override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext): N5FSMeta {
		return with (GsonExtensions) {
			N5FSMeta(
					json.getStringProperty(CONTAINER_PATH_KEY) ?: projectDirectory.get(),
					json.getStringProperty(DATASET_KEY)!!)
		}
	}

	companion object {
		private const val DATASET_KEY = "dataset"

		private const val CONTAINER_PATH_KEY = "n5"
	}

	@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
	class Factory : StatefulSerializer.SerializerAndDeserializer<N5FSMeta, N5FSMetaPainteraAdapter, N5FSMetaPainteraAdapter> {

		override fun createSerializer(
				projectDirectory: Supplier<String>,
				stateToIndex: ToIntFunction<SourceState<*, *>>) = N5FSMetaPainteraAdapter(projectDirectory)

		override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>) = N5FSMetaPainteraAdapter(projectDirectory)

		override fun getTargetClass() = N5FSMeta::class.java

	}


}
