package org.janelia.saalfeldlab.paintera.state.metadata

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata
import org.janelia.saalfeldlab.n5.metadata.N5Metadata
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata
import java.util.Optional

abstract class MetadataState<T> constructor(open val n5ContainerState: N5ContainerState, open val metadata: T)
    where T : N5Metadata {
    val metadataProperty: ObservableValue<T> by lazy { SimpleObjectProperty(metadata) }
    abstract val datasetAttributes: DatasetAttributes
    abstract val transform: AffineTransform3D
    abstract val isLabel: Boolean
    abstract val isLabelMultiset: Boolean

    val reader
        get() = n5ContainerState.reader
    val writer
        get() = n5ContainerState.getWriter()
    val group
        get() = metadata.path!!

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
        fun metadataIsValid(metadata: N5Metadata?): Optional<N5Metadata> {
            val validMetaOrNull = (metadata as? MultiscaleMetadata<N5SingleScaleMetadata>) ?: run {
                if (metadata is N5SingleScaleMetadata) metadata else null
            }
            return Optional.ofNullable(validMetaOrNull)
        }

        @JvmStatic
        fun createMetadataState(n5ContainerState: N5ContainerState, metadata: N5Metadata?): Optional<MetadataState<*>> {
            (metadata as? MultiscaleMetadata<N5SingleScaleMetadata>)?.let {
                return Optional.of(MulticScaleMetadataState(n5ContainerState, it))
            } ?: run {
                if (metadata is N5SingleScaleMetadata) return Optional.of(SingleScaleMetadataState(n5ContainerState, metadata))
            }
            return Optional.empty()
        }
    }
}

class SingleScaleMetadataState constructor(override val n5ContainerState: N5ContainerState, override val metadata: N5SingleScaleMetadata) : MetadataState<N5SingleScaleMetadata>(n5ContainerState, metadata) {
    override val transform: AffineTransform3D = metadata.spatialTransform3d()
    override val isLabel: Boolean = isLabel(metadata.attributes.dataType)
    override val isLabelMultiset: Boolean = metadata.isLabelMultiset
    override val datasetAttributes: DatasetAttributes = metadata.attributes
}


class MulticScaleMetadataState constructor(override val n5ContainerState: N5ContainerState, override val metadata: MultiscaleMetadata<N5SingleScaleMetadata>) : MetadataState<MultiscaleMetadata<*>>(n5ContainerState, metadata) {
    private val highestResMetadata: N5SingleScaleMetadata = metadata.childrenMetadata[0]
    override val transform: AffineTransform3D = metadata.spatialTransforms3d()[0]
    override val isLabel: Boolean = isLabel(highestResMetadata.attributes.dataType)
    override val isLabelMultiset: Boolean = highestResMetadata.isLabelMultiset
    override val datasetAttributes: DatasetAttributes = highestResMetadata.attributes
}
