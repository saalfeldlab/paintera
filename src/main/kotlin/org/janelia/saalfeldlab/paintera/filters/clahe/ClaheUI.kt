package org.janelia.saalfeldlab.paintera.filters.clahe

import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import org.janelia.saalfeldlab.paintera.filters.RenderFilterSettingsGrid
import org.janelia.saalfeldlab.paintera.ui.sliderWithSpinner
import org.janelia.saalfeldlab.paintera.ui.withTooltip
import kotlin.math.round

class ClaheUI(model: ClaheModel) : RenderFilterSettingsGrid(model, "Reset CLAHE settings") {

	init {
		val radius = sliderWithSpinner(
			Slider(MIN_RADIUS, MAX_RADIUS, model.radiusProperty.get()),
			Spinner(SpinnerValueFactory.DoubleSpinnerValueFactory(MIN_RADIUS, MAX_RADIUS_INPUT, model.radiusProperty.get(), 1.0)),
			model.radiusProperty
		) { round(it * 10) / 10 }
		val maxSlope = sliderWithSpinner(
			Slider(MIN_MAX_SLOPE, MAX_MAX_SLOPE, model.maxSlopeProperty.get()),
			Spinner(SpinnerValueFactory.DoubleSpinnerValueFactory(MIN_MAX_SLOPE, Double.MAX_VALUE, model.maxSlopeProperty.get(), 0.5)),
			model.maxSlopeProperty.asObject()
		) { round(it * 100) / 100 }
		val bins = sliderWithSpinner(
			Slider(MIN_BINS.toDouble(), MAX_BINS.toDouble(), model.binsProperty.get().toDouble()),
			Spinner(SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_BINS_INPUT, MAX_BINS_INPUT, model.binsProperty.get(), 1)),
			model.binsProperty
		) { it.toInt() }

		addSetting(Label("Radius (voxels)").withTooltip("Half width of a tile, in voxels of the displayed resolution"), radius)
		addSetting(Label("Max slope").withTooltip("Clip limit as a multiple of a flat histogram: 1 leaves the image as is, larger equalizes harder"), maxSlope)
		addSetting(Label("Bins").withTooltip("Histogram bins over the contrast range"), bins)
	}

	private companion object {
		const val MIN_RADIUS = 1.0
		const val MAX_RADIUS = 128.0
		const val MAX_RADIUS_INPUT = 1024.0
		const val MIN_MAX_SLOPE = 1.0
		const val MAX_MAX_SLOPE = 10.0
		const val MIN_BINS = 16
		const val MAX_BINS = 1024
		const val MIN_BINS_INPUT = 2
		const val MAX_BINS_INPUT = 4096
	}
}
