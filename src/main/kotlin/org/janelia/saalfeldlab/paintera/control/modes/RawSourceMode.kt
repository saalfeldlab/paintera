package org.janelia.saalfeldlab.paintera.control.modes

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.input.KeyEvent.KEY_PRESSED
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
import net.imglib2.util.Intervals
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
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.SourceStateWithBackend
import org.janelia.saalfeldlab.util.*


open class RawSourceMode : AbstractToolMode() {

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList()

	override val allowedActions = AllowedActions.NAVIGATION

	private val minMaxIntensityThreshold = painteraActionSet("Min/Max Intensity Threshold") {
		verifyAll(KEY_PRESSED, "Invalid Source State") { (activeSourceStateProperty.get() as? SourceState<*, RealType<*>>) != null }
		KEY_PRESSED(RawSourceStateKeys.RESET_MIN_MAX_INTENSITY_THRESHOLD) {
			graphic = { ScaleView().apply { styleClass += "intensity-reset-min-max" } }
			onAction {
				(activeSourceStateProperty.get() as? SourceState<*, RealType<*>>)?.let {
					resetIntensityMinMax(it)
				}
			}
		}
		KEY_PRESSED(RawSourceStateKeys.AUTO_MIN_MAX_INTENSITY_THRESHOLD) {
			graphic = { ScaleView().apply { styleClass += "intensity-auto-min-max" } }
			onAction {
				val viewer = paintera.baseView.run {
					lastFocusHolder.value ?: orthogonalViews().topLeft
				}.viewer()
				(activeSourceStateProperty.get() as? SourceState<*, RealType<*>>)?.let {
					autoIntensityMinMax(it, viewer)
				}
			}
		}
	}

	override val activeViewerActions: List<ActionSet> = listOf(minMaxIntensityThreshold)

	companion object {
		fun autoIntensityMinMax(rawSource: SourceState<*, RealType<*>>, viewer: ViewerPanelFX) {
			val converter = rawSource.converter() as? ARGBColorConverter<*> ?: return

			val globalToViewerTransform = AffineTransform3D().also { viewer.state.getViewerTransform(it) }
			val viewerInterval = Intervals.createMinSize(0, 0, 0, viewer.width.toLong(), viewer.height.toLong(), 1L)

			val scaleLevel = viewer.state.bestMipMapLevel
			val dataSource = rawSource.getDataSource().getSource(0, scaleLevel)

			val sourceToGlobalTransform = rawSource.getDataSource().getSourceTransformCopy(0, scaleLevel)


			val extension = dataSource.type.createVariable()


			val screenSource = dataSource
				.extendValue(extension)
				.interpolateNearestNeighbor()
				.affineReal(globalToViewerTransform.concatenate(sourceToGlobalTransform))
				.raster()
				.interval(viewerInterval)

			val curMin = converter.minProperty().get()
			val curMax = converter.maxProperty().get()

			if (curMin == curMax) {
				resetIntensityMinMax(rawSource)
				return
			}

			when {
				converterAtDefault(rawSource, converter) -> estimateWithRange(screenSource, converter)
				extension is IntegerType<*> -> estimateWithHistogram(IntType(), screenSource, rawSource, converter)
				else -> estimateWithHistogram(DoubleType(), screenSource, rawSource, converter)
			}
		}

		private fun estimateWithRange(screenSource: IntervalView<RealType<*>>, converter: ARGBColorConverter<*>) {
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

		//TODO Caleb: Render histogram, let users select based on slider
		private fun <T : RealType<T>> estimateWithHistogram(type: T, screenSource: IntervalView<RealType<*>>, rawSource: SourceState<*, RealType<*>>, converter: ARGBColorConverter<*>) {
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

		private fun converterAtDefault(rawSource: SourceState<*, RealType<*>>, converter: ARGBColorConverter<*>): Boolean {
			((rawSource as? SourceStateWithBackend<*, *>)?.backend as? SourceStateBackendN5<*, *>)?.metadataState?.let {
				return converter.min == it.minIntensity && converter.max == it.maxIntensity
			}

			val dataSource = rawSource.getDataSource().getSource(0, 0)
			val extension = dataSource.type.createVariable().let {
				when (it) {
					is VolatileLabelMultisetType, is LabelMultisetType -> UnsignedLongType(0)
					else -> it
				}
			}

			return converter.minProperty().get() == extension.minValue && converter.maxProperty().get() == extension.maxValue
		}

		fun resetIntensityMinMax(rawSource: SourceState<*, RealType<*>>) {

			val converter = rawSource.converter() as? ARGBColorConverter<*> ?: return



			((rawSource as? SourceStateWithBackend<*, *>)?.backend as? SourceStateBackendN5<*, *>)?.metadataState?.let {
				converter.min = it.minIntensity
				converter.max = it.maxIntensity
				return
			}

			val dataSource = rawSource.getDataSource().getSource(0, 0)
			val extension = dataSource.type.createVariable().let {
				when (it) {
					is VolatileLabelMultisetType, is LabelMultisetType -> UnsignedLongType(0)
					else -> it
				}
			}

			converter.min = extension.minValue
			converter.max = extension.maxValue
		}
	}

}


