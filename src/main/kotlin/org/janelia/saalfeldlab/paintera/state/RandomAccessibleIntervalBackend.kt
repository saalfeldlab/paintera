package org.janelia.saalfeldlab.paintera.state

import bdv.cache.SharedQueue
import bdv.util.volatiles.VolatileTypeMatcher
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.geometry.HPos
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.Volatile
import net.imglib2.cache.Invalidate
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.converter.Converters
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.Type
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.numeric.RealType
import net.imglib2.util.Util
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.util.convertRAI
import java.util.function.Predicate

private val NO_OP_INVALIDATE: Invalidate<Long> = object : Invalidate<Long> {
	override fun invalidate(key: Long) {}
	override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<Long>) {}
	override fun invalidateAll(parallelismThreshold: Long) {}
}

abstract class RandomAccessibleIntervalBackend<D, T>(
	override val name: String,
	val sources: Array<RandomAccessibleInterval<D>>,
	var resolutions: Array<DoubleArray>,
	var translations: Array<DoubleArray>
) : SourceStateBackend<D, T>
		where D : RealType<D>, D : NativeType<D>, T : Volatile<D>, T : Type<T> {

	override val resolution: DoubleArray
		get() = resolutions[0]
	override val translation: DoubleArray
		get() = translations[0]

	override var virtualCrop: Interval? = null

	constructor(
		name: String,
		source: RandomAccessibleInterval<D>,
		resolution: DoubleArray,
		translation: DoubleArray
	) : this(name, arrayOf(source), arrayOf(resolution), arrayOf(translation))

	val transform = AffineTransform3D().also {
		it.set(
			resolution[0], 0.0, 0.0, translation[0],
			0.0, resolution[1], 0.0, translation[1],
			0.0, 0.0, resolution[2], translation[2]
		)
	}

	val transforms: Array<AffineTransform3D>
		get() {
			return resolutions.zip(translations)
				.map { (res, trans) -> MetadataUtils.transformFromResolutionOffset(res, trans) }
				.toTypedArray()
		}

	override fun updateTransform(transform: AffineTransform3D) {
		val oldToNew = AffineTransform3D().also {
			it.set(transform.inverse().copy().concatenate(this.transform))
		}

		/* TODO verify this */
		resolutions.zip(translations).forEachIndexed() { idx, (res, trans) ->
			val scaleTransform = MetadataUtils.transformFromResolutionOffset(res, trans)
			scaleTransform.concatenate(oldToNew)
			resolutions[idx][0] = scaleTransform[0, 0]
			resolutions[idx][1] = scaleTransform[1, 1]
			resolutions[idx][2] = scaleTransform[2, 2]
			translations[idx][0] = scaleTransform[0, 3]
			translations[idx][1] = scaleTransform[1, 3]
			translations[idx][2] = scaleTransform[2, 3]
		}

		this.transform.set(transform)
	}

	override fun createSource(queue: SharedQueue, priority: Int, name: String): DataSource<D, T> {

		val dataSources = mutableListOf<RandomAccessibleInterval<D>>()
		val volatileSources = mutableListOf<RandomAccessibleInterval<T>>()

		sources.forEach { source ->

			val zeroMinSource = if (Views.isZeroMin(source)) source else Views.zeroMin(source)

			val volatileType = VolatileTypeMatcher.getVolatileTypeForType(source.type).createVariable() as T
			volatileType.isValid = true

			val volatileSource = zeroMinSource.convertRAI(volatileType) { s, t -> (t.get() as NativeType<D>).set(s) }

			dataSources += zeroMinSource
			volatileSources += volatileSource
		}

		return RandomAccessibleIntervalDataSource(
			dataSources.toTypedArray(),
			volatileSources.toTypedArray(),
			{ transforms },
			NO_OP_INVALIDATE,
			{ NearestNeighborInterpolatorFactory() },
			{ NearestNeighborInterpolatorFactory() },
			name
		)

	}

	override fun createMetaDataNode(): Node {
		val isMultiscaleLabel = Labels.withTooltip("Multiscale")
		val resolutionLabel = Labels.withTooltip("Resolution", "Resolution of the source dataset")
		val offsetLabel = Labels.withTooltip("Offset", "Offset of the source dataset")
		val labelMultisetLabel = Label("Label Multiset")
		val dimensionsLabel = Label("Dimensions")
		val blockSizeLabel = Label("Block Size")


		val getSpatialFieldWithInitialDoubleArray: (DoubleArray) -> SpatialField<DoubleProperty> = {
			SpatialField.doubleField(0.0, { true }, -1.0, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST).apply {
				x.value = it[0]
				y.value = it[1]
				z.value = it[2]
				editable = false
			}
		}

		val resolutionField = getSpatialFieldWithInitialDoubleArray(resolution)
		val offsetField = getSpatialFieldWithInitialDoubleArray(translation)

		var blockSizeField: SpatialField<IntegerProperty>? = null
		(sources[0] as? CachedCellImg<*, *>)?.let {
			val blockSize = it.cellGrid.gridDimensions
			blockSizeField = SpatialField.intField(0, { true }, Region.USE_COMPUTED_SIZE).apply {
				x.value = blockSize[0]
				y.value = blockSize[1]
				z.value = blockSize[2]
				editable = false
			}
		}


		val dimensions = sources[0].dimensionsAsLongArray()
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

			add(Separator(Orientation.VERTICAL), 1, row, 1, GridPane.REMAINING)

			add(isMultiscaleLabel, 0, row)
			add(Label("${sources.size > 1}").also { it.alignment = Pos.CENTER_RIGHT }, 2, row++)

			add(labelMultisetLabel, 0, row)
			add(Label("${sources[0].randomAccess().get() is LabelMultisetType?}").also { it.alignment = Pos.CENTER_RIGHT }, 2, row++)

			mapOf(
				dimensionsLabel to dimensionsField,
				resolutionLabel to resolutionField,
				offsetLabel to offsetField,
				blockSizeLabel to blockSizeField,
			)
				.filter { (_, v) -> v != null }
				.forEach { (label, field) ->
					add(label, 0, row)
					add(field!!.node, 2, row++, 3, 2)
					row++
				}
		}
	}
}