package org.janelia.saalfeldlab.paintera.control.actions.navigation

import javafx.beans.property.DoubleProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.ui.PositiveDoubleTextFormatter
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow
import org.janelia.saalfeldlab.paintera.ui.vGrow

interface GoToCoordinateUIState {

	val xProperty: DoubleProperty
	val yProperty: DoubleProperty
	val zProperty: DoubleProperty

}

class GoToCoordinateUI(val state: GoToCoordinateUIState) : HBox() {

	init {

		hvGrow()
		spacing = 10.0
		padding = Insets(10.0)
		children += VBox().vGrow {
			children += Label("Coordinate")
			alignment = Pos.BOTTOM_RIGHT
		}
		children += HBox().hvGrow {
			listOf("X" to state.xProperty, "Y" to state.yProperty, "Z" to state.zProperty).forEach { (axis, property) ->
				children += VBox().hvGrow {
					spacing = 5.0
					children += HBox().hGrow {
						children += Label(axis)
						alignment = Pos.BOTTOM_CENTER
					}
					children += TextField().hGrow {
						promptText = "Source $axis Coordinate... "
						textFormatter = PositiveDoubleTextFormatter().apply {
							value = property.value
							property.bind(valueProperty())

						}
					}
				}
			}
		}

	}
}