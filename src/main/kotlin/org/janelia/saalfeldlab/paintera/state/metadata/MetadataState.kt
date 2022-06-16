package org.janelia.saalfeldlab.paintera.state.metadata

import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.UtilityExtensions.Companion.nullable
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.metadata.*
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState.Companion.isLabel
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5Utils.getReaderOrWriterIfN5ContainerExists
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.util.Optional

interface MetadataState {

    val n5ContainerState: N5ContainerState

    val metadata: N5Metadata
    var datasetAttributes: DatasetAttributes
    var transform: AffineTransform3D
    var isLabel: Boolean
    var isLabelMultiset: Boolean
    var minIntensity: Double
    var maxIntensity: Double
    var pixelResolution: DoubleArray
    var offset: DoubleArray
    var unit: String
    var reader: N5Reader

    var writer: N5Writer?
    var group: String
    val dataset: String
        get() = group

    fun updateTransform(newTransform: AffineTransform3D)
    fun updateTransform(resolution: DoubleArray, offset: DoubleArray)

    companion object {
        fun isLabel(dataType: DataType): Boolean {
            return when (dataType) {
                DataType.UINT64 -> true
                DataType.UINT32 -> true
                DataType.INT16 -> true
                else -> false
            }
        }
    }
}

class SingleScaleMetadataState constructor(override var n5ContainerState: N5ContainerState, override var metadata: N5SingleScaleMetadata) : MetadataState {
    override var transform: AffineTransform3D = metadata.spatialTransform3d()
    override var isLabelMultiset: Boolean = metadata.isLabelMultiset
    override var isLabel: Boolean = isLabel(metadata.attributes.dataType) || isLabelMultiset
    override var datasetAttributes: DatasetAttributes = metadata.attributes
    override var minIntensity = metadata.minIntensity()
    override var maxIntensity = metadata.maxIntensity()
    override var pixelResolution = metadata.pixelResolution!!
    override var offset = metadata.offset!!
    override var unit = metadata.unit()!!
    override var reader = n5ContainerState.reader
    override var writer = n5ContainerState.writer
    override var group = metadata.path!!


    /* FIXME: updateTransform modifies the [metadata] BUT it does not update the valuse that are initialized from the original [metadata].
    *   Think about how to fix it. Naively, we could maek all the vals that get info from [metadata] get()ers, but we may
    *   Want to think more critically if we are concerned about writing back into the label dataset. We may need to retain the original
    *   Values, and/or map them during serialization.*/


    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
        updateTransform(newTransform)
    }

    override fun updateTransform(newTransform: AffineTransform3D) {

        val deltaTransform = newTransform.copy().concatenate(transform.inverse().copy())
        transform.concatenate(deltaTransform)
        this@SingleScaleMetadataState.pixelResolution = doubleArrayOf(transform.get(0, 0), transform.get(1, 1), transform.get(2, 2))
        this@SingleScaleMetadataState.offset = transform.translation
    }

}


class MultiScaleMetadataState constructor(override val n5ContainerState: N5ContainerState, override var metadata: MultiscaleMetadata<N5SingleScaleMetadata>) : MetadataState by SingleScaleMetadataState(n5ContainerState, metadata[0]) {
    private val highestResMetadata: N5SingleScaleMetadata = metadata[0]
    override var transform: AffineTransform3D = metadata.spatialTransforms3d()[0]
    override var isLabel: Boolean = isLabel(highestResMetadata.attributes.dataType) || isLabelMultiset
    override var group: String = metadata.path
    override val dataset: String = metadata.path
    val scaleTransforms: Array<AffineTransform3D> get() = metadata.spatialTransforms3d()

    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
        updateTransform(newTransform)
    }

    override fun updateTransform(newTransform: AffineTransform3D) {
        val deltaTransform = newTransform.copy().concatenate(transform.inverse().copy())
        transform.concatenate(deltaTransform)
        this@MultiScaleMetadataState.pixelResolution = doubleArrayOf(transform.get(0, 0), transform.get(1, 1), transform.get(2, 2))
        this@MultiScaleMetadataState.offset = transform.translation

        scaleTransforms.forEach { it.concatenate(deltaTransform) }
    }
}

operator fun <T> MultiscaleMetadata<T>.get(index: Int): T where T : N5DatasetMetadata, T : SpatialMetadata {
    return childrenMetadata[index]
}

class MetadataUtils {

    companion object {
        @JvmStatic
        fun metadataIsValid(metadata: N5Metadata?): Boolean {
            /* Valid if we are MultiscaleMetadata whose children are single scale, or we are SingleScale ourselves. */
            return (metadata as? MultiscaleMetadata<*>)?.let {
                it.childrenMetadata[0] is N5SingleScaleMetadata
            } ?: run {
                metadata is N5SingleScaleMetadata
            }
        }

        @JvmStatic
        fun createMetadataState(n5ContainerState: N5ContainerState, metadata: N5Metadata?): Optional<MetadataState> {
            @Suppress("UNCHECKED_CAST")
            (metadata as? MultiscaleMetadata<N5SingleScaleMetadata>)?.let {
                return Optional.of(MultiScaleMetadataState(n5ContainerState, it))
            } ?: run {
                if (metadata is N5SingleScaleMetadata) return Optional.of(SingleScaleMetadataState(n5ContainerState, metadata))
            }
            return Optional.empty()
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
            //TODO Caleb: Ask Jon if it's reasonable to allow the dataset to either contain a leading '/' or not
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
