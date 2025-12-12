package org.janelia.saalfeldlab.paintera.config

import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField

class OrthoSliceConfigNode() {

	constructor(config: OrthoSliceConfig) : this() {
		bind(config)
	}

	private val contents: TitledPane

	private val topLeftCheckBox = CheckBox()

	private val topRightCheckBox = CheckBox()

	private val bottomLeftCheckBox = CheckBox()

	private val showOrthoViews = CheckBox()

	private val opacitySlider = NumericSliderWithField(0.0, 1.0, 0.5)

	private val shadingSlider = NumericSliderWithField(0.0, 1.0, 0.1)

	init {

		val grid = GridPane()
		var row = 0
		val textFieldWidth = 55.0

		val setupSlider = { sliderWithField: NumericSliderWithField, ttText: String ->
			sliderWithField.apply {
				textField.prefWidth = textFieldWidth
				slider.apply {
					isShowTickLabels = true
					tooltip = Tooltip(ttText)
				}
				GridPane.setHgrow(slider, Priority.ALWAYS)
			}
		}

		grid.add(Labels.withTooltip("Opacity"), 0, row)
		grid.add(opacitySlider.slider, 1, row)
		GridPane.setColumnSpan(opacitySlider.slider, 2)
		grid.add(opacitySlider.textField, 3, row)
		setupSlider(opacitySlider, "Opacity")
		++row

		grid.add(Labels.withTooltip("Shading"), 0, row)
		grid.add(shadingSlider.slider, 1, row)
		GridPane.setColumnSpan(shadingSlider.slider, 2)
		grid.add(shadingSlider.textField, 3, row)
		setupSlider(shadingSlider, "Shading")
		++row

		val topLeftLabel = Label("top left")
		val topRightLabel = Label("top right")
		val bottomLeftLabel = Label("bottom left")

		grid.add(topLeftLabel, 0, row)
		grid.add(topLeftCheckBox, 1, row)
		++row

		grid.add(topRightLabel, 0, row)
		grid.add(topRightCheckBox, 1, row)
		++row

		grid.add(bottomLeftLabel, 0, row)
		grid.add(bottomLeftCheckBox, 1, row)

		GridPane.setHgrow(topLeftLabel, Priority.ALWAYS)
		GridPane.setHgrow(topRightLabel, Priority.ALWAYS)
		GridPane.setHgrow(bottomLeftLabel, Priority.ALWAYS)

		contents = TitledPane("Ortho-Views", grid)
		contents.graphic = showOrthoViews
		contents.isExpanded = false
	}

	fun bind(config: OrthoSliceConfig) {
		showOrthoViews.selectedProperty().bindBidirectional(config.enableProperty())
		topLeftCheckBox.selectedProperty().bindBidirectional(config.showTopLeftProperty())
		topRightCheckBox.selectedProperty().bindBidirectional(config.showTopRightProperty())
		bottomLeftCheckBox.selectedProperty().bindBidirectional(config.showBottomLeftProperty())
		opacitySlider.valueProperty.bindBidirectional(config.opacityProperty())
		shadingSlider.valueProperty.bindBidirectional(config.shadingProperty())
	}

	fun getContents(): Node {
		return contents
	}

}
