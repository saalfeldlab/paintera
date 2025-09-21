package org.janelia.saalfeldlab.paintera.state.channel.n5

import bdv.cache.SharedQueue
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.N5Helpers.serializeTo
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class N5BackendChannel<D, T>(
	override val metadataState: MetadataState,
	override val channelSelection: IntArray,
	override val channelIndex: Int,
) : AbstractN5BackendChannel<RealComposite<D>, VolatileWithSet<RealComposite<T>>>
		where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

	override val container = metadataState.reader
	override val dataset = metadataState.dataset


	override fun createSource(
		queue: SharedQueue,
		priority: Int,
		name: String
	): ChannelDataSource<RealComposite<D>, VolatileWithSet<RealComposite<T>>> {
		return N5ChannelDataSource.valueExtended(
			metadataState,
			name,
			queue,
			priority,
			channelIndex,
			channelSelection.map { i -> i.toLong() }.toLongArray(),
			Double.NaN
		)


	}

	private object SerializationKeys {
		const val CONTAINER = "container"
		const val DATASET = "dataset"
		const val CHANNELS = "channels"
		const val CHANNEL_INDEX = "channelIndex"
	}

	private object SerializationDefaultValues {
		const val CHANNEL_INDEX = 3
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendChannel<D, T>>
			where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

		override fun serialize(
			backend: N5BackendChannel<D, T>,
			typeOfSrc: Type,
			context: JsonSerializationContext
		): JsonElement {
			val map = JsonObject()
			with(SerializationKeys) {
				backend.container.serializeTo(map)
				map.addProperty(DATASET, backend.dataset)
				backend.channelIndex.takeIf { it != SerializationDefaultValues.CHANNEL_INDEX }?.let { map.addProperty(CHANNEL_INDEX, it) }
				map.add(CHANNELS, context[backend.channelSelection])
			}
			return map
		}

		override fun getTargetClass() = N5BackendChannel::class.java as Class<N5BackendChannel<D, T>>
	}

	@Plugin(type = PainteraSerialization.PainteraDeserializer::class)
	class Deserializer<D, T> : PainteraSerialization.PainteraDeserializer<N5BackendChannel<D, T>>
			where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

		override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext,
		): N5BackendChannel<D, T> {
			return with(SerializationKeys) {
				with(GsonExtensions) {
					val container = N5Helpers.deserializeFrom(json.asJsonObject)
					val dataset: String = json[DATASET]!!
					N5BackendChannel(
						MetadataUtils.createMetadataState(container, dataset)!!,
						context[json, CHANNELS]!!,
						json[CHANNEL_INDEX] ?: SerializationDefaultValues.CHANNEL_INDEX
					)
				}
			}
		}

		override fun getTargetClass() = N5BackendChannel::class.java as Class<N5BackendChannel<D, T>>

	}
}
