package org.janelia.saalfeldlab.paintera.state.channel.n5

import javafx.scene.Node
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelBackend

interface AbstractN5BackendChannel<D, T> : ConnectomicsChannelBackend<D, T>, SourceStateBackendN5<D, T> {

    override fun createMetaDataNode(): Node {
        val containerLabel = Labels.withTooltip("Container", "N5 container of source dataset `$dataset'")
        val datasetLabel = Labels.withTooltip("Dataset", "Dataset path inside container `${container.urlRepresentation()}'")
        val container = TextField(this.container.urlRepresentation()).also { it.isEditable = false }
        val dataset = TextField(this.dataset).also { it.isEditable = false }
        GridPane.setHgrow(container, Priority.ALWAYS)
        GridPane.setHgrow(dataset, Priority.ALWAYS)
        return GridPane()
            .also { it.hgap = 10.0 }
            .also { it.add(containerLabel, 0, 0) }
            .also { it.add(datasetLabel, 0, 1) }
            .also { it.add(container, 1, 0) }
            .also { it.add(dataset, 1, 1) }
    }

    companion object {
        private fun N5Reader.urlRepresentation() = when (this) {
            is N5FSReader -> "n5://$basePath"
            is N5HDF5Reader -> "h5://${filename.absolutePath}"
            else -> "??://${toString()}"
        }
    }

}
