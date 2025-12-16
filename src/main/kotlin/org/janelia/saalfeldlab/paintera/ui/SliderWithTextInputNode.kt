package org.janelia.saalfeldlab.paintera.ui

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlin.collections.plusAssign

data class SliderWithTextInputNode(val label: Label, val resetBtn: Button, val textField: TextField, val slider: Slider) {

	fun makeNode() = VBox(5.0).hGrow(Priority.ALWAYS) {
		children += HBox(10.0, label.hGrow(Priority.NEVER), textField.hGrow(), resetBtn.hGrow(Priority.NEVER)).apply { alignment = Pos.CENTER_RIGHT }
		children += HBox(10.0, slider.hGrow(Priority.ALWAYS))
	}
}