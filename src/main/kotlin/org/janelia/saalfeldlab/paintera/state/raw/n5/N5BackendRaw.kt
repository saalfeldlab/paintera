package org.janelia.saalfeldlab.paintera.state.raw.n5

import bdv.cache.SharedQueue
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.AxisAdapter
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.N5Helpers.serializeTo
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class N5BackendRaw<D, T>(override val metadataState: MetadataState) : AbstractN5BackendRaw<D, T>
		where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

	override fun createSource(queue: SharedQueue, priority: Int, name: String): DataSource<D, T> {
		return N5DataSource(metadataState, name, queue, priority)
	}
}

internal object SerializationKeys {
	@Deprecated("No need to specify the container class; prefer URI instead via N5Factory")
	const val CONTAINER = "container"
	const val DATASET = "dataset"
}

@Plugin(type = PainteraSerialization.PainteraSerializer::class)
class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendRaw<D, T>>
		where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

	override fun serialize(
		backend: N5BackendRaw<D, T>,
		typeOfSrc: Type,
		context: JsonSerializationContext
	): JsonElement {
		val map = JsonObject()
		with(SerializationKeys) {
			backend.container.serializeTo(map)
			map[DATASET] = backend.dataset
			map["metadataState"] = backend.metadataState.serializeMetadataState()
		}
		return map
	}

	override fun getTargetClass() = N5BackendRaw::class.java as Class<N5BackendRaw<D, T>>
}

@Plugin(type = PainteraSerialization.PainteraDeserializer::class)
class Deserializer<D, T>() : PainteraSerialization.PainteraDeserializer<N5BackendRaw<D, T>>
		where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

	override fun deserialize(
		json: JsonElement,
		typeOfT: Type,
		context: JsonDeserializationContext
	): N5BackendRaw<D, T> {
		return with(SerializationKeys) {
			val container = N5Helpers.deserializeFrom(json.asJsonObject)
			val dataset: String = json[DATASET]!!
			val metadataState = MetadataUtils.createMetadataState(container, dataset)!!
			metadataState.deserializeMetadataState(json["metadataState"])
			N5BackendRaw(metadataState)
		}
	}

	override fun getTargetClass() = N5BackendRaw::class.java as Class<N5BackendRaw<D, T>>
}

private val axisGson = GsonBuilder()
	.registerTypeAdapter(Axis::class.java, AxisAdapter())
	.create()

internal fun MetadataState.deserializeMetadataState(json: JsonObject?) {

	json ?: return

	json["axes"]?.let { axisGson.fromJson(it, Array<Axis>::class.java) }?.let {
		axes = it
	}
	slicePositions = json["slicePositions"]?.let { axisGson.fromJson(it, Array<Int>::class.java)?.toIntArray() }

}

internal fun MetadataState.serializeMetadataState() : JsonObject {

	return JsonObject().also { msObj ->
		msObj["axes"] = axisGson.toJsonTree(axes)
		slicePositions?.let {
			msObj["slicePositions"] = axisGson.toJsonTree(it)
		}
	}
}
