package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.IntFunction
import java.util.function.Supplier
import java.util.function.ToIntFunction

class N5HDF5MetaPainteraAdapter(val projectDirectory: Supplier<String>) : JsonSerializer<N5HDF5Meta>, JsonDeserializer<N5HDF5Meta> {

	override fun serialize(
			src: N5HDF5Meta,
			typeOfSrc: Type,
			context: JsonSerializationContext): JsonElement {
		val projectDirectory = Paths.get(this.projectDirectory.get())
		val isRelative = src.file.isContainedIn(projectDirectory)
		return JsonObject()
				.also { it.addProperty(DATASET_KEY, src.dataset()) }
				.also { m -> src.defaultCellDimensionsCopy?.let { m.add(DEFAULT_CELL_DIMENSIONS_KEY, context.serialize(it)) } }
				.also { it.addProperty(OVERRIDE_CELL_DIMENSIONS_KEY, src.isOverrideCellDimensions) }
				.also { it.takeIf { isRelative }?.addProperty(IS_RELATIVE_TO_PROJECT_KEY, isRelative) }
				.also { it.addProperty(FILE_KEY, if (isRelative) projectDirectory.relativize(src.file).toString() else src.file) }
	}

	override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext) = with (GsonExtensions) {
		N5HDF5Meta(
				json.getStringProperty(FILE_KEY)!!.let { if (json.isRelative()) projectDirectory.get().resolve(it) else it },
				json.getStringProperty(DATASET_KEY)!!,
				context.deserialize(json.getJsonObject(DEFAULT_CELL_DIMENSIONS_KEY), IntArray::class.java),
				json.getBooleanProperty(OVERRIDE_CELL_DIMENSIONS_KEY)!!)
		}

	companion object {
		private const val DATASET_KEY = "dataset"

		private const val FILE_KEY = "file"

		private const val IS_RELATIVE_TO_PROJECT_KEY = "isRelativeToProject"

		private const val DEFAULT_CELL_DIMENSIONS_KEY = "defaultCellDimensions"

		private const val OVERRIDE_CELL_DIMENSIONS_KEY = "overrideCellDimensions"

		private fun JsonElement.isRelative() = with (GsonExtensions) { getBooleanProperty(IS_RELATIVE_TO_PROJECT_KEY) } ?: false

		private fun Path.realAbsolute() = toRealPath().toAbsolutePath()

		private fun String.isContainedIn(parent: Path) = Paths.get(this).isContainedIn(parent)

		private fun Path.isContainedIn(parent: Path) = this.realAbsolute().startsWith(parent.realAbsolute())

		private fun Path.relativize(other: String) = this.relativize(Paths.get(other))

		private fun String.resolve(other: String) = Paths.get(this).resolve(other).toString()
	}

	@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
	class Factory : StatefulSerializer.SerializerAndDeserializer<N5HDF5Meta, N5HDF5MetaPainteraAdapter, N5HDF5MetaPainteraAdapter> {

		override fun createSerializer(
				projectDirectory: Supplier<String>,
				stateToIndex: ToIntFunction<SourceState<*, *>>) = N5HDF5MetaPainteraAdapter(projectDirectory)

		override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>) = N5HDF5MetaPainteraAdapter(projectDirectory)

		override fun getTargetClass() = N5HDF5Meta::class.java

	}


}
