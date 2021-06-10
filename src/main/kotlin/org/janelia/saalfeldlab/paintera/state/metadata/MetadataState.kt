package org.janelia.saalfeldlab.paintera.state.metadata

import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata
import org.janelia.saalfeldlab.n5.metadata.N5DatasetMetadata
import org.janelia.saalfeldlab.n5.metadata.N5Metadata
import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.metadata.SpatialMetadata
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState.Companion.isLabel
import java.util.Optional

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
            (metadata as? MultiscaleMetadata<N5SingleScaleMetadata>)?.let {
                return Optional.of(MultiScaleMetadataState(n5ContainerState, it))
            } ?: run {
                if (metadata is N5SingleScaleMetadata) return Optional.of(SingleScaleMetadataState(n5ContainerState, metadata))
            }
            return Optional.empty()
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

interface MetadataState {

    val n5ContainerState: N5ContainerState

    val metadata: N5Metadata
    val datasetAttributes: DatasetAttributes
    val transform: AffineTransform3D
    val isLabel: Boolean
    val isLabelMultiset: Boolean
    val minIntensity: Double
    val maxIntensity: Double
    val pixelResolution: DoubleArray
    val offset: DoubleArray
    val reader: N5Reader

    val writer: Optional<N5Writer>
    val group: String

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

class SingleScaleMetadataState constructor(override val n5ContainerState: N5ContainerState, override var metadata: N5SingleScaleMetadata) : MetadataState {
    override val transform: AffineTransform3D
        get() = metadata.spatialTransform3d()
    override val isLabelMultiset: Boolean
        get() = metadata.isLabelMultiset
    override val isLabel: Boolean
        get() = isLabel(metadata.attributes.dataType) || isLabelMultiset
    override val datasetAttributes: DatasetAttributes
        get() = metadata.attributes
    override val minIntensity
        get() = metadata.minIntensity()
    override val maxIntensity
        get() = metadata.maxIntensity()
    override val pixelResolution
        get() = metadata.pixelResolution!!
    override val offset
        get() = metadata.offset!!
    override val reader = n5ContainerState.reader
    override val writer = n5ContainerState.getWriter()
    override val group
        get() = metadata.path!!

    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        val newMetadata = with(metadata) {
            val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
            N5SingleScaleMetadata(path, newTransform, downsamplingFactors, resolution, offset, unit(), attributes)
        }
        this.metadata = newMetadata
    }

    override fun updateTransform(newTransform: AffineTransform3D) {
        val newMetadata = with(metadata) {
            N5SingleScaleMetadata(path, newTransform, downsamplingFactors, pixelResolution, offset, unit(), attributes)
        }
        this.metadata = newMetadata
    }
}


class MultiScaleMetadataState constructor(override val n5ContainerState: N5ContainerState, override var metadata: MultiscaleMetadata<N5SingleScaleMetadata>) : MetadataState by SingleScaleMetadataState(n5ContainerState, metadata[0]) {
    private val highestResMetadata: N5SingleScaleMetadata
        get() = metadata[0]
    override val transform: AffineTransform3D
        get() = metadata.spatialTransforms3d()[0]
    override val isLabel: Boolean = isLabel(highestResMetadata.attributes.dataType) || isLabelMultiset
    override val group: String
        get() = metadata.path!!

    override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
        this.metadata = with(metadata) {
            val resultTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
            val conversionTransform = childrenMetadata[0].spatialTransform3d().inverse().copy()
            conversionTransform.concatenate(resultTransform)
            applyTransformToNewMetadataCopy(metadata, conversionTransform)
        }
    }

    override fun updateTransform(newTransform: AffineTransform3D) {
        metadata = with(metadata) {
            val resultTransform = newTransform
            val conversionTransform = childrenMetadata[0].spatialTransform3d().inverse().concatenate(resultTransform)
            applyTransformToNewMetadataCopy(metadata, conversionTransform)
        }
    }

    companion object {
        fun applyTransformToNewMetadataCopy(metadata: MultiscaleMetadata<N5SingleScaleMetadata>, applyTtransform: AffineTransform3D): MultiscaleMetadata<N5SingleScaleMetadata> {
            val newChildrenMetadata = mutableListOf<N5SingleScaleMetadata>();
            metadata.childrenMetadata.forEach {
                with(it) {
                    val updatedTransform = it.spatialTransform3d().copy().concatenate(applyTtransform)
                    val newChildMetadata = N5SingleScaleMetadata(path, updatedTransform, downsamplingFactors, pixelResolution, offset, unit(), attributes)
                    newChildrenMetadata += newChildMetadata
                }
            }

            return N5MultiScaleMetadata(metadata.path, *newChildrenMetadata.toTypedArray())
        }
    }
}

operator fun <T> MultiscaleMetadata<T>.get(index: Int): T where T : N5DatasetMetadata, T : SpatialMetadata {
    return childrenMetadata[index];
}
