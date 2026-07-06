package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.viewer.Source
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.NgffSingleScaleAxesMetadata
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.util.n5.SpatialMapping
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiscaleGroup
import java.io.File

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
	val sizeProperty: LongProperty = SimpleLongProperty(size)
	val blockSizeProperty: IntegerProperty = SimpleIntegerProperty(blockSize)
	val resolutionProperty: DoubleProperty = SimpleDoubleProperty(resolution)
	val offsetProperty: DoubleProperty = SimpleDoubleProperty(offset)
	val unitProperty: StringProperty = SimpleStringProperty(unit)
}

interface CreateDatasetModel {

	val nameProperty: StringProperty
	val datasetProperty: StringProperty
	val containerProperty: ObjectProperty<File?>

	/* the spatial x, y, z axes */
	val dimensions: SpatialValues<LongProperty>
	val blockSize: SpatialValues<IntegerProperty>
	val resolution: SpatialValues<DoubleProperty>
	val offset: SpatialValues<DoubleProperty>

	val xUnitProperty: StringProperty
	val yUnitProperty: StringProperty
	val zUnitProperty: StringProperty

	val labelMultisetProperty: BooleanProperty
	val additionalAxes: ObservableList<AdditionalAxis>
	val scaleLevels: ScaleLevelsModel

	/** True if a required field is missing or a scale level has a non-positive dimension; disables Create. */
	val invalidProperty: ObservableBooleanValue

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

	override val dimensions = SpatialValues.longValues(1)
	override val blockSize = SpatialValues.intValues(32)
	override val resolution = SpatialValues.doubleValues(1.0)
	override val offset = SpatialValues.doubleValues(0.0)
	override val xUnitProperty = SimpleStringProperty("pixel")
	override val yUnitProperty = SimpleStringProperty("pixel")
	override val zUnitProperty = SimpleStringProperty("pixel")

	override val labelMultisetProperty = SimpleBooleanProperty(true)
	override val additionalAxes: ObservableList<AdditionalAxis> = FXCollections.observableArrayList()

	/* the scale pyramid shares this model's base spatial values, so re-basing on add/remove edits them directly */
	override val scaleLevels: ScaleLevelsModel = DefaultScaleLevelsModel(dimensions, resolution, offset, blockSize)

	override val invalidProperty: ObservableBooleanValue = Bindings.createBooleanBinding(
		{ name.isBlank() || dataset.isBlank() || container == null || scaleLevels.invalidProperty.value },
		nameProperty, datasetProperty, containerProperty, scaleLevels.invalidProperty
	)

	override var name: String by nameProperty.nonnull()
	override var dataset: String by datasetProperty.nonnull()
	override var container: File? by containerProperty.nullable()
	override var labelMultiset: Boolean by labelMultisetProperty.nonnull()

	override fun ndDimensions(): LongArray =
		dimensions.asLongArray() + additionalAxes.map { it.sizeProperty.value }.toLongArray()

	override fun ndBlockSize(): IntArray =
		blockSize.asIntArray() + additionalAxes.map { it.blockSizeProperty.value }.toIntArray()

	override fun ndResolution(): DoubleArray =
		resolution.asDoubleArray() + additionalAxes.map { it.resolutionProperty.value }.toDoubleArray()

	override fun ndOffset(): DoubleArray =
		offset.asDoubleArray() + additionalAxes.map { it.offsetProperty.value }.toDoubleArray()

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
			axes += Axis(axisColumn.typeProperty.value.axisType, name, axisColumn.unitProperty.value.ifBlank { null }, false)
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
			blockSize.xProperty.value = blockDims[0]
			blockSize.yProperty.value = blockDims[1]
			blockSize.zProperty.value = blockDims[2]
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
		dimensions.xProperty.value = data.dimension(0)
		dimensions.yProperty.value = data.dimension(1)
		dimensions.zProperty.value = data.dimension(2)
		if (data is AbstractCellImg<*, *, *, *>) {
			val grid = data.cellGrid
			blockSize.xProperty.value = grid.cellDimension(0)
			blockSize.yProperty.value = grid.cellDimension(1)
			blockSize.zProperty.value = grid.cellDimension(2)
		}
		val transform = AffineTransform3D()
		source.getSourceTransform(0, 0, transform)
		resolution.xProperty.value = transform[0, 0]
		resolution.yProperty.value = transform[1, 1]
		resolution.zProperty.value = transform[2, 2]
		metadataSource?.metadataState?.virtualCrop?.let {
			offset.xProperty.value = it.min(0) * transform[0, 0]
			offset.yProperty.value = it.min(1) * transform[1, 1]
			offset.zProperty.value = it.min(2) * transform[2, 2]
		} ?: let {
			offset.xProperty.value = transform[0, 3]
			offset.yProperty.value = transform[1, 3]
			offset.zProperty.value = transform[2, 3]
		}

		metadataSource?.metadataState?.unit?.let { unit ->
			xUnitProperty.value = unit
			yUnitProperty.value = unit
			zUnitProperty.value = unit
		}
		scaleLevels.setFromSource(source)
	}
}
