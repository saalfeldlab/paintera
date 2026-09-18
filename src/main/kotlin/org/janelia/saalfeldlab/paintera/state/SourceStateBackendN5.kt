package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.*
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.data.XyzView
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.metadata.SingleScaleMetadataState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.paintera.util.PainteraUtils.intervalInSourceSpace
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.isNotEmpty
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.union
import org.janelia.saalfeldlab.paintera.state.metadata.resolution
import org.janelia.saalfeldlab.paintera.state.metadata.transform
import org.janelia.saalfeldlab.paintera.state.metadata.translation

interface SourceStateBackendN5<D, T> : SourceStateBackend<D, T> {
	val metadataState: MetadataState
	val container: N5Reader
		get() = metadataState.reader
	val dataset: String
		get() = metadataState.dataset
	override val name: String
		get() = dataset.split("/").last()

	override val resolution: DoubleArray
		get() = metadataState.resolution

	override val translation: DoubleArray
		get() = metadataState.translation

	override val xyzView: XyzView
		get() = metadataState.xyzView

	override fun updateTransform(resolution: DoubleArray, translation: DoubleArray) = metadataState.updateTransform(resolution, translation)

	override fun updateTransform(transform: AffineTransform3D) = metadataState.updateTransform(transform)

	override fun createMetaDataNode(): Node {
		val metadataState = metadataState

		return (metadataState as? MultiScaleMetadataState)?.let { multiScaleMetadataNode(it) } ?: singleScaleMetadataNode(metadataState)
	}

	override fun shutdown() {
		container.close()
	}

	fun multiScaleMetadataNode(metadataState: MultiScaleMetadataState): Node {

		return VBox().apply {
			spacing = 10.0

			val n5ContainerState = metadataState.n5ContainerState
			addContainerAndDatasetChildren(n5ContainerState, metadataState)
			children += Separator(Orientation.HORIZONTAL)
			children += newVirtualCropInputGrid(metadataState)

			metadataState.metadata.childrenMetadata.zip(metadataState.sourceToXyzTransforms).forEachIndexed { idx, (scale, transform) ->
				val title = "Scale $idx: ${scale.name}"
				val scaleMetadataGrid = singleScaleMetadataNode(SingleScaleMetadataState(n5ContainerState, scale), true, transform)
				children += TitledPanes.createCollapsed(title, scaleMetadataGrid)
			}
		}
	}

	private fun VBox.addContainerAndDatasetChildren(n5ContainerState: N5ContainerState, metadataState: MetadataState) {
		val containerLocation = N5Helpers.canonicalString(n5ContainerState.uri)

		val containerLabel = Labels.withTooltip("Container", "N5 container of source dataset `$dataset'")
		val datasetLabel = Labels.withTooltip("Dataset", "Dataset path inside container `$containerLocation'")

		val container = TextField(containerLocation).apply { isEditable = false }
		val dataset = TextField(metadataState.dataset).apply { isEditable = false }

		children += HBox(containerLabel, container).apply { spacing = 10.0 }
		HBox.setHgrow(containerLabel, Priority.NEVER)
		HBox.setHgrow(container, Priority.ALWAYS)

		children += HBox(datasetLabel, dataset).apply { spacing = 10.0 }
		HBox.setHgrow(datasetLabel, Priority.NEVER)
		HBox.setHgrow(dataset, Priority.ALWAYS)
	}

	private fun newVirtualCropInputGrid(metadataState: MetadataState): GridPane {
		val virtualCropGrid = GridPane().also { grid ->
			grid.columnConstraints.addAll(
				ColumnConstraints(),
				*Array(3) {
					ColumnConstraints().apply {
						hgrow = Priority.ALWAYS
						halignment = HPos.CENTER
						isFillWidth = true
					}
				},
			)
			grid.maxWidth = Double.MAX_VALUE
			grid.maxHeight = Region.USE_COMPUTED_SIZE

			VBox.setVgrow(grid, Priority.ALWAYS)
		}

		val virtualCropLabel = Labels.withTooltip("Virtual Crop\n(pixels)", "Virtual crop extents of the source in pixel space. May be modified.")
		virtualCropLabel.minWidth = Region.USE_PREF_SIZE
		GridPane.setHgrow(virtualCropLabel, Priority.ALWAYS)
		virtualCropGrid.add(virtualCropLabel, 0, 0)
		virtualCropGrid.add(Label("\tX"), 1, 0)
		virtualCropGrid.add(Label("\tY"), 2, 0)
		virtualCropGrid.add(Label("\tZ"), 3, 0)

		virtualCropGrid.add(Label("\tMin"), 0, 1)
		virtualCropGrid.add(Label("\tMax"), 0, 2)

		val cropMins = LongArray(3) {
			0L
		}
		/* the crop is over the x, y, z view, the stored dimensions are in source axis order */
		val imgDimensions = metadataState.xyzView.spatialMapping().toSpatial(metadataState.datasetAttributes.dimensions, 1L)
		val cropSize = LongArray(3) {
			(imgDimensions[it])
		}

		val uncroppedInterval = Intervals.createMinSize(*cropMins, *cropSize)

		val valueProps = Array(2) { rowIdx ->
			val row = rowIdx.takeIf { it == 0 }?.let { uncroppedInterval.minAsLongArray() } ?: uncroppedInterval.maxAsLongArray()
			Array(3) { colIdx ->
				val initialValue = row[colIdx]
				val boundsCheck: (value: Long) -> Boolean = { it in 0..imgDimensions[colIdx] }
				NumberField.longField(initialValue, boundsCheck, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).also {
					virtualCropGrid.add(it.textField, colIdx + 1, rowIdx + 1)
				}
			}
		}

		fun updateNumberFields(virtualCrop: Interval?) {
			val interval = virtualCrop ?: uncroppedInterval
			interval.minAsLongArray().zip(valueProps[0]).forEach { (min, field) -> field.value = min }
			interval.maxAsLongArray().zip(valueProps[1]).forEach { (max, field) -> field.value = max + 1 }
		}


		val virtualCropInterval = object : SimpleObjectProperty<Interval?>(null) {

			override fun set(newValue: Interval?) {
				/* let the super handle this error case */
				if (isBound)
					super.set(newValue)

				/* if we are the same interval, don't set the value. */
				if (newValue != null && this.value != null && Intervals.equals(this.value, newValue))
					return
				/* if we are the uncropped Interval, set interval to null */
				if (newValue != null && Intervals.equals(uncroppedInterval, newValue)) {
					super.set(null)
					return
				}

				super.set(newValue)
			}
		}
		virtualCropInterval.subscribe { it ->
			metadataState.xyzView.setCropInterval(it)
			updateNumberFields(it)
			paintera.baseView.orthogonalViews().requestRepaint()
			paintera.baseView.orthogonalViews().drawOverlays()
		}


		val intervalFromProperties = {
			val maxExclusiveCrop = Intervals.createMinMax(
				*valueProps[0].map { it.value.toLong() }.toLongArray(),
				*valueProps[1].map { it.value.toLong() - 1 }.toLongArray()
			)
			val cropEqualsFullImage = {
				valueProps.zip(arrayOf(longArrayOf(0, 0, 0), imgDimensions))
					.flatMap { (props, extents) -> props.zip(extents.asIterable()) }
					.asSequence()
					.map { (prop, extent) -> prop.value.toLong() == extent }
					.reduce(Boolean::and)
			}

			if (Intervals.isEmpty(maxExclusiveCrop) || cropEqualsFullImage())
				null
			else
				maxExclusiveCrop
		}

		valueProps.forEach { row ->
			row.forEach { col ->
				col.valueProperty().subscribe { _, _ ->
					virtualCropInterval.value = intervalFromProperties()
				}
			}
		}


		val resetMin = Button("").apply {
			addStyleClass(Style.RESET_ICON)
			setOnAction {
				valueProps[0].forEachIndexed { idx, prop ->
					prop.valueProperty().value = 0L
				}
			}
		}
		val resetMax = Button("").apply {
			addStyleClass(Style.RESET_ICON)
			setOnAction {
				valueProps[1].forEachIndexed { idx, prop ->
					prop.valueProperty().value = imgDimensions[idx]
				}
			}
		}
		virtualCropGrid.add(resetMin, 4, 1)
		virtualCropGrid.add(resetMax, 4, 2)

		val cropToRegionBox = VBox().apply {
			padding = Insets(10.0, 0.0, 10.0, 0.0)
			maxWidth = Double.MAX_VALUE
			alignment = Pos.CENTER_LEFT
			children += Label("Crop to Region: ")
			children += HBox(10.0).apply {
				maxWidth = Double.MAX_VALUE
				alignment = Pos.CENTER_RIGHT
				children += Button("Current View").apply { setOnAction { virtualCropInterval.value = uncroppedInterval intersect sourceIntervalForCurrentView() } }
				children += Button("Remove Crop").apply { setOnAction { virtualCropInterval.value = null } }
			}
		}
		virtualCropGrid.add(cropToRegionBox, 0, 3)
		GridPane.setColumnSpan(cropToRegionBox, GridPane.REMAINING)


		return virtualCropGrid
	}

	fun sourceIntervalForCurrentView(): Interval? {

		return paintera.baseView.orthogonalViews().viewerAndTransforms()
			.map { it.viewer() }
			.mapNotNull { viewer ->
				viewer.intervalInSourceSpace(metadataState.transform)
					?.takeIf { it.isNotEmpty() }
			}
			.reduceOrNull { acc, interval -> acc.union(interval) }
			?.smallestContainingInterval
	}

	fun singleScaleMetadataNode(metadataState: MetadataState, asScaleLevel: Boolean = false, transformOverride: AffineTransform3D? = null): Node {
		val n5ContainerState = metadataState.n5ContainerState

		val resolutionLabel = Labels.withTooltip("Resolution", "Resolution of the source dataset")
		val offsetLabel = Labels.withTooltip("Translation", "Translation of the source dataset")
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

		val spatialMapping = metadataState.xyzView.spatialMapping()
		val blockSize = spatialMapping.toSpatial(metadataState.datasetAttributes.blockSize, 1)
		val blockSizeField = SpatialField.intField(0, { true }, Region.USE_COMPUTED_SIZE).apply {
			x.value = blockSize[0]
			y.value = blockSize[1]
			z.value = blockSize[2]
			editable = false
		}

		val dimensions = spatialMapping.toSpatial(metadataState.datasetAttributes.dimensions, 1L)
		val dimensionsField = SpatialField.longField(0, { true }, Region.USE_COMPUTED_SIZE).apply {
			x.value = dimensions[0]
			y.value = dimensions[1]
			z.value = dimensions[2]
			showHeader = true
			editable = false
		}

		return VBox().apply {
			if (!asScaleLevel) {
				spacing = 10.0
				addContainerAndDatasetChildren(n5ContainerState, metadataState)
				children += Separator(Orientation.HORIZONTAL)

				children += newVirtualCropInputGrid(metadataState)
				children += Separator(Orientation.HORIZONTAL)
			}

			children += GridPane().apply {
				columnConstraints.add(
					0, ColumnConstraints(
						Label.USE_COMPUTED_SIZE, Label.USE_COMPUTED_SIZE, Label.USE_COMPUTED_SIZE,
						Priority.ALWAYS,
						HPos.LEFT,
						true
					)
				)
				padding = Insets(10.0, 0.0, 0.0, 0.0)
				hgap = 10.0
				var row = 0

				add(Separator(Orientation.VERTICAL), 1, 0, 1, GridPane.REMAINING)

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
}
