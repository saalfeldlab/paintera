package org.janelia.saalfeldlab.paintera.state.channel

import bdv.viewer.Interpolation
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.ChannelSourceStateDeserializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.LabelSourceStateDeserializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.SourceStateSerialization
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier

class ChannelSourceStateFallbackDeserializer<D, T>(private val arguments: StatefulSerializer.Arguments) : JsonDeserializer<SourceState<*, *>>
    where D : RealType<D>,
          D : NativeType<D>,
          T : AbstractVolatileRealType<D, T>,
          T : NativeType<T> {

    private val fallbackDeserializer: ChannelSourceStateDeserializer = ChannelSourceStateDeserializer()

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SourceState<*, *> {
        return json.getN5MetaAndTransform(context)
            ?.takeIf { (meta, _) ->
                arguments.convertDeprecatedDatasets.let {
                    PainteraAlerts.askConvertDeprecatedStatesShowAndWait(
                        it.convertDeprecatedDatasets,
                        it.convertDeprecatedDatasetsRememberChoice,
                        ChannelSourceState::class.java,
                        ConnectomicsChannelState::class.java,
                        json["name"] ?: meta
                    )
                }
            }
            ?.let { (meta, transform) ->
                with(GsonExtensions) {
                    val (resolution, offset) = transform.toOffsetAndResolution()
                    val sourceJson: JsonObject = json["source"]!!
                    val channels = context.get<IntArray>(sourceJson, "channels")!!
                    val channelIndex: Int = (sourceJson as JsonElement)["channelDimension"] ?: 3
                    val backend = N5BackendChannel<D, T>(MetadataUtils.tmpCreateMetadataState(meta.writer!!, meta.dataset), channels, channelIndex)
                    ConnectomicsChannelState(
                        backend,
                        arguments.viewer.queue,
                        0,
                        json["name"] ?: backend.name,
                        context[json, "converter"]!!
                    ).apply {
                        LOG.debug("Successfully converted state {} into {}", json, this)

                        context.fromClassInfo<Composite<ARGBType, ARGBType>>(json.asJsonObject, "compositeType", "composite")?.let { composite = it }

                        // TODO what about other converter properties like user-defined colors?
                        context.get<Interpolation>(json, "interpolation") { interpolation = it }
                        json.get<Boolean>("isVisible") { isVisible = it }
                        arguments.convertDeprecatedDatasets.wereAnyConverted.value = true
                    }
                }
            } ?: run {
            // TODO should this throw an exception instead? could be handled downstream with fall-back and a warning dialog
            LOG.warn(
                "Unable to de-serialize/convert deprecated `{}' into `{}', falling back using `{}'. Support for `{}' has been deprecated and may be removed in the future.",
                ChannelSourceState::class.java.simpleName,
                ConnectomicsChannelState::class.java.simpleName,
                LabelSourceStateDeserializer::class.java.simpleName,
                ChannelSourceState::class.java.simpleName
            )
            fallbackDeserializer.deserialize(json, typeOfT, context)
        }
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun JsonElement.getN5MetaAndTransform(
            context: JsonDeserializationContext,
            typeKey: String = SourceStateSerialization.SOURCE_TYPE_KEY,
            dataKey: String = SourceStateSerialization.SOURCE_KEY,
            metaTypeKey: String = "metaType",
            metaKey: String = "meta",
            transformKey: String = "transform",
        ): Pair<N5Meta, AffineTransform3D>? = with(GsonExtensions) {
            val type = getStringProperty(typeKey)
            val data = getJsonObject(dataKey)
            if (N5ChannelDataSource::class.java.name == type) {
                val meta = context.deserialize(data?.get(metaKey), Class.forName(data?.getStringProperty(metaTypeKey))) as N5Meta
                val transform = context.deserialize<AffineTransform3D>(data?.get(transformKey), AffineTransform3D::class.java)
                meta to transform
            } else
                null
        }

        private fun AffineTransform3D.toOffsetAndResolution() = Pair(
            DoubleArray(3) { this[it, it] },
            DoubleArray(3) { this[it, 3] })
    }


    @Plugin(type = StatefulSerializer.DeserializerFactory::class)
    class Factory<D, T> : StatefulSerializer.DeserializerFactory<SourceState<*, *>, ChannelSourceStateFallbackDeserializer<D, T>>
        where D : RealType<D>,
              D : NativeType<D>,
              T : AbstractVolatileRealType<D, T>,
              T : NativeType<T> {
        override fun createDeserializer(
            arguments: StatefulSerializer.Arguments,
            projectDirectory: Supplier<String>,
            dependencyFromIndex: IntFunction<SourceState<*, *>>,
        ): ChannelSourceStateFallbackDeserializer<D, T> =
            ChannelSourceStateFallbackDeserializer(arguments)

        override fun getTargetClass(): Class<SourceState<*, *>> = ChannelSourceState::class.java as Class<SourceState<*, *>>

    }
}

