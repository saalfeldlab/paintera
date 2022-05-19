package org.janelia.saalfeldlab.paintera.state.label

import bdv.viewer.Interpolation
import com.google.gson.*
import javafx.scene.paint.Color
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSourceSerializer
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.serialization.assignments.FragmentSegmentAssignmentOnlyLocalSerializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.LabelSourceStateDeserializer
import org.janelia.saalfeldlab.paintera.serialization.sourcestate.SourceStateSerialization
import org.janelia.saalfeldlab.paintera.state.LabelSourceState
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier

class LabelSourceStateFallbackDeserializer<D, T>(
    private val arguments: StatefulSerializer.Arguments,
    private val projectDirectory: Supplier<String>
) : JsonDeserializer<SourceState<*, *>>
    where D : IntegerType<D>,
          D : NativeType<D>,
          T : Volatile<D>,
          T : NativeType<T> {

    private val fallbackDeserializer: LabelSourceStateDeserializer<*> = LabelSourceStateDeserializer.create(arguments)

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SourceState<*, *> {
        return json.getN5MetaAndTransform(context)
            ?.takeIf { (meta, _) ->
                arguments.convertDeprecatedDatasets.let {
                    PainteraAlerts.askConvertDeprecatedStatesShowAndWait(
                        it.convertDeprecatedDatasets,
                        it.convertDeprecatedDatasetsRememberChoice,
                        LabelSourceState::class.java,
                        ConnectomicsLabelState::class.java,
                        json["name"] ?: meta
                    )
                }
            }?.let { (meta, transform) ->
                val (resolution, offset) = transform.toOffsetAndResolution()
                val backend = N5Backend.createFrom<D, T>(
                    MetadataUtils.tmpCreateMetadataState(meta),
                    projectDirectory,
                    arguments.viewer.propagationQueue
                )
                ConnectomicsLabelState(
                    backend,
                    arguments.viewer.viewer3D().meshesGroup(),
                    arguments.viewer.viewer3D().viewFrustumProperty(),
                    arguments.viewer.viewer3D().eyeToWorldTransformProperty(),
                    arguments.meshManagerExecutors,
                    arguments.meshWorkersExecutors,
                    arguments.viewer.queue,
                    0,
                    json["name"] ?: backend.defaultSourceName,
                    resolution,
                    offset
                ).apply {
                    LOG.debug("Successfully converted state {} into {}", json, this)
                    context.fromClassInfo<Composite<ARGBType, ARGBType>>(json.asJsonObject, "compositeType", "composite") { composite = it }
                    // TODO what about other converter properties like user-defined colors?
                    json.get<JsonObject>("converter")?.get<Long>("seed") { converter().seedProperty().set(it) }
                    json.get<JsonObject>("converter")?.get<JsonObject>("userSpecifiedColors") { converter().setCustomColorsFromJson(it) }
                    context.get<Interpolation>(json, "interpolation") { interpolation = it }
                    json.get<Boolean>("isVisible") { isVisible = it }
                    setSelectedIdsTo(json["selectedIds"], context)
                    val assignmentDataActions: JsonArray? = json.get<JsonElement>("assignment")?.get<JsonElement>("data")?.let { it["actions"] }
                    applyActions(assignmentDataActions, context)
                    loadMeshSettings(json["meshSettings"], context)
                    arguments.convertDeprecatedDatasets.wereAnyConverted.value = true
                    context.get<LongArray>(json, "lockedSegments")?.forEach { lockedSegments.lock(it) }
                }
            } ?: run {
            // TODO should this throw an exception instead? could be handled downstream with fall-back and a warning dialog
            LOG.warn(
                "Unable to de-serialize/convert deprecated `{}' into `{}', falling back using `{}'. Support for `{}' has been deprecated and may be removed in the future.",
                LabelSourceState::class.java.simpleName,
                ConnectomicsLabelState::class.java.simpleName,
                LabelSourceStateDeserializer::class.java.simpleName,
                LabelSourceState::class.java.simpleName
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
        ): Pair<N5Meta, AffineTransform3D>? {
            val type = get<String>(typeKey)
            val data = get<JsonObject>(dataKey)
            return if (MaskedSource::class.java.name == type)
                data?.getN5MetaAndTransform(context, MaskedSourceSerializer.UNDERLYING_SOURCE_CLASS_KEY, MaskedSourceSerializer.UNDERLYING_SOURCE_KEY)
            else if (N5DataSource::class.java.name == type)
                Pair(
                    context.deserialize(data?.get(metaKey), Class.forName(data?.get<String>(metaTypeKey))) as N5Meta,
                    context.deserialize(data?.get(transformKey), AffineTransform3D::class.java)
                )
            else
                null
        }

        private fun AffineTransform3D.toOffsetAndResolution() = DoubleArray(3) { this[it, it] } to DoubleArray(3) { this[it, 3] }

        private fun ConnectomicsLabelState<*, *>.setSelectedIdsTo(json: JsonElement?, context: JsonDeserializationContext) {
            this.selectedIds.deactivateAll()
            json?.let {
                context.get<LongArray>(it, "activeIds") { ids -> selectedIds.activate(*ids) }
                it.get<Long>("lastSelection") { id -> selectedIds.activateAlso(id) }
            }
        }

        private fun ConnectomicsLabelState<*, *>.applyActions(json: JsonArray?, context: JsonDeserializationContext) {
            json?.map { it.asJsonObject }
                ?.map { context.fromClassInfo<AssignmentAction>(it, FragmentSegmentAssignmentOnlyLocalSerializer.TYPE_KEY, FragmentSegmentAssignmentOnlyLocalSerializer.DATA_KEY) }
                ?.let { fragmentSegmentAssignment.apply(it) }
        }

        private fun ConnectomicsLabelState<*, *>.loadMeshSettings(json: JsonObject?, context: JsonDeserializationContext) {
            json?.let { meshManager.managedSettings.set(context[it]) }
        }

        private fun HighlightingStreamConverter<*>.setCustomColorsFromJson(json: JsonObject) {
            json.entrySet().forEach { (k, v) ->
                try {
                    this.setColor(k.toLong(), Color.web(v.asString))
                } catch (e: Exception) {
                    LOG.debug("Unable to set custom color {} for id {}: {}", k, v, e.message, e)
                }
            }
        }
    }


    @Plugin(type = StatefulSerializer.DeserializerFactory::class)
    class Factory<D, T> : StatefulSerializer.DeserializerFactory<SourceState<*, *>, LabelSourceStateFallbackDeserializer<D, T>>
        where D : IntegerType<D>,
              D : NativeType<D>,
              T : Volatile<D>,
              T : NativeType<T> {
        override fun createDeserializer(
            arguments: StatefulSerializer.Arguments,
            projectDirectory: Supplier<String>,
            dependencyFromIndex: IntFunction<SourceState<*, *>>
        ): LabelSourceStateFallbackDeserializer<D, T> = LabelSourceStateFallbackDeserializer(arguments, projectDirectory)

        override fun getTargetClass(): Class<SourceState<*, *>> = LabelSourceState::class.java as Class<SourceState<*, *>>

    }
}

