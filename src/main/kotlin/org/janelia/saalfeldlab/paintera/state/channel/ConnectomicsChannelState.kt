package org.janelia.saalfeldlab.paintera.state.channel

import bdv.cache.SharedQueue
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import io.github.oshai.kotlinlogging.KotlinLogging
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBCompositeColorConverter
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.DeserializerFactory
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier

/**
 * State logic was removed. The class exists now only to be found reflectively for migration on deserialization.
 * Currently, deserializes to [ConnectomicsRawState] with a [ChannelComposition].
 */
@Deprecated("A channel source is now ConnectomicsRawState with a ChannelComposition")
class ConnectomicsChannelState private constructor() {

	class Deserializer(
		private val queue: SharedQueue,
		private val priority: Int
	) : PainteraSerialization.PainteraDeserializer<SourceState<*, *>> {

		@Plugin(type = DeserializerFactory::class)
		class Factory : DeserializerFactory<SourceState<*, *>, Deserializer> {
			override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>
			): Deserializer = Deserializer(arguments.viewer.queue, 0)

			@Suppress("UNCHECKED_CAST", "DEPRECATION")
			override fun getTargetClass() = ConnectomicsChannelState::class.java as Class<SourceState<*, *>>
		}

		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SourceState<*, *> = deserializeChannelState<Nothing, Nothing>(context, json)

		/* the removed backend's container and dataset open a raw backend; its channel fields and converter become the composite */
		private fun <D, T> deserializeChannelState(context: JsonDeserializationContext, json: JsonElement): ConnectomicsRawState<*, *>
			where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {
			val backendJson = (json as? JsonObject)?.get(BACKEND_KEY) as? JsonObject
			if (backendJson?.get<String>(TYPE_KEY) != BACKEND_TYPE)
				throw JsonParseException("expected a $BACKEND_TYPE backend")
			val backendData = backendJson.getAsJsonObject(DATA_KEY)
			val container = N5Helpers.deserializeFrom(backendData)
			val backend = N5BackendRaw<D, T>(MetadataUtils.createMetadataState(container, backendData.get<String>(DATASET_KEY)!!)!!)
			return ConnectomicsRawState.Deserializer.rawState(context, json, backend, queue, priority).apply {
				val channels = channels ?: throw JsonParseException("${backend.dataset} has no channel axis to composite")
				val channelIndex = backendData.get<Int>(CHANNEL_INDEX_KEY) ?: DEFAULT_CHANNEL_INDEX
				if (channelIndex != channels.axis)
					LOG.warn { "saved channel index $channelIndex differs from the dataset's channel axis ${channels.axis}; using ${channels.axis}" }
				channels.activeChannels = backendData.getAsJsonArray(CHANNELS_KEY).map { it.asInt }
				context.fromClassInfo<ARGBCompositeColorConverter<T, RealComposite<T>, VolatileWithSet<RealComposite<T>>>>(json, CONVERTER_KEY) {
					channels.restoreConverter(it)
				}
			}
		}

		@Suppress("UNCHECKED_CAST", "DEPRECATION")
		override fun getTargetClass() = ConnectomicsChannelState::class.java as Class<SourceState<*, *>>
	}

	companion object {
		private val LOG = KotlinLogging.logger { }

		const val BACKEND_TYPE = "org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel"

		private const val TYPE_KEY = "type"
		private const val DATA_KEY = "data"
		private const val BACKEND_KEY = "backend"
		private const val DATASET_KEY = "dataset"
		private const val CHANNEL_INDEX_KEY = "channelIndex"
		private const val CHANNELS_KEY = "channels"
		private const val CONVERTER_KEY = "converter"
		private const val DEFAULT_CHANNEL_INDEX = 3
	}
}
