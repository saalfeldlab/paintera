package org.janelia.saalfeldlab.paintera.state.channel.n5

import bdv.util.volatiles.SharedQueue
import com.google.gson.*
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier

// NB: If this ever becomes dataset dependent, we should create individual classes for
//         - dataset
//         - multi-scale group
//         - paintera dataset

class N5BackendChannel<D, T> constructor(
	override val container: N5Writer,
	override val dataset: String,
	private val resolution: DoubleArray,
	private val offset: DoubleArray,
	private val channelSelection: IntArray,
	private val channelIndex: Int,
	queue: SharedQueue,
	priority: Int,
	name: String) : AbstractN5BackendChannel<RealComposite<D>, VolatileWithSet<RealComposite<T>>>
		where D: NativeType<D>, D: RealType<D>, T: AbstractVolatileRealType<D, T>, T: NativeType<T> {

	private val transform = N5Helpers.fromResolutionAndOffset(resolution, offset)
	override val source: ChannelDataSource<RealComposite<D>, VolatileWithSet<RealComposite<T>>> = N5ChannelDataSource.valueExtended<D, T>(
		N5Meta.fromReader(container, dataset),
		transform,
		name,
		queue,
		priority,
		channelIndex,
		channelSelection.map { it.toLong() }.toLongArray(),
		Double.NaN)

	private object SerializationKeys {
		const val CONTAINER = "container"
		const val DATASET = "dataset"
		const val RESOLUTION = "resolution"
		const val OFFSET = "offset"
		const val NAME = "name"
		const val CHANNELS = "channels"
		const val CHANNEL_INDEX = "channelIndex"
	}

	private object SerializationDefaultValues {
		const val CHANNEL_INDEX = 3
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendChannel<D, T>>
			where D: NativeType<D>, D: RealType<D>, T: AbstractVolatileRealType<D, T>, T: NativeType<T> {

		override fun serialize(
			backend: N5BackendChannel<D, T>,
			typeOfSrc: Type,
			context: JsonSerializationContext): JsonElement {
			val map = JsonObject()
			with (SerializationKeys) {
				map.add(CONTAINER, SerializationHelpers.serializeWithClassInfo(backend.container, context))
				map.addProperty(DATASET, backend.dataset)
				map.add(RESOLUTION, context.serialize(backend.resolution))
				map.add(OFFSET, context.serialize(backend.offset))
				map.addProperty(NAME, backend.source.name)
				backend.channelIndex.takeIf { it != SerializationDefaultValues.CHANNEL_INDEX }?.let { map.addProperty(CHANNEL_INDEX, it) }
				map.add(CHANNELS, context.serialize(backend.channelSelection))
			}
			return map
		}

		override fun getTargetClass() = N5BackendChannel::class.java as Class<N5BackendChannel<D, T>>
	}

	class Deserializer<D, T>(
		private val queue: SharedQueue,
		private val priority: Int) : JsonDeserializer<N5BackendChannel<D, T>>
			where D: NativeType<D>, D: RealType<D>, T: AbstractVolatileRealType<D, T>, T: NativeType<T> {

		@Plugin(type = StatefulSerializer.DeserializerFactory::class)
		class Factory<D, T> : StatefulSerializer.DeserializerFactory<N5BackendChannel<D, T>, Deserializer<D, T>>
				where D: NativeType<D>, D: RealType<D>, T: AbstractVolatileRealType<D, T>, T: NativeType<T> {
			override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>): Deserializer<D, T> = Deserializer(
				arguments.viewer.queue,
				0)

			override fun getTargetClass() = N5BackendChannel::class.java as Class<N5BackendChannel<D, T>>
		}

		override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext
		): N5BackendChannel<D, T> {
			return with (SerializationKeys) {
				with (GsonExtensions) {
					N5BackendChannel<D, T>(
						SerializationHelpers.deserializeFromClassInfo(json.getJsonObject(CONTAINER)!!, context),
						json.getStringProperty(DATASET)!!,
						json.getProperty(RESOLUTION)?.let { context.deserialize<DoubleArray>(it, DoubleArray::class.java) } ?: DoubleArray(3) { 1.0 },
						json.getProperty(OFFSET)?.let { context.deserialize<DoubleArray>(it, DoubleArray::class.java) } ?: DoubleArray(3) { 0.0 },
						context.deserialize(json.getProperty(CHANNELS)!!, IntArray::class.java),
						json.getIntProperty(CHANNEL_INDEX) ?: SerializationDefaultValues.CHANNEL_INDEX,
						queue,
						priority,
						json.getStringProperty(NAME) ?: json.getStringProperty(DATASET)!!)
				}
			}
		}

	}
}
