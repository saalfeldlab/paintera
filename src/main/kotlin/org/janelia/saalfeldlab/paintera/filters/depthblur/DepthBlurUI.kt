package org.janelia.saalfeldlab.paintera.filters.depthblur

import javafx.application.Application
import javafx.geometry.Insets
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.scene.shape.Polygon
import javafx.scene.shape.Polyline
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.stage.Stage
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.filters.RenderFilterSettingsGrid
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlur.Blend
import org.janelia.saalfeldlab.paintera.ui.sliderWithSpinner
import org.janelia.saalfeldlab.paintera.ui.withTooltip
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round

class DepthBlurUI(model: DepthBlurModel) : RenderFilterSettingsGrid(model, "Reset Depth Blur settings") {

	init {
		val slices = sliderWithSpinner(
			Slider(MIN_SLICES, MAX_SLICES, model.slicesPerSideProperty.get().toDouble()).apply {
				majorTickUnit = 1.0
				minorTickCount = 0
				blockIncrement = 1.0
				isSnapToTicks = true
				isShowTickMarks = true
			},
			Spinner(SpinnerValueFactory.IntegerSpinnerValueFactory(1, MAX_SLICES_INPUT, model.slicesPerSideProperty.get())),
			model.slicesPerSideProperty
		) { it.toInt() }
		val spacing = sliderWithSpinner(
			Slider(MIN_SPACING, MAX_SPACING, model.spacingProperty.get()),
			Spinner(SpinnerValueFactory.DoubleSpinnerValueFactory(MIN_SPACING_INPUT, Double.MAX_VALUE, model.spacingProperty.get(), 0.25)),
			model.spacingProperty
		) { round(it * 100) / 100 }

		val blend = ComboBox<Blend>().apply {
			items.setAll(*Blend.entries.toTypedArray())
			valueProperty().bindBidirectional(model.blendProperty)
		}
		val cutoff = sliderWithSpinner(
			Slider(MIN_CUTOFF, MAX_CUTOFF, model.gaussianCutoffProperty.get()),
			Spinner(SpinnerValueFactory.DoubleSpinnerValueFactory(MIN_CUTOFF_INPUT, Double.MAX_VALUE, model.gaussianCutoffProperty.get(), 0.1)),
			model.gaussianCutoffProperty.asObject()
		) { round(it * 100) / 100 }
		val cutoffLabel = Label("Cutoff (σ)").withTooltip("The +/- sigma of the gaussian at the farthest slices. Smaller is a wider, flatter gaussian")
		listOf(cutoffLabel, cutoff).forEach {
			it.visibleProperty().bind(model.blendProperty.isEqualTo(Blend.GAUSSIAN))
			it.managedProperty().bind(it.visibleProperty())
		}

		addSetting(Label("Slices per side").withTooltip("Number of slices rendered on each side of the viewer plane. Total number of slices is 2n+1"), slices)
		addSetting(Label("Spacing").withTooltip("Distance factor between slices in the viewer depth. Maps to the same physical distance regardless of rotation or anisotropy"), spacing)
		addSetting(Label("Filter"), blend)
		addSetting(cutoffLabel, cutoff)
		addWideRow(DepthBlurPlot(model))
	}

	private companion object {
		const val MIN_SLICES = 1.0
		const val MAX_SLICES = 10.0
		const val MAX_SLICES_INPUT = 25
		const val MIN_SPACING = 1.0
		const val MAX_SPACING = 10.0
		const val MIN_SPACING_INPUT = 0.1
		const val MIN_CUTOFF = 0.5
		const val MIN_CUTOFF_INPUT = 0.1
		const val MAX_CUTOFF = 4.0
	}
}

fun main() {
	Application.launch(DepthBlurUIApplication::class.java)
}

class DepthBlurUIApplication : Application() {
	override fun start(stage: Stage) {
		stage.scene = Scene(DepthBlurUI(DepthBlurModel.default()).apply { padding = Insets(10.0) }).also { Paintera.registerStylesheets(it) }
		stage.title = "Depth Blur (preview)"
		stage.show()
	}
}

private fun Double.display(): String {
	val rounded = round(this * 100) / 100
	return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/** the Gaussian over the slice offsets, laid out in pixels across the width it is given, with text at the labels' size */
private class DepthBlurPlot(private val model: DepthBlurModel) : Region() {

	private val content = Group().apply { isManaged = false }
	private val fontSize = Font.getDefault().size

	init {
		opacityProperty().bind(disabledProperty().map { if (it) DISABLED_OPACITY else 1.0 })
		children += content
		widthProperty().subscribe { _ -> redraw() }
		listOf(model.slicesPerSideProperty, model.spacingProperty, model.blendProperty, model.gaussianCutoffProperty).forEach { property ->
			property.subscribe { _ -> redraw() }
		}
	}

	override fun computePrefWidth(height: Double) = 0.0

	override fun computePrefHeight(width: Double) = BASELINE + CUTOFF_LABEL_HEIGHT + 2 * ROW_HEIGHT + BOTTOM_PADDING

	/** the height of the kernel, 1 at the peak, at [x] as a fraction of the half width; the outermost slice is at 1 */
	private fun DepthBlurModel.yAt(x: Double) = when (blendProperty.get()) {
		Blend.MEAN -> 1.0
		Blend.GAUSSIAN -> exp(-0.5 * (x * gaussianCutoffProperty.get()).pow(2))
	}

	private fun redraw() {
		val slices = model.slicesPerSideProperty.get()
		val spacing = model.spacingProperty.get()
		val isGaussian = model.blendProperty.get() == Blend.GAUSSIAN
		val cutoff = model.gaussianCutoffProperty.get()

		val centerX = width / 2
		val span = width / 2 - MARGIN
		val step = span / slices
		fun curveY(offset: Double) = BASELINE - CURVE_HEIGHT * model.yAt(offset / span)
		val curveOffsets = generateSequence(-span) { it + CURVE_STEP }.takeWhile { it <= span }.toList()

		val nodes = mutableListOf<Node>()
		nodes += Line(centerX - span, BASELINE, centerX + span, BASELINE).apply { stroke = Color.BLACK }
		nodes += Polygon().apply {
			points += listOf(centerX - span, BASELINE)
			curveOffsets.forEach { points += listOf(centerX + it, curveY(it)) }
			points += listOf(centerX + span, BASELINE)
			fill = AREA
		}
		nodes += Polyline().apply {
			curveOffsets.forEach { points += listOf(centerX + it, curveY(it)) }
			stroke = CURVE
			strokeWidth = 2.0
			fill = Color.TRANSPARENT
		}

		for (index in -slices..slices) {
			val x = centerX + index * step
			val isCenter = index == 0
			val isLast = index == -slices || index == slices
			val color = if (isLast) CUTOFF else Color.BLACK
			nodes += Line(x, curveY(index * step), x, BASELINE + 5.0).apply {
				stroke = color
				strokeWidth = if (isCenter || isLast) 2.0 else 1.0
				if (!isCenter)
					strokeDashArray.setAll(4.0, 3.0)
			}
			nodes += Circle(x, curveY(index * step), if (isCenter) 4.5 else 3.5, color)
		}
		nodes += bar(centerX - step, centerX, BASELINE + CUTOFF_LABEL_HEIGHT + ROW_HEIGHT, "${(spacing * model.physicalPerSpacing).display()} ${model.unit}")
		nodes += bar(centerX - span, centerX + span, BASELINE + CUTOFF_LABEL_HEIGHT + 2 * ROW_HEIGHT, "${(2 * slices * spacing * model.physicalPerSpacing).display()} ${model.unit}")
		if (isGaussian) {
			nodes += label("${(-cutoff).display()} σ", centerX - span, BASELINE + CUTOFF_LABEL_HEIGHT - 4.0)
			nodes += label("${cutoff.display()} σ", centerX + span, BASELINE + CUTOFF_LABEL_HEIGHT - 4.0)
		}
		content.children.setAll(nodes)
	}

	private fun bar(x1: Double, x2: Double, labelBaselineY: Double, text: String): List<Node> {
		val y = labelBaselineY - fontSize - 4.0
		return listOf(
			Line(x1, y, x2, y).apply { stroke = Color.BLACK },
			Line(x1, y - 5.0, x1, y + 5.0).apply { stroke = Color.BLACK },
			Line(x2, y - 5.0, x2, y + 5.0).apply { stroke = Color.BLACK },
			label(text, (x1 + x2) / 2, labelBaselineY)
		)
	}

	private fun label(text: String, centerX: Double, baselineY: Double) = Text(text).apply {
		font = Font.font(Font.getDefault().family, FontWeight.SEMI_BOLD, fontSize)
		x = centerX - layoutBounds.width / 2
		y = baselineY
	}

	private companion object {
		const val MARGIN = 28.0
		const val BASELINE = 120.0
		const val CURVE_HEIGHT = 100.0
		const val CUTOFF_LABEL_HEIGHT = 24.0
		const val ROW_HEIGHT = 36.0
		const val DISABLED_OPACITY = 0.4
		const val BOTTOM_PADDING = 8.0
		const val CURVE_STEP = 3.0

		val CURVE: Color = Color.BLUE
		val AREA: Color = Color.color(0.0, 0.0, 1.0, 0.1)
		val CUTOFF: Color = Color.RED
	}
}
