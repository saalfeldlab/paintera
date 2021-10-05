package org.janelia.saalfeldlab.paintera.state.raw

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
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.getStringProperty
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.LabelSourceStateDeserializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.RawSourceStateDeserializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.SourceStateSerialization
import org.janelia.saalfeldlab.paintera.state.RawSourceState
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.Colors
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier

class RawSourceStateFallbackDeserializer<D, T>(private val arguments: StatefulSerializer.Arguments) : JsonDeserializer<SourceState<*, *>>
    where D : RealType<D>,
          D : NativeType<D>,
          T : AbstractVolatileRealType<D, T>,
          T : NativeType<T> {

    private val fallbackDeserializer: RawSourceStateDeserializer = RawSourceStateDeserializer()

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SourceState<*, *> {
        return json.getN5MetaAndTransform(context)
            ?.takeIf { (meta, _) ->
                arguments.convertDeprecatedDatasets.let {
                    PainteraAlerts.askConvertDeprecatedStatesShowAndWait(
                        it.convertDeprecatedDatasets,
                        it.convertDeprecatedDatasetsRememberChoice,
                        RawSourceState::class.java,
                        ConnectomicsRawState::class.java,
                        json.getStringProperty("name") ?: meta
                    )
                }
            }
            ?.let { (meta, transform) ->
                val (resolution, offset) = transform.toOffsetAndResolution()
                val backend = N5BackendRaw<D, T>(MetadataUtils.tmpCreateMetadataState(meta.writer!!, meta.dataset))
                ConnectomicsRawState(
                    backend,
                    arguments.viewer.queue,
                    0,
                    with(GsonExtensions) { json.getStringProperty("name") } ?: backend.defaultSourceName,
                    resolution,
                    offset)
                    .also { LOG.debug("Successfully converted state {} into {}", json, it) }
                    .also { s ->
                        SerializationHelpers.deserializeFromClassInfo<Composite<ARGBType, ARGBType>>(
                            json.asJsonObject,
                            context,
                            "compositeType",
                            "composite"
                        )?.let { s.composite = it }
                    }
                    // TODO what about other converter properties like user-defined colors?
                    .also { s -> with(GsonExtensions) { s.updateConverterSettings(json.getJsonObject("converter")) } }
                    .also { s ->
                        with(GsonExtensions) {
                            json.getProperty("interpolation")?.let { context.deserialize<Interpolation>(it, Interpolation::class.java) }
                                ?.let { s.interpolation = it }
                        }
                    }
                    .also { s -> with(GsonExtensions) { json.getBooleanProperty("isVisible") }?.let { s.isVisible = it } }
                    .also { arguments.convertDeprecatedDatasets.wereAnyConverted.value = true }
            } ?: run {
            // TODO should this throw an exception instead? could be handled downstream with fall-back and a warning dialog
            LOG.warn(
                "Unable to de-serialize/convert deprecated `{}' into `{}', falling back using `{}'. Support for `{}' has been deprecated and may be removed in the future.",
                RawSourceState::class.java.simpleName,
                ConnectomicsRawState::class.java.simpleName,
                LabelSourceStateDeserializer::class.java.simpleName,
                RawSourceState::class.java.simpleName
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
            transformKey: String = "transform"
        ): Pair<N5Meta, AffineTransform3D>? = with(GsonExtensions) {
            val type = getStringProperty(typeKey)
            val data = getJsonObject(dataKey)
            if (N5DataSource::class.java.name == type)
                Pair(
                    context.deserialize(data?.get(metaKey), Class.forName(data?.getStringProperty(metaTypeKey))) as N5Meta,
                    context.deserialize(data?.get(transformKey), AffineTransform3D::class.java)
                )
            else
                null
        }

        private fun AffineTransform3D.toOffsetAndResolution() = Pair(
            DoubleArray(3) { this[it, it] },
            DoubleArray(3) { this[it, 3] })

        private fun ConnectomicsRawState<*, *>.updateConverterSettings(json: JsonObject?) = json?.let { j ->
            val c = converter()
            with(GsonExtensions) {
                j.getDoubleProperty("alpha")?.let { c.alphaProperty().value = it }
                j.getDoubleProperty("min")?.let { c.setMin(it) }
                j.getDoubleProperty("max")?.let { c.setMax(it) }
                j.getStringProperty("color")?.let { c.setColor(Colors.toARGBType(it)) }
            }
        }
    }


    @Plugin(type = StatefulSerializer.DeserializerFactory::class)
    class Factory<D, T> : StatefulSerializer.DeserializerFactory<SourceState<*, *>, RawSourceStateFallbackDeserializer<D, T>>
        where D : RealType<D>,
              D : NativeType<D>,
              T : AbstractVolatileRealType<D, T>,
              T : NativeType<T> {
        override fun createDeserializer(
            arguments: StatefulSerializer.Arguments,
            projectDirectory: Supplier<String>,
            dependencyFromIndex: IntFunction<SourceState<*, *>>
        ): RawSourceStateFallbackDeserializer<D, T> =
            RawSourceStateFallbackDeserializer(arguments)

        override fun getTargetClass(): Class<SourceState<*, *>> = RawSourceState::class.java as Class<SourceState<*, *>>

    }
}

