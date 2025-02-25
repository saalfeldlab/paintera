package org.janelia.saalfeldlab.paintera.control.modes

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.input.KeyEvent.KEY_PRESSED
import net.imglib2.RandomAccessibleInterval
import net.imglib2.histogram.Histogram1d
import net.imglib2.histogram.Real1dBinMapper
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.label.VolatileLabelMultisetType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.util.Intervals
import net.imglib2.util.Util
import net.imglib2.view.IntervalView
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.ui.ScaleView
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.paintera.RawSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.util.*


object RawSourceMode : AbstractToolMode() {

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList()

	override val allowedActions = AllowedActions.NAVIGATION

	private val minMaxIntensityThreshold = painteraActionSet("Min/Max Intensity Threshold") {
		verifyAll(KEY_PRESSED, "Source State is Raw Source State ") { activeSourceStateProperty.get() is ConnectomicsRawState<*, *> }
		KEY_PRESSED(RawSourceStateKeys.RESET_MIN_MAX_INTENSITY_THRESHOLD) {
			graphic = { ScaleView().apply { styleClass += "intensity-reset-min-max" } }
			onAction {
				val rawSource = activeSourceStateProperty.get() as ConnectomicsRawState<*, *>
				resetIntensityMinMax(rawSource)
			}
		}
		KEY_PRESSED(RawSourceStateKeys.AUTO_MIN_MAX_INTENSITY_THRESHOLD) {
			graphic = { ScaleView().apply { styleClass += "intensity-auto-min-max" } }
			onAction {
				val viewer = paintera.baseView.run {
					lastFocusHolder.value ?: orthogonalViews().topLeft
				}.viewer()
				val rawSource = activeSourceStateProperty.get() as ConnectomicsRawState<*, *>
				autoIntensityMinMax(rawSource, viewer)
			}
		}
	}

	override val activeViewerActions: List<ActionSet> = listOf(minMaxIntensityThreshold)

	fun autoIntensityMinMax(rawSource: ConnectomicsRawState<*, *>, viewer: ViewerPanelFX) {
		val globalToViewerTransform = AffineTransform3D().also { viewer.state.getViewerTransform(it) }
		val viewerInterval = Intervals.createMinSize(0, 0, 0, viewer.width.toLong(), viewer.height.toLong(), 1L)

		val scaleLevel = viewer.state.bestMipMapLevel
		val dataSource = rawSource.getDataSource().getSource(0, scaleLevel) as RandomAccessibleInterval<RealType<*>>

		val sourceToGlobalTransform = rawSource.getDataSource().getSourceTransformCopy(0, scaleLevel)


		val extension = Util.getTypeFromInterval(dataSource).createVariable().let {
			when (it) {
				is VolatileLabelMultisetType, is LabelMultisetType -> UnsignedLongType(0)
				else -> it
			}
		}


		val screenSource = dataSource
			.extendValue(extension)
			.interpolateNearestNeighbor()
			.affineReal(globalToViewerTransform.concatenate(sourceToGlobalTransform))
			.raster()
			.interval(viewerInterval)

		val converter = rawSource.converter()
		val curMin = converter.minProperty().get()
		val curMax = converter.maxProperty().get()

		if (curMin == curMax) {
			resetIntensityMinMax(rawSource)
			return
		}

		when {
			converterAtDefault(rawSource) -> estimateWithRange(screenSource, converter)
			extension is IntegerType<*> -> estimateWithHistogram(IntType(), screenSource, rawSource, converter)
			else -> estimateWithHistogram(DoubleType(), screenSource, rawSource, converter)
		}
	}

	private fun estimateWithRange(screenSource: IntervalView<RealType<*>>, converter: ARGBColorConverter<out AbstractVolatileRealType<*, *>>) {
		var min = screenSource.cursor().get().realDouble
		var max = min
		screenSource.forEach {
			val value = it.realDouble
			if (value < min)
				min = value
			if (value > max)
				max = value
		}
		converter.min = min
		converter.max = max
	}

	private fun <T : RealType<T>> estimateWithHistogram(type: T, screenSource: IntervalView<RealType<*>>, rawSource: ConnectomicsRawState<*, *>, converter: ARGBColorConverter<out AbstractVolatileRealType<*, *>>) {
		val numSamples = Intervals.numElements(screenSource)
		val numBins = numSamples.coerceIn(100, 1000)
		val binMapper = Real1dBinMapper<T>(converter.min, converter.max, numBins, false)
		val histogram = Histogram1d(binMapper)
		val img = screenSource.convertRAI(type) { src, target -> target.setReal(src.realDouble) }.asIterable()
		histogram.countData(img)

		val counts = histogram.toLongArray()
		var runningSumMin = 0L
		var runningSumMax = 0L
		var minBinIdx = 0
		var maxBinIdx = counts.size - 1
		val threshold = numSamples / 25
		for (i in counts.indices) {
			val count = counts[i]
			runningSumMin += count
			if (runningSumMin >= threshold) {
				minBinIdx = i
				break
			}
		}
		for (i in counts.indices.reversed()) {
			val count = counts[i]
			runningSumMax += count
			if (runningSumMax >= threshold) {
				maxBinIdx = i
				break
			}
		}

		val updateOrResetConverter = { min: Double, max: Double ->
			if (converter.minProperty().value == min && converter.maxProperty().value == max)
				resetIntensityMinMax(rawSource)
			else {
				converter.min = min
				converter.max = max
			}
		}

		if (minBinIdx >= maxBinIdx) {
			resetIntensityMinMax(rawSource)
			return
		}

		updateOrResetConverter(
			histogram.getLowerBound(minBinIdx.toLong(), type).let { type.realDouble },
			histogram.getUpperBound(maxBinIdx.toLong(), type).let { type.realDouble }
		)
	}

	private fun converterAtDefault(rawSource: ConnectomicsRawState<*, *>): Boolean {
		val converter = rawSource.converter()
		(rawSource.backend as? SourceStateBackendN5<*, *>)?.metadataState?.let {
			return converter.min == it.minIntensity && converter.max == it.maxIntensity
		}

		val dataSource = rawSource.getDataSource().getDataSource(0, 0) as RandomAccessibleInterval<RealType<*>>
		val extension = Util.getTypeFromInterval(dataSource).createVariable().let {
			when (it) {
				is VolatileLabelMultisetType, is LabelMultisetType -> UnsignedLongType(0)
				else -> it
			}
		}

		return converter.minProperty().get() == extension.minValue && converter.maxProperty().get() == extension.maxValue
	}

	fun resetIntensityMinMax(rawSource: ConnectomicsRawState<*, *>) {

		(rawSource.backend as? SourceStateBackendN5<*, *>)?.metadataState?.let {
			rawSource.converter().min = it.minIntensity
			rawSource.converter().max = it.maxIntensity
			return
		}

		val dataSource = rawSource.getDataSource().getDataSource(0, 0) as RandomAccessibleInterval<RealType<*>>
		val extension = Util.getTypeFromInterval(dataSource).createVariable().let {
			when (it) {
				is VolatileLabelMultisetType, is LabelMultisetType -> UnsignedLongType(0)
				else -> it
			}
		}

		rawSource.converter().minProperty().set(extension.minValue)
		rawSource.converter().maxProperty().set(extension.maxValue)
	}

}


