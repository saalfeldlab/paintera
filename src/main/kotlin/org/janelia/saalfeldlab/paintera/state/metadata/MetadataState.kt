package org.janelia.saalfeldlab.paintera.state.metadata

import bdv.util.volatiles.SharedQueue
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.metadata.N5Metadata
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState.Companion.isLabel
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5Utils.getReaderOrWriterIfN5ContainerExists
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleGroup
import java.util.*

interface MetadataState {

    val n5ContainerState: N5ContainerState

    val metadata: N5Metadata
    var datasetAttributes: DatasetAttributes
    var transform: AffineTransform3D
    var isLabel: Boolean
    var isLabelMultiset: Boolean
    var minIntensity: Double
    var maxIntensity: Double
    var resolution: DoubleArray
    var translation: DoubleArray
    var unit: String
    var reader: N5Reader

    var writer: N5Writer?
    var group: String
    val dataset: String
        get() = group

    fun updateTransform(newTransform: AffineTransform3D)
    fun updateTransform(resolution: DoubleArray, offset: DoubleArray)

    fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>>

    fun copy(): MetadataState

    companion object {
        fun isLabel(dataType: DataType): Boolean {
            return when (dataType) {
                DataType.UINT64 -> true
                DataType.UINT32 -> true
                DataType.INT16 -> true
                else -> false
            }
        }

        @JvmStatic
        fun <T : MetadataState> setBy(source: T, target: T) {
            target.transform.set(source.transform)
            target.isLabelMultiset = source.isLabelMultiset
            target.isLabel = source.isLabel
            target.datasetAttributes = source.datasetAttributes
            target.minIntensity = source.minIntensity
            target.maxIntensity = source.maxIntensity
            target.resolution = source.resolution.copyOf()
            target.translation = source.translation.copyOf()
            target.unit = source.unit
            target.reader = source.reader
            target.writer = source.writer
            target.group = source.group
        }
    }
}

open class SingleScaleMetadataState constructor(final override var n5ContainerState: N5ContainerState, final override val metadata: N5SingleScaleMetadata) : MetadataState {
    override var transform: AffineTransform3D = metadata.spatialTransform3d()
    override var isLabelMultiset: Boolean = metadata.isLabelMultiset
    override var isLabel: Boolean = isLabel(metadata.attributes.dataType) || isLabelMultiset
    override var datasetAttributes: DatasetAttributes = metadata.attributes
    override var minIntensity = metadata.minIntensity()
    override var maxIntensity = metadata.maxIntensity()
    override var resolution = metadata.pixelResolution!!
    override var translation = metadata.offset!!
    override var unit = metadata.unit()!!
    override var reader = n5ContainerState.reader
    override var writer = n5ContainerState.writer
    override var group = metadata.path!!

    override fun copy(): SingleScaleMetadataState {
        return SingleScaleMetadataState(n5ContainerState, metadata).also {
            MetadataState.setBy(this, it)
        }
    }

    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
        updateTransform(newTransform)
    }

    override fun updateTransform(newTransform: AffineTransform3D) {

        val deltaTransform = newTransform.copy().concatenate(transform.inverse().copy())
        transform.concatenate(deltaTransform)
        this@SingleScaleMetadataState.resolution = doubleArrayOf(transform.get(0, 0), transform.get(1, 1), transform.get(2, 2))
        this@SingleScaleMetadataState.translation = transform.translation
    }

    override fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> {
        return arrayOf(
            if (isLabelMultiset) {
                N5Data.openLabelMultiset(this, queue, priority)
            } else {
                N5Data.openRaw(this, queue, priority)
            }
        ) as Array<ImagesWithTransform<D, T>>
    }
}


open class MultiScaleMetadataState constructor(override val n5ContainerState: N5ContainerState, final override val metadata: SpatialMultiscaleMetadata<N5SingleScaleMetadata>) : MetadataState by SingleScaleMetadataState(n5ContainerState, metadata[0]) {

    private val highestResMetadata: N5SingleScaleMetadata = metadata[0]
    override var transform: AffineTransform3D = metadata.spatialTransform3d()
    override var isLabel: Boolean = isLabel(highestResMetadata.attributes.dataType) || isLabelMultiset
    override var resolution: DoubleArray = transform.run { doubleArrayOf(get(0, 0), get(1, 1), get(2, 2)) }
    override var translation: DoubleArray = transform.translation
    override var group: String = metadata.path
    override val dataset: String = metadata.path
    val scaleTransforms: Array<AffineTransform3D> = metadata.spatialTransforms3d()

    override fun copy(): MultiScaleMetadataState {
        return MultiScaleMetadataState(n5ContainerState, metadata).also {
            MetadataState.setBy(this, it)
            scaleTransforms.forEachIndexed { idx, transform ->
                transform.set(it.scaleTransforms[idx])
            }
        }
    }

    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
        updateTransform(newTransform)
    }

    override fun updateTransform(newTransform: AffineTransform3D) {
        val deltaTransform = newTransform.copy().concatenate(transform.inverse().copy())
        transform.concatenate(deltaTransform)
        this@MultiScaleMetadataState.resolution = doubleArrayOf(transform.get(0, 0), transform.get(1, 1), transform.get(2, 2))
        this@MultiScaleMetadataState.translation = transform.translation

        scaleTransforms.forEach { it.concatenate(deltaTransform) }

    }


    override fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> {
        return if (isLabelMultiset) {
            N5Data.openLabelMultisetMultiscale(this, queue, priority)
        } else {
            N5Data.openRawMultiscale(this, queue, priority)
        } as Array<ImagesWithTransform<D, T>>
    }
}

class PainteraDataMultiscaleMetadataState constructor(n5ContainerState: N5ContainerState, var painteraDataMultiscaleMetadata: N5PainteraDataMultiScaleGroup) : MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata) {

    val dataMetadataState = MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata.dataGroupMetadata)

    override fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> {
        return if (isLabelMultiset) {
            N5Data.openLabelMultisetMultiscale(dataMetadataState, queue, priority)
        } else {
            N5Data.openRawMultiscale(dataMetadataState, queue, priority)
        } as Array<ImagesWithTransform<D, T>>
    }

    override fun copy(): PainteraDataMultiscaleMetadataState {
        return PainteraDataMultiscaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata).also {
            MetadataState.setBy(this, it)
        }
    }
}

operator fun <T> SpatialMultiscaleMetadata<T>.get(index: Int): T where T : N5SpatialDatasetMetadata {
    return childrenMetadata[index]
}

class MetadataUtils {

    companion object {
        @JvmStatic
        fun metadataIsValid(metadata: N5Metadata?): Boolean {
            /* Valid if we are SpatialMultiscaleMetadata whose children are single scale, or we are SingleScale ourselves. */
            return (metadata as? SpatialMultiscaleMetadata<*>)?.let {
                it.childrenMetadata[0] is N5SingleScaleMetadata
            } ?: run {
                metadata is N5SingleScaleMetadata
            }
        }

        @JvmStatic
        fun createMetadataState(n5ContainerState: N5ContainerState, metadata: N5Metadata?): Optional<MetadataState> {
            @Suppress("UNCHECKED_CAST")
            return Optional.ofNullable(
                (metadata as? N5PainteraDataMultiScaleGroup)?.let { PainteraDataMultiscaleMetadataState(n5ContainerState, it) }
                    ?: (metadata as? SpatialMultiscaleMetadata<N5SingleScaleMetadata>)?.let { MultiScaleMetadataState(n5ContainerState, it) }
                    ?: (metadata as? N5SingleScaleMetadata)?.let { SingleScaleMetadataState(n5ContainerState, it) }
            )
        }

        @JvmStatic
        fun createMetadataState(n5containerAndDataset: String): Optional<MetadataState> {
            val reader = getReaderOrWriterIfN5ContainerExists(n5containerAndDataset) ?: return Optional.empty()
            val writer: N5Writer? = (reader as? N5Writer)

            val n5ContainerState = N5ContainerState(n5containerAndDataset, reader, writer)
            return N5Helpers.parseMetadata(reader).map { treeNode ->
                if (treeNode.isDataset && metadataIsValid(treeNode.metadata) && treeNode.metadata is N5Metadata) {
                    createMetadataState(n5ContainerState, treeNode.metadata).get()
                } else {
                    null
                }
            }
        }

        @JvmStatic
        fun createMetadataState(n5container: String, dataset: String?): Optional<MetadataState> {
            val reader = getReaderOrWriterIfN5ContainerExists(n5container) ?: return Optional.empty()
            val writer: N5Writer? = (reader as? N5Writer)

            val n5ContainerState = N5ContainerState(n5container, reader, writer)
            return N5Helpers.parseMetadata(reader)
                .flatMap { tree: N5TreeNode? -> N5TreeNode.flattenN5Tree(tree).filter { node: N5TreeNode -> node.path == dataset }.findFirst() }
                .filter { node: N5TreeNode -> metadataIsValid(node.metadata) }
                .map { obj: N5TreeNode -> obj.metadata }
                .flatMap { md: N5Metadata? -> createMetadataState(n5ContainerState, md) }
        }

        @JvmStatic
        fun createMetadataState(n5ContainerState: N5ContainerState, dataset: String?): Optional<MetadataState> {
            return N5Helpers.parseMetadata(n5ContainerState.reader)
                .flatMap { tree: N5TreeNode? -> N5TreeNode.flattenN5Tree(tree).filter { node: N5TreeNode -> node.path == dataset || node.path == "/$dataset" }.findFirst() }
                .filter { node: N5TreeNode -> metadataIsValid(node.metadata) }
                .map { obj: N5TreeNode -> obj.metadata }
                .flatMap { md: N5Metadata? -> createMetadataState(n5ContainerState, md) }
        }

        @JvmStatic
        //FIXME Caleb: Temporary bridge between N5Meta and MetadataState!
        fun tmpCreateMetadataState(meta: N5Meta): MetadataState {
            val n5ContainerState = N5ContainerState.tmpFromN5Meta(meta)
            val createMetadataState = createMetadataState(n5ContainerState, meta.dataset)
            return createMetadataState.nullable!!
        }

        @JvmStatic
        //FIXME Caleb: Temporary bridge between N5Meta and MetadataState!
        fun tmpCreateMetadataState(writer: N5Writer, dataset: String): MetadataState {
            val meta = N5Meta.fromReader(writer, dataset)
            return tmpCreateMetadataState(meta)
        }

        fun transformFromResolutionOffset(resolution: DoubleArray, offset: DoubleArray): AffineTransform3D {
            val newTransform = AffineTransform3D()
            newTransform.set(
                resolution[0], 0.0, 0.0, offset[0],
                0.0, resolution[1], 0.0, offset[1],
                0.0, 0.0, resolution[2], offset[2]
            )
            return newTransform
        }
    }
}
