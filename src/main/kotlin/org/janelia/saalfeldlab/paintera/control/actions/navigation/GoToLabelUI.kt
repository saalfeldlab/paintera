package org.janelia.saalfeldlab.paintera.control.actions.navigation

import javafx.beans.property.BooleanProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleLongProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow


interface GoToLabelUIState {
	val labelProperty: LongProperty
	val activateLabelProperty: BooleanProperty
}

class GoToLabelUI(val state: GoToLabelUIState) : VBox(5.0) {

	init {
		hvGrow()
		padding = Insets(5.0)
		alignment = Pos.CENTER_RIGHT
		children += HBox(5.0).apply {
			alignment = Pos.BOTTOM_RIGHT
			children += Label("Label ID:")
			children += TextField().hGrow {
				textFormatter = PositiveLongTextFormatter().apply {
					value = state.labelProperty.value
					state.labelProperty.bind(valueProperty())
				}
			}
		}
		children += HBox(5.0).apply {
			alignment = Pos.BOTTOM_RIGHT
			children += Label("Activate Label? ")
			children += CheckBox().apply {
				selectedProperty().bindBidirectional(state.activateLabelProperty)
				isSelected = true
			}
		}

	}
}


fun main() {
	InvokeOnJavaFXApplicationThread {

		val state = object : GoToLabelUIState {
			override val labelProperty = SimpleLongProperty(1234L)
			override val activateLabelProperty = SimpleBooleanProperty(true)
		}

		val root = VBox()
		root.apply {
			children += Button("Reload").apply {
				onAction = EventHandler {
					root.children.removeIf { it is GoToLabelUI }
					root.children.add(GoToLabelUI(state))
				}
			}
			children += GoToLabelUI(state)
		}

		val scene = Scene(root)
		val stage = Stage()
		stage.scene = scene
		stage.show()
	}
}

