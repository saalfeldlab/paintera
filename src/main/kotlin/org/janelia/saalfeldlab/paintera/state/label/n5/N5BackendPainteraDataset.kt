package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.util.volatiles.SharedQueue
import com.google.gson.*
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.extensions.UtilityExtensions.Companion.nullable
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.n5.IsRelativeToContainer
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentStateWithActionTracker
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceMetadata
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.withClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.raw.n5.urlRepresentation
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.nio.file.Paths
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction
import java.util.function.Supplier

class N5BackendPainteraDataset<D, T> constructor(
    private val metadataState: MetadataState,
    private val projectDirectory: Supplier<String>,
    private val propagationExecutorService: ExecutorService,
    private val backupLookupAttributesIfMakingRelative: Boolean,
) : N5Backend<D, T>
    where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

    override val container: N5Reader = metadataState.reader
    override val dataset: String = metadataState.dataset

    override fun createSource(
        queue: SharedQueue,
        priority: Int,
        name: String,
        resolution: DoubleArray,
        offset: DoubleArray,
    ): DataSource<D, T> {
        return makeSource(
            metadataState,
            queue,
            priority,
            name,
            projectDirectory,
            propagationExecutorService
        )
    }

    //FIXME Caleb: should use metadata; AND we should expect the correct metadata type (not just MetadataState)
    override val fragmentSegmentAssignment: FragmentSegmentAssignmentStateWithActionTracker
        get() {
            return metadataState.writer.nullable?.let {
                N5Helpers.assignments(it, dataset)!!
            } ?: FragmentSegmentAssignmentOnlyLocal(FragmentSegmentAssignmentOnlyLocal.DoesNotPersist())
        }
    override val providesLookup = true

    //FIXME Caleb: same as above with idService
    override fun createIdService(source: DataSource<D, T>): IdService {
        return metadataState.writer.nullable?.let {
            N5Helpers.idService(it, dataset)!!
        } ?: IdService.IdServiceNotProvided()
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun persistError(dataset: String) = "Persisting assignments not supported for non Paintera dataset $dataset."

        private fun <D, T> makeSource(
            metadataState: MetadataState,
            queue: SharedQueue,
            priority: Int,
            name: String,
            projectDirectory: Supplier<String>,
            propagationExecutorService: ExecutorService,
        ): DataSource<D, T> where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
            val dataSource = N5DataSourceMetadata<D, T>(metadataState, name, queue, priority)
            val containerWriter = metadataState.n5ContainerState.writer
            return containerWriter?.let {
                val tmpDir = Masks.canvasTmpDirDirectorySupplier(projectDirectory)
                Masks.maskedSource(dataSource, queue, tmpDir.get(), tmpDir, CommitCanvasN5(metadataState), propagationExecutorService)
            } ?: dataSource
        }


        private object MakeLabelBlockLookupRelativeConstants {
            const val LABEL_BLOCK_LOOKUP = "labelBlockLookup"
            const val N5_FILESYSTEM = "n5-filesystem"
            const val N5_FILESYSTEM_RELATIVE = "n5-filesystem-relative"
            const val TYPE = "type"
            const val SLASH = "/"
            const val SCALE_DATASET_PATTERN = "scaleDatasetPattern"
            const val ROOT = "root"
            const val LABEL_TO_BLOCK_MAPPING = "label-to-block-mapping"
        }

        private fun N5FSWriter.makeN5LabelBlockLookupRelative(
            painteraDataset: String,
            backupAttributes: Boolean,
            date: Date = Date(),
            dateFormat: DateFormat = SimpleDateFormat("'.bkp.'yyyy-mm-dd_HH-mm-ss"),
            overwrite: Boolean = false
        ) {
            val constants = MakeLabelBlockLookupRelativeConstants
            val painteraDatasetNoLeadingSlash = painteraDataset.trimSlashStart()
            val labelBlockLookupJson = this.getAttribute(painteraDataset, constants.LABEL_BLOCK_LOOKUP, JsonObject::class.java)
            labelBlockLookupJson
                ?.takeIf { with(GsonExtensions) { constants.N5_FILESYSTEM == it.getStringProperty(constants.TYPE) } }
                ?.takeIf { with(GsonExtensions) { basePath == it.getStringProperty(constants.ROOT) } }
                ?.takeIf {
                    with(GsonExtensions) {
                        it.getStringProperty(constants.SCALE_DATASET_PATTERN)?.trimSlashStart()?.startsWith(painteraDatasetNoLeadingSlash) ?: false
                    }
                }
                ?.let { json ->
                    LOG.warn("Converting deprecated label block lookup format {} into {}.", constants.N5_FILESYSTEM, constants.N5_FILESYSTEM_RELATIVE)
                    if (backupAttributes) {
                        val painteraDatasetAttributes =
                            Paths.get(basePath, *painteraDataset.split("/").toTypedArray()).resolve("attributes.json").toAbsolutePath().toFile()
                        val lookupAttributes =
                            Paths.get(basePath, *painteraDataset.split("/").toMutableList().also { it += constants.LABEL_TO_BLOCK_MAPPING }.toTypedArray())
                                .resolve("attributes.json").toAbsolutePath().toFile()
                        val suffix = dateFormat.format(date)
                        painteraDatasetAttributes.copyTo(File("${painteraDatasetAttributes.absolutePath}$suffix"), overwrite)
                        lookupAttributes.copyTo(File("${lookupAttributes.absolutePath}$suffix"), overwrite)
                    }
                    val oldPattern = with(GsonExtensions) { json.getStringProperty(constants.SCALE_DATASET_PATTERN)!!.trimSlashStart() }
                    val newPattern = oldPattern.replaceFirst(painteraDatasetNoLeadingSlash, "")
                    val newLookupJson = JsonObject().apply {
                        addProperty(constants.TYPE, constants.N5_FILESYSTEM_RELATIVE)
                        addProperty(constants.SCALE_DATASET_PATTERN, newPattern)
                    }
                    setAttribute(painteraDataset, constants.LABEL_BLOCK_LOOKUP, newLookupJson)
                    setAttribute("$painteraDataset/${constants.LABEL_TO_BLOCK_MAPPING}", constants.LABEL_BLOCK_LOOKUP, newLookupJson)
                }
        }

        private fun String.trimSlashStart() = trimStart(*MakeLabelBlockLookupRelativeConstants.SLASH.toCharArray())

    }

    override fun createLabelBlockLookup(source: DataSource<D, T>): LabelBlockLookup {
        val n5FSWriter = metadataState.writer.nullable as? N5FSWriter
        n5FSWriter?.makeN5LabelBlockLookupRelative(dataset, backupLookupAttributesIfMakingRelative)
        return N5Helpers.getLabelBlockLookup(metadataState).also {
            if (it is IsRelativeToContainer && n5FSWriter != null) {
                it.setRelativeTo(n5FSWriter, dataset)
            }
        }
    }

    private object SerializationKeys {
        const val CONTAINER = "container"
        const val DATASET = "dataset"
        const val FRAGMENT_SEGMENT_ASSIGNMENT = "fragmentSegmentAssignment"
    }

    @Plugin(type = PainteraSerialization.PainteraSerializer::class)
    class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendPainteraDataset<D, T>>
        where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

        override fun serialize(
            backend: N5BackendPainteraDataset<D, T>,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonElement {
            val map = JsonObject()
            with(SerializationKeys) {
                map.add(CONTAINER, context.withClassInfo(backend.container))
                map.addProperty(DATASET, backend.dataset)
                map.add(FRAGMENT_SEGMENT_ASSIGNMENT, context[FragmentSegmentAssignmentActions(backend.fragmentSegmentAssignment)])
            }
            return map
        }

        override fun getTargetClass() = N5BackendPainteraDataset::class.java as Class<N5BackendPainteraDataset<D, T>>
    }

    class Deserializer<D, T>(
        private val projectDirectory: Supplier<String>,
        private val propagationExecutorService: ExecutorService
    ) : JsonDeserializer<N5BackendPainteraDataset<D, T>>
        where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

        @Plugin(type = StatefulSerializer.DeserializerFactory::class)
        class Factory<D, T> : StatefulSerializer.DeserializerFactory<N5BackendPainteraDataset<D, T>, Deserializer<D, T>>
            where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
            override fun createDeserializer(
                arguments: StatefulSerializer.Arguments,
                projectDirectory: Supplier<String>,
                dependencyFromIndex: IntFunction<SourceState<*, *>>
            ): Deserializer<D, T> = Deserializer(
                projectDirectory,
                arguments.viewer.propagationQueue
            )

            override fun getTargetClass() = N5BackendPainteraDataset::class.java as Class<N5BackendPainteraDataset<D, T>>
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): N5BackendPainteraDataset<D, T> {
            return with(SerializationKeys) {
                with(GsonExtensions) {
                    val container: N5Reader = context.fromClassInfo(json, CONTAINER)!!
                    val dataset: String = json[DATASET]!!
                    val n5ContainerState = N5ContainerState(container.urlRepresentation(), container, container as? N5Writer)
                    val metadataState = MetadataUtils.createMetadataState(n5ContainerState, dataset).nullable!!
                    N5BackendPainteraDataset<D, T>(
                        metadataState,
                        projectDirectory,
                        propagationExecutorService,
                        true
                    ).also { json.getProperty(FRAGMENT_SEGMENT_ASSIGNMENT)?.asAssignmentActions(context)?.feedInto(it.fragmentSegmentAssignment) }
                }
            }
        }

        companion object {
            private fun JsonElement.asAssignmentActions(context: JsonDeserializationContext) = context
                .deserialize<FragmentSegmentAssignmentActions?>(this, FragmentSegmentAssignmentActions::class.java)
        }
    }

    override fun getMetadataState() = metadataState
}
