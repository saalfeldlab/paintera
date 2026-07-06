package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.n5.N5KeyValueWriter
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.control.actions.state.PainteraActionState
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.io.File

private val LOG = KotlinLogging.logger { }

/**
 * A [CreateDatasetModel] pre-populated from the current source.
 */
private fun fromPaintera(): CreateDatasetModel {

    val sourceInfo = paintera.baseView.sourceInfo()
    val currentSource = sourceInfo.currentSourceProperty().get()
    val sourceStates = sourceInfo.trackSources().map { sourceInfo.getState(it) }
    return DefaultCreateDatasetModel(currentSource, sourceStates).also { model ->
        runCatching {
            currentSource?.let { model.populateFrom(it) }
        }.onFailure {
            LOG.warn(it) { "Unable to populate create-dataset dialog from current source ${currentSource?.name}" }
        }
        model.container = File(paintera.projectDirectory.actualDirectory.absolutePath)
    }
}

internal class CreateDatasetActionState(delegate: CreateDatasetModel = fromPaintera()) :
    PainteraActionState(),
    CreateDatasetModel by delegate {

    /**
     * Create a new label dataset from the current inputs and return its [MetadataState]. Throws if
     * the container cannot be written, or if a label multiset dataset is requested for a non-N5 container.
     */
    fun create(): MetadataState? {
        val container = container ?: throw IllegalStateException("Container not specified")
        LOG.debug { "Trying to create empty label dataset `$dataset' in container `$container'" }
        val writer = n5Factory.newWriter(container.absolutePath)

        if (labelMultiset && writer !is N5KeyValueWriter)
            throw UnsupportedOperationException("LabelMultisetType Label dataset only supported for N5 datasets")

        N5Data.createPainteraLabelDataset(
            writer = writer,
            group = dataset,
            dimensions = ndDimensions(),
            blockSize = ndBlockSize(),
            resolution = ndResolution(),
            offset = ndOffset(),
            relativeScaleFactors = downsamplingFactors(),
            unit = xUnitProperty.value,
            maxNumEntries = maxNumEntries(),
            labelMultisetType = labelMultiset,
            overwrite = false,
            axes = axes()
        )

        return N5Helpers.parseMetadata(writer, dataset, ignoreCache = true)?.let { (containerState, metadataNode) ->
            createMetadataState(containerState, dataset, metadataNode)
        }
    }
}
