package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.viewer.Source
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.TextField
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.NgffSingleScaleAxesMetadata
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.util.n5.SpatialMapping
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiscaleGroup
import java.io.File

internal const val NAME_WIDTH = 100.0
internal const val MAIN_FIELD_WIDTH = 75.0
internal const val SCALE_FIELD_WIDTH = 60.0

private val LOG = KotlinLogging.logger { }

/**
 * A non-spatial (e.g. channel, time) axis appended after x, y, z.
 * - they are not downsampled
 * - the default block size is 1
 * - the default resolution is 1
 * - default unit is empty
 */
enum class NonSpatialAxisType(private val label: String, val axisType: String, val shortLabel: String) {
	CHANNEL("Channel", Axis.CHANNEL, "C"),
	TIME("Time", Axis.TIME, "T");

	override fun toString() = label
}

class AdditionalAxis(size: Long, blockSize: Int, type: NonSpatialAxisType, unit: String, resolution: Double = 1.0, offset: Double = 0.0) {
	val typeProperty = SimpleObjectProperty(type)
	val size = NumberField.longField(size, { it > 0 }, *SubmitOn.entries.toTypedArray()).apply { textField.prefWidth = MAIN_FIELD_WIDTH }
	val blockSize = NumberField.intField(blockSize, { it > 0 }, *SubmitOn.entries.toTypedArray()).apply { textField.prefWidth = MAIN_FIELD_WIDTH }
	val resolution = NumberField.doubleField(resolution, { it > 0 }, *SubmitOn.entries.toTypedArray()).apply { textField.prefWidth = MAIN_FIELD_WIDTH }
	val offset = NumberField.doubleField(offset, { true }, *SubmitOn.entries.toTypedArray()).apply { textField.prefWidth = MAIN_FIELD_WIDTH }
	val unit = TextField(unit).apply { prefWidth = MAIN_FIELD_WIDTH; promptText = "unit" }
}

/**
 * The full editable state of the "Create new Label dataset" dialog. Owns the value-editor widgets (spatial fields, axis
 * columns, scale levels) and the data behaviors (populate from a source, derive the nD create parameters). The [UI][CreateDatasetUI]
 * arranges these; the [state][CreateDatasetActionState] verifies and creates from them.
 */
interface CreateDatasetModel {

	val nameProperty: StringProperty
	val datasetProperty: StringProperty
	val containerProperty: ObjectProperty<File?>

	/* the spatial x, y, z axes */
	val dimensions: SpatialField<LongProperty>
	val blockSize: SpatialField<IntegerProperty>
	val resolution: SpatialField<DoubleProperty>
	val offset: SpatialField<DoubleProperty>

	val xUnitProperty: StringProperty
	val yUnitProperty: StringProperty
	val zUnitProperty: StringProperty

	val labelMultisetProperty: BooleanProperty
	val additionalAxes: ObservableList<AdditionalAxis>
	val scaleLevels: ScaleLevelsModel

	/* context for the "Populate" menu */
	val currentSource: Source<*>?
	val populateSources: List<SourceState<*, *>>

	var name: String
	var dataset: String
	var container: File?
	var labelMultiset: Boolean

	fun populateFrom(source: Source<*>?)

	fun ndDimensions(): LongArray

	fun ndBlockSize(): IntArray

	fun ndResolution(): DoubleArray

	fun ndOffset(): DoubleArray

	/** The source axes (x, y, z, then non-spatial) */
	fun axes(): Array<Axis>

	fun downsamplingFactors(): Array<DoubleArray>

	fun maxNumEntries(): IntArray?

	companion object {
		fun default(): CreateDatasetModel = DefaultCreateDatasetModel()
	}
}

internal class DefaultCreateDatasetModel(
	override val currentSource: Source<*>? = null,
	override val populateSources: List<SourceState<*, *>> = emptyList()
) : CreateDatasetModel {

	override val nameProperty = SimpleStringProperty("")
	override val datasetProperty = SimpleStringProperty("")
	override val containerProperty = SimpleObjectProperty<File?>(null)

	override val dimensions = SpatialField.longField(1, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	override val blockSize = SpatialField.intField(16, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	override val resolution = SpatialField.doubleField(1.0, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	override val offset = SpatialField.doubleField(0.0, { true }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	override val xUnitProperty = SimpleStringProperty("pixel")
	override val yUnitProperty = SimpleStringProperty("pixel")
	override val zUnitProperty = SimpleStringProperty("pixel")

	override val labelMultisetProperty = SimpleBooleanProperty(true)
	override val additionalAxes: ObservableList<AdditionalAxis> = FXCollections.observableArrayList()

	/* the scale pyramid shares this model's base spatial fields, so re-basing on add/remove edits them directly */
	override val scaleLevels: ScaleLevelsModel = DefaultScaleLevelsModel(dimensions, resolution, offset, blockSize)

	override var name: String by nameProperty.nonnull()
	override var dataset: String by datasetProperty.nonnull()
	override var container: File? by containerProperty.nullable()
	override var labelMultiset: Boolean by labelMultisetProperty.nonnull()

	override fun ndDimensions(): LongArray =
		dimensions.asLongArray() + additionalAxes.map { it.size.valueProperty().get() }.toLongArray()

	override fun ndBlockSize(): IntArray =
		blockSize.asIntArray() + additionalAxes.map { it.blockSize.valueProperty().get() }.toIntArray()

	override fun ndResolution(): DoubleArray =
		resolution.asDoubleArray() + additionalAxes.map { it.resolution.valueProperty().get() }.toDoubleArray()

	override fun ndOffset(): DoubleArray =
		offset.asDoubleArray() + additionalAxes.map { it.offset.valueProperty().get() }.toDoubleArray()

	override fun downsamplingFactors(): Array<DoubleArray> = scaleLevels.downsamplingFactors()

	override fun maxNumEntries(): IntArray? = if (labelMultiset) scaleLevels.maxNumEntries() else null

	override fun axes(): Array<Axis> {
		val axes = mutableListOf(
			Axis(Axis.SPACE, "x", xUnitProperty.value.ifBlank { null }, false),
			Axis(Axis.SPACE, "y", yUnitProperty.value.ifBlank { null }, false),
			Axis(Axis.SPACE, "z", zUnitProperty.value.ifBlank { null }, false)
		)
		var channels = 0
		var times = 0
		additionalAxes.forEach { axisColumn ->
			val name = when (axisColumn.typeProperty.value) {
				NonSpatialAxisType.CHANNEL -> "c" + (channels++.takeIf { it > 0 }?.toString() ?: "")
				NonSpatialAxisType.TIME -> "t" + (times++.takeIf { it > 0 }?.toString() ?: "")
			}
			axes += Axis(axisColumn.typeProperty.value.axisType, name, axisColumn.unit.text.ifBlank { null }, false)
		}
		return axes.toTypedArray()
	}

	override fun populateFrom(source: Source<*>?) {
		val metadataSource = when (source) {
			null -> return
			is N5DataSource<*, *> -> source
			is MaskedSource<*, *> -> source.underlyingSource() as? N5DataSource<*, *>
			else -> null
		}

		metadataSource?.let {
			val blockDims = it.metadataState.datasetAttributes.blockSize
			blockSize.x.value = blockDims[0]
			blockSize.y.value = blockDims[1]
			blockSize.z.value = blockDims[2]
		}
		/* match the source's additional (non-spatial) axes (size, block size, type, unit, resolution and offset) in source order */
		metadataSource?.metadataState?.let { metadataState ->

			val attributes = metadataState.datasetAttributes
			/* the highest-res NGFF child, when the source has one; its scale/translation carry the non-spatial axes */
			val highestRes = when (val metadata = metadataState.metadata) {
				is N5PainteraDataMultiscaleGroup -> metadata.dataGroupMetadata.childrenMetadata.firstOrNull()
				is NgffSingleScaleAxesMetadata -> metadata
				is SpatialMultiscaleMetadata<*> -> metadata.childrenMetadata.firstOrNull() as? NgffSingleScaleAxesMetadata
				else -> null
			}
			val nonSpatial = SpatialMapping.nonSpatialAxes(metadataState.axes, attributes.numDimensions)
			additionalAxes.setAll(nonSpatial.map { axisIndex ->
				val sourceAxis = metadataState.axes[axisIndex]
				val type = if (sourceAxis.type == Axis.TIME) NonSpatialAxisType.TIME else NonSpatialAxisType.CHANNEL
				AdditionalAxis(
                    size = attributes.dimensions[axisIndex],
                    blockSize = attributes.blockSize.getOrElse(axisIndex) { 1 },
                    type = type,
                    unit = sourceAxis.unit ?: "",
                    resolution = highestRes?.scale?.getOrNull(axisIndex) ?: 1.0,
                    offset = highestRes?.translation?.getOrNull(axisIndex) ?: 0.0
				)
			})
		}
		val data = source.getSource(0, 0)
		dimensions.x.value = data.dimension(0)
		dimensions.y.value = data.dimension(1)
		dimensions.z.value = data.dimension(2)
		if (data is AbstractCellImg<*, *, *, *>) {
			val grid = data.cellGrid
			blockSize.x.value = grid.cellDimension(0)
			blockSize.y.value = grid.cellDimension(1)
			blockSize.z.value = grid.cellDimension(2)
		}
		val transform = AffineTransform3D()
		source.getSourceTransform(0, 0, transform)
		resolution.x.value = transform[0, 0]
		resolution.y.value = transform[1, 1]
		resolution.z.value = transform[2, 2]
		metadataSource?.metadataState?.virtualCrop?.let {
			offset.x.value = it.min(0) * transform[0, 0]
			offset.y.value = it.min(1) * transform[1, 1]
			offset.z.value = it.min(2) * transform[2, 2]
		} ?: let {
			offset.x.value = transform[0, 3]
			offset.y.value = transform[1, 3]
			offset.z.value = transform[2, 3]
		}

		metadataSource?.metadataState?.unit?.let { unit ->
			xUnitProperty.value = unit
			yUnitProperty.value = unit
			zUnitProperty.value = unit
		}
		scaleLevels.setFromSource(source)
	}
}
