package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.DoubleProperty
import javafx.geometry.HPos
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.geometry.VPos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.layout.*
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.SingleScaleMetadataState

interface SourceStateBackendN5<D, T> : SourceStateBackend<D, T> {
	val container: N5Reader
	val dataset: String
	override val name: String
		get() = dataset.split("/").last()

	fun getMetadataState(): MetadataState

	override val resolution: DoubleArray
		get() = getMetadataState().resolution

	override val translation: DoubleArray
		get() = getMetadataState().translation

	override fun updateTransform(resolution: DoubleArray, translation: DoubleArray) = getMetadataState().updateTransform(resolution, translation)

	override fun updateTransform(transform: AffineTransform3D) = getMetadataState().updateTransform(transform)

	override fun createMetaDataNode(): Node {
		val metadataState = getMetadataState()

		return (metadataState as? MultiScaleMetadataState)?.let { multiScaleMetadataNode(it) } ?: singleScaleMetadataNode(metadataState)
	}

    override fun shutdown() {
        container.close()
    }

    fun multiScaleMetadataNode(metadataState: MultiScaleMetadataState): VBox {

		return VBox().apply {

			val n5ContainerState = metadataState.n5ContainerState
			val containerLabel = Labels.withTooltip("Container", "N5 container of source dataset `$dataset'")
			val datasetLabel = Labels.withTooltip("Dataset", "Dataset path inside container `${n5ContainerState.uri}'")

			val container = TextField(n5ContainerState.uri.toString()).apply { isEditable = false }
			val dataset = TextField(metadataState.dataset).apply { isEditable = false }

			children += HBox(containerLabel, container)
			HBox.setHgrow(containerLabel, Priority.NEVER)
			HBox.setHgrow(container, Priority.ALWAYS)

			children += HBox(datasetLabel, dataset)
			HBox.setHgrow(datasetLabel, Priority.NEVER)
			HBox.setHgrow(dataset, Priority.ALWAYS)


			metadataState.metadata.childrenMetadata.zip(metadataState.scaleTransforms).forEachIndexed { idx, (scale, transform) ->
				val title = "Scale $idx: ${scale.name}"
				val scaleMetadataGrid = singleScaleMetadataNode(SingleScaleMetadataState(n5ContainerState, scale), true, transform)
				children += TitledPanes.createCollapsed(title, scaleMetadataGrid)
			}


		}
	}

	fun singleScaleMetadataNode(metadataState: MetadataState, asScaleLevel: Boolean = false, transformOverride: AffineTransform3D? = null): GridPane {
		val n5ContainerState = metadataState.n5ContainerState

		val resolutionLabel = Labels.withTooltip("Resolution", "Resolution of the source dataset")
		val offsetLabel = Labels.withTooltip("Offset", "Offset of the source dataset")
		val labelMultisetLabel = Label("Label Multiset?")
		val dataTypeLabel = Label("Data Type")
		val unitLabel = Label("Unit")
		val dimensionsLabel = Label("Dimensions")
		val compressionLabel = Label("Compression")
		val blockSizeLabel = Label("Block Size")

		val getSpatialFieldWithInitialDoubleArray: (DoubleArray) -> SpatialField<DoubleProperty> = {
			SpatialField.doubleField(0.0, { true }, -1.0, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).apply {
				x.value = it[0]
				y.value = it[1]
				z.value = it[2]
				editable = false
			}
		}

		val resolution = transformOverride?.let {
			doubleArrayOf(it.get(0, 0), it.get(1, 1), it.get(2, 2))
		} ?: metadataState.resolution

		val translation = transformOverride?.translation ?: metadataState.translation

		val resolutionField = getSpatialFieldWithInitialDoubleArray(resolution)
		val offsetField = getSpatialFieldWithInitialDoubleArray(translation)

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
			showHeader = true
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

			if (!asScaleLevel) {
				val containerLabel = Labels.withTooltip("Container", "N5 container of source dataset `$dataset'")
				val container = TextField(n5ContainerState.uri.toString()).apply { isEditable = false }

				val datasetLabel = Labels.withTooltip("Dataset", "Dataset path inside container `${n5ContainerState.uri}'")
				val dataset = TextField(metadataState.dataset).apply { isEditable = false }

				add(containerLabel, 0, row)
				add(container, 2, row++, 3, 1)

				add(datasetLabel, 0, row)
				add(dataset, 2, row++, 3, 1)
			}


			add(Separator(Orientation.VERTICAL), 1, 0, 1, 20)

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
				add(label, 0, row, 1, 2)
				GridPane.setValignment(label, VPos.BOTTOM)
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
