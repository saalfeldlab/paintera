package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.DoubleProperty
import javafx.geometry.HPos
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5Utils.urlRepresentation

interface SourceStateBackendN5<D, T> : SourceStateBackend<D, T> {
    val container: N5Reader
    val dataset: String
    override val name: String
        get() = dataset.split("/").last()

    override fun createMetaDataNode(): Node {
        val metadataState = getMetadataState()

        val containerLabel = Labels.withTooltip("Container", "N5 container of source dataset `$dataset'")
        val datasetLabel = Labels.withTooltip("Dataset", "Dataset path inside container `${container.urlRepresentation()}'")
        val resolutionLabel = Labels.withTooltip("Resolution", "Resolution of the source dataset")
        val offsetLabel = Labels.withTooltip("Offset", "Offset of the source dataset")
        val labelMultisetLabel = Label("Label Multiset?")
        val dataTypeLabel = Label("Data Type")
        val unitLabel = Label("Unit")
        val dimensionsLabel = Label("Dimensions")
        val compressionLabel = Label("Compression")
        val blockSizeLabel = Label("Block Size")


        val container = TextField(this.container.urlRepresentation()).apply { isEditable = false }
        val dataset = TextField(this.dataset).apply { isEditable = false }

        val getSpatialFieldWithInitialDoubleArray: (DoubleArray) -> SpatialField<DoubleProperty> = {
            SpatialField.doubleField(0.0, { true }, -1.0, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).apply {
                x.value = it[0]
                y.value = it[1]
                z.value = it[2]
                editable = false
            }
        }

        val resolutionField = getSpatialFieldWithInitialDoubleArray(metadataState.resolution)
        val offsetField = getSpatialFieldWithInitialDoubleArray(metadataState.translation)

        val blockSize = metadataState.datasetAttributes.blockSize
        val blockSizeField = SpatialField.intField(0, { true }, Region.USE_COMPUTED_SIZE).apply {
            x.value = blockSize[0]
            y.value = blockSize[1]
            z.value = blockSize[2]
            editable = false
        }


        val dimensions = metadataState.datasetAttributes.dimensions
        val dimensionsField = SpatialField.longField(0, { true }, Region.USE_COMPUTED_SIZE).apply {
            x.value = dimensions[0]
            y.value = dimensions[1]
            z.value = dimensions[2]
            editable = false
        }

        return GridPane().apply {
            columnConstraints.add(
                0, ColumnConstraints(
                    Label.USE_COMPUTED_SIZE, Label.USE_COMPUTED_SIZE, Label.USE_COMPUTED_SIZE,
                    Priority.ALWAYS,
                    HPos.LEFT,
                    true
                )
            )

            hgap = 10.0
            var row = 0

            add(Separator(Orientation.VERTICAL), 1, 0, 1, 20)
            add(containerLabel, 0, row)
            add(container, 2, row++, 3, 1)

            add(datasetLabel, 0, row)
            add(dataset, 2, row++, 3, 1)

            add(labelMultisetLabel, 0, row)
            add(Label("${metadataState.isLabelMultiset}").also { it.alignment = Pos.CENTER_RIGHT }, 2, row++)

            add(unitLabel, 0, row)
            add(Label(metadataState.unit).also { GridPane.setHalignment(it, HPos.RIGHT) }, 2, row++)

            mapOf(
                dimensionsLabel to dimensionsField,
                resolutionLabel to resolutionField,
                offsetLabel to offsetField,
                blockSizeLabel to blockSizeField,
            ).forEach { (label, field) ->
                add(label, 0, row)
                add(field.node, 2, row++, 3, 2)
                row++
            }

            add(dataTypeLabel, 0, row)
            add(Label("${metadataState.datasetAttributes.dataType}").also { GridPane.setHalignment(it, HPos.RIGHT) }, 2, row++)

            add(compressionLabel, 0, row)
            add(Label(metadataState.datasetAttributes.compression.type).also { GridPane.setHalignment(it, HPos.RIGHT) }, 2, row++)
        }
    }
}
