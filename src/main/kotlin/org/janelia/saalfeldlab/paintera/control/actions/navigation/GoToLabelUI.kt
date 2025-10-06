package org.janelia.saalfeldlab.paintera.control.actions.navigation

import javafx.beans.property.BooleanProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleLongProperty
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.navigation.GoToLabelUI.Model.Companion.getDialog
import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.hGrow
import kotlin.math.absoluteValue
import kotlin.random.Random


class GoToLabelUI(val model: Model) : VBox(5.0) {

	interface Model {
		val labelProperty: LongProperty
		val activateLabelProperty: BooleanProperty

		companion object {

			fun Model.getDialog(dialogTitle: String = "Go To Label"): Alert {
				return PainteraAlerts.confirmation("_Go", "_Cancel").apply {
					title = dialogTitle
					headerText = "Go to Label"
					bindDialog(this)
				}
			}
		}

		fun bindDialog(dialog: Dialog<ButtonType>) = dialog.apply {
			dialogPane.content = GoToLabelUI(this@Model)
			(dialogPane.lookupButton(ButtonType.OK) as Button).apply {
				val modelInvalid = labelProperty.map { it.toLong() < 0 }
				disableProperty().unbind()
				disableProperty().bind(modelInvalid)
			}
		}
	}

	class Default : Model {
		override val labelProperty = SimpleLongProperty(-1L)
		override val activateLabelProperty = SimpleBooleanProperty(true)
	}

	init {
		padding = Insets(5.0)
		alignment = Pos.CENTER_RIGHT
		children += HBox(5.0).apply {
			alignment = Pos.BOTTOM_RIGHT
			children += Label("Label ID:")
			children += TextField().hGrow {
				alignment = Pos.CENTER_RIGHT
				textFormatter = PositiveLongTextFormatter(model.labelProperty.value).apply {
					val valueOrInvalid = valueProperty().createNullableValueBinding { it?.takeIf { it >= 0 } ?: -1L }
					valueOrInvalid.subscribe { it ->
						model.labelProperty.value = it
					}
				}
			}
		}
		children += HBox(5.0).apply {
			alignment = Pos.BOTTOM_RIGHT
			children += Label("Activate Label? ")
			children += CheckBox().apply {
				selectedProperty().bindBidirectional(model.activateLabelProperty)
			}
		}
	}
}


fun main() {
	InvokeOnJavaFXApplicationThread {

		val model = {
			GoToLabelUI.Default().apply {
				labelProperty.set(Random.nextLong().absoluteValue)
			}
		}

		val dialog = model().getDialog().apply {
			val reloadButton = ButtonType("Reload", ButtonBar.ButtonData.LEFT)
			dialogPane.buttonTypes += reloadButton
			(dialogPane.lookupButton(reloadButton) as? Button)?.addEventFilter(ActionEvent.ACTION) {
				model().bindDialog(this)
				it.consume()
			}
		}
		dialog.show()
	}
}

