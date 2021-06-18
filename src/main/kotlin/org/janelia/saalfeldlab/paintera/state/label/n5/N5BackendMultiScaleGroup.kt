package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.util.volatiles.SharedQueue
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction
import java.util.function.Supplier

class N5BackendMultiScaleGroup<D, T> constructor(
    override val container: N5Writer,
    override val dataset: String,
    private val projectDirectory: Supplier<String>,
    private val propagationExecutorService: ExecutorService
) : N5Backend<D, T>
    where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

    override fun createSource(
        queue: SharedQueue,
        priority: Int,
        name: String,
        resolution: DoubleArray,
        offset: DoubleArray
    ): DataSource<D, T> {
        return makeSource<D, T>(
            container,
            dataset,
            N5Helpers.fromResolutionAndOffset(resolution, offset),
            queue,
            priority,
            name,
            projectDirectory,
            propagationExecutorService
        )
    }

    override val fragmentSegmentAssignment = FragmentSegmentAssignmentOnlyLocal(
        FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE,
        FragmentSegmentAssignmentOnlyLocal.doesNotPersist(persistError(dataset))
    )

    override fun createIdService(source: DataSource<D, T>) = N5Helpers.idService(
        container,
        dataset,
        Supplier { PainteraAlerts.getN5IdServiceFromData(container, dataset, source) })!!

    override fun createLabelBlockLookup(source: DataSource<D, T>) = PainteraAlerts.getLabelBlockLookupFromN5DataSource(container, dataset, source)!!

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun persistError(dataset: String) = "Persisting assignments not supported for non Paintera dataset $dataset."

        private fun <D, T> makeSource(
            container: N5Reader,
            dataset: String,
            transform: AffineTransform3D,
            queue: SharedQueue,
            priority: Int,
            name: String,
            projectDirectory: Supplier<String>,
            propagationExecutorService: ExecutorService
        ): DataSource<D, T>
            where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
            val dataSource = N5DataSource<D, T>(N5Meta.fromReader(container, dataset), transform, name, queue, priority)
            return if (container is N5Writer) {
                val tmpDir = Masks.canvasTmpDirDirectorySupplier(projectDirectory)
                Masks.mask(dataSource, queue, tmpDir.get(), tmpDir, CommitCanvasN5(container, dataset), propagationExecutorService)
            } else
                dataSource
        }

    }

    private object SerializationKeys {
        const val CONTAINER = "container"
        const val DATASET = "dataset"
        const val FRAGMENT_SEGMENT_ASSIGNMENT = "fragmentSegmentAssignment"
    }

    @Plugin(type = PainteraSerialization.PainteraSerializer::class)
    class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendMultiScaleGroup<D, T>>
        where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

        override fun serialize(
            backend: N5BackendMultiScaleGroup<D, T>,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val map = JsonObject()
            with(SerializationKeys) {
                map.add(CONTAINER, SerializationHelpers.serializeWithClassInfo(backend.container, context))
                map.addProperty(DATASET, backend.dataset)
                map.add(FRAGMENT_SEGMENT_ASSIGNMENT, context.serialize(FragmentSegmentAssignmentActions(backend.fragmentSegmentAssignment)))
            }
            return map
        }

        override fun getTargetClass() = N5BackendMultiScaleGroup::class.java as Class<N5BackendMultiScaleGroup<D, T>>
    }

    class Deserializer<D, T>(
        private val projectDirectory: Supplier<String>,
        private val propagationExecutorService: ExecutorService
    ) : JsonDeserializer<N5BackendMultiScaleGroup<D, T>>
        where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

        @Plugin(type = StatefulSerializer.DeserializerFactory::class)
        class Factory<D, T> : StatefulSerializer.DeserializerFactory<N5BackendMultiScaleGroup<D, T>, Deserializer<D, T>>
            where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
            override fun createDeserializer(
                arguments: StatefulSerializer.Arguments,
                projectDirectory: Supplier<String>,
                dependencyFromIndex: IntFunction<SourceState<*, *>>
            ): Deserializer<D, T> = Deserializer(
                projectDirectory,
                arguments.viewer.propagationQueue
            )

            override fun getTargetClass() = N5BackendMultiScaleGroup::class.java as Class<N5BackendMultiScaleGroup<D, T>>
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): N5BackendMultiScaleGroup<D, T> {
            return with(SerializationKeys) {
                with(GsonExtensions) {
                    N5BackendMultiScaleGroup<D, T>(
                        SerializationHelpers.deserializeFromClassInfo(json.getJsonObject(CONTAINER)!!, context),
                        json.getStringProperty(DATASET)!!,
                        projectDirectory,
                        propagationExecutorService
                    )
                        .also { json.getProperty(FRAGMENT_SEGMENT_ASSIGNMENT)?.asAssignmentActions(context)?.feedInto(it.fragmentSegmentAssignment) }
                }
            }
        }

        companion object {
            private fun JsonElement.asAssignmentActions(context: JsonDeserializationContext) = context
                .deserialize<FragmentSegmentAssignmentActions?>(this, FragmentSegmentAssignmentActions::class.java)
        }
    }
}
