package org.janelia.saalfeldlab.paintera.ui

import javafx.beans.property.ObjectProperty
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.paintera.Style.RESET_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass

private const val SPINNER_WIDTH = 80.0

fun resetButton(tooltipText: String, onAction: () -> Unit) = Button().apply {
	addStyleClass(RESET_ICON)
	setOnAction { onAction() }
	tooltip = Tooltip(tooltipText)
}

fun Label.withTooltip(text: String) = apply { tooltip = Tooltip(text) }

/** a slider and an editable spinner of [property]; the spinner accepts values outside the slider's range */
fun <T : Number> sliderWithSpinner(slider: Slider, spinner: Spinner<T>, property: ObjectProperty<T>, fromSlider: (Double) -> T) = HBox(10.0).apply {
	spinner.isEditable = true
	spinner.prefWidth = SPINNER_WIDTH
	spinner.valueFactory.valueProperty().bindBidirectional(property)
	slider.valueProperty().subscribe { _, new -> property.set(fromSlider(new.toDouble())) }
	property.subscribe { value ->
		value.toDouble().takeIf { it in slider.min..slider.max }?.let { slider.value = it }
	}
	HBox.setHgrow(slider, Priority.ALWAYS)
	children += listOf(slider, spinner)
}
