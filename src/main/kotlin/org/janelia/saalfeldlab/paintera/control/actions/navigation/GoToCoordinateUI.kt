package org.janelia.saalfeldlab.paintera.control.actions.navigation

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.ui.PositiveDoubleTextFormatter
import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow
import org.janelia.saalfeldlab.paintera.ui.vGrow


class GoToCoordinateUI(val model: GoToCoordinateModel) : HBox() {

	init {

		spacing = 10.0
		padding = Insets(10.0)
		children += VBox().vGrow {
			children += Label("Coordinates")
			alignment = Pos.BOTTOM_RIGHT

		}
		children += HBox().hvGrow {
			model.positionProperties.forEach { positionProperty ->
				children += VBox().hvGrow {
					spacing = 5.0
					children += HBox().hGrow {
						children += Label(positionProperty.label)
						alignment = Pos.BOTTOM_CENTER
					}
					children += TextField().hGrow {
						promptText = "Source ${positionProperty.label.uppercase()} Coordinate"
						when (val property = positionProperty.property) {
							is SimpleDoubleProperty -> textFormatter = PositiveDoubleTextFormatter().apply {
								value = property.value
								property.bind(valueProperty())
							}
							is SimpleLongProperty -> textFormatter = PositiveLongTextFormatter().apply {
								value = property.value
								property.bind(valueProperty())
							}
							else -> error("Unsupported property type: ${property::class}")
						}
					}
				}
			}
		}

	}
}