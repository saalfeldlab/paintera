package org.janelia.saalfeldlab.paintera.control.actions.navigation

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.PositiveDoubleTextFormatter
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow
import org.janelia.saalfeldlab.paintera.ui.vGrow


class GoToCoordinateUI(val model: Model) : HBox() {

	interface Model {

		val xProperty : DoubleProperty
		val yProperty : DoubleProperty
		val zProperty : DoubleProperty

		fun Action<*>.getDialog(header: String, title : String = name?.replace("_", "") ?: "Go to Coordinate"): Alert {
			return PainteraAlerts.confirmation("Go", "Cancel").apply {
				this.title = title
				headerText = header
				dialogPane.content = GoToCoordinateUI(this@Model)
			}
		}
	}

	open class Default : Model {

		override val xProperty = SimpleDoubleProperty()
		override val yProperty = SimpleDoubleProperty()
		override val zProperty = SimpleDoubleProperty()
	}

	init {

		spacing = 10.0
		padding = Insets(10.0)
		children += VBox().vGrow {
			children += Label("Coordinates")
			alignment = Pos.BOTTOM_RIGHT

		}
		children += HBox().hvGrow {
			listOf("X" to model.xProperty, "Y" to model.yProperty, "Z" to model.zProperty).forEach { (axis, property) ->
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