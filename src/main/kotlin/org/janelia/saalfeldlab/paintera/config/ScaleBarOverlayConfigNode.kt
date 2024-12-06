package org.janelia.saalfeldlab.paintera.config

import org.janelia.saalfeldlab.bdv.fx.viewer.scalebar.ScaleBarOverlayConfig
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.text.Font
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField

class ScaleBarOverlayConfigNode() : TitledPane("Scale Bar", null) {

	constructor(config: ScaleBarOverlayConfig) : this() {
		bindBidirectionalTo(config)
	}

	private val isShowing = CheckBox()

	private val targetScaleBarLength = NumberField.doubleField(1.0, {  true }, *ObjectField.SubmitOn.entries.toTypedArray())

	private val foregroundColorPicker = ColorPicker()

	private val backgroundColorPicker = ColorPicker()

	private val font = SimpleObjectProperty(Font("SansSerif", 18.0))

	private val fontSize = NumberField.doubleField(font.get().size, { v -> v > 0.0 }, *ObjectField.SubmitOn.entries.toTypedArray())

	init {
		val grid = GridPane()
		content = grid
		graphic = isShowing
		isExpanded = false
		grid.add(Label("Scale Bar Size"), 0, 0)
		grid.add(targetScaleBarLength.textField, 1, 0)
		grid.add(Label("Font Size"), 0, 1)
		grid.add(fontSize.textField, 1, 1)
		grid.add(Label("Foreground Color"), 0, 2)
		grid.add(Label("Background Color"), 0, 3)
		grid.add(foregroundColorPicker, 1, 2)
		grid.add(backgroundColorPicker, 1, 3)
		grid.hgap = 5.0

		GridPane.setHgrow(targetScaleBarLength.textField, Priority.ALWAYS)
		GridPane.setHgrow(fontSize.textField, Priority.ALWAYS)
		GridPane.setHgrow(foregroundColorPicker, Priority.ALWAYS)
		GridPane.setHgrow(backgroundColorPicker, Priority.ALWAYS)

		foregroundColorPicker.maxWidth = java.lang.Double.POSITIVE_INFINITY
		backgroundColorPicker.maxWidth = java.lang.Double.POSITIVE_INFINITY

		font.subscribe { it -> fontSize.valueProperty().set(it.size) }
		fontSize.valueProperty().subscribe { it -> font.set(Font(font.get().name, it.toDouble())) }
	}

	fun bindBidirectionalTo(config: ScaleBarOverlayConfig) {
		this.targetScaleBarLength.valueProperty().bindBidirectional(config.targetScaleBarLengthProperty())
		this.isShowing.selectedProperty().bindBidirectional(config.isShowingProperty)
		this.foregroundColorPicker.valueProperty().bindBidirectional(config.foregroundColorProperty())
		this.backgroundColorPicker.valueProperty().bindBidirectional(config.backgroundColorProperty())
		this.font.bindBidirectional(config.overlayFontProperty())

		this.targetScaleBarLength.valueProperty().set(config.targetScaleBarLength)
		this.isShowing.isSelected = config.isShowing
		this.foregroundColorPicker.value = config.foregroundColor
		this.backgroundColorPicker.value = config.backgroundColor
		this.font.set(config.overlayFont)
	}

}
