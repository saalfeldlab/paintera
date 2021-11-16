package org.janelia.saalfeldlab.paintera.state

import javafx.scene.Node
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.state.raw.n5.urlRepresentation

interface SourceStateBackendN5<D, T> : SourceStateBackend<D, T> {
    val container: N5Reader
    val dataset: String
    override val defaultSourceName: String
        get() = dataset.split("/").last()

    override fun createMetaDataNode(): Node {
        val containerLabel = Labels.withTooltip("Container", "N5 container of source dataset `$dataset'")
        val datasetLabel = Labels.withTooltip("Dataset", "Dataset path inside container `${container.urlRepresentation()}'")
        val container = TextField(this.container.urlRepresentation()).apply { isEditable = false }
        val dataset = TextField(this.dataset).apply { isEditable = false }
        GridPane.setHgrow(container, Priority.ALWAYS)
        GridPane.setHgrow(dataset, Priority.ALWAYS)
        return GridPane().apply {
            hgap = 10.0
            add(containerLabel, 0, 0)
            add(datasetLabel, 0, 1)
            add(container, 1, 0)
            add(dataset, 1, 1)
        }
    }
}
