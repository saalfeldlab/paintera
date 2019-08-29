package org.janelia.saalfeldlab.fx

import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent

class TextFields {
	companion object {
		@JvmStatic
		@JvmOverloads
		fun editableOnDoubleClick(text: String? = null): TextField {
			val field = TextField(text).also { it.isEditable = false }
			val initialValue = SimpleStringProperty()

			val setEditable = { initialValue.value = field.text; field.isEditable = true }
			val setText = { newText: String? -> field.isEditable = false; field.text = newText;}

			field.addEventHandler(MouseEvent.MOUSE_PRESSED) {
				if (it.clickCount == 2 && !field.isEditable) {
					it.consume()
					setEditable()
				}
			}

			field.addEventHandler(KeyEvent.KEY_PRESSED) {
				if (ESCAPE_COMBINATION.match(it) && field.isEditable) {
					it.consume()
					setText(initialValue.value)
				} else if (ENTER_COMBINATION.match(it) && field.isEditable) {
					it.consume()
					setText(field.text)
				} else if (ENTER_COMBINATION.match(it) && !field.isEditable) {
					it.consume()
					setEditable()
				}
			}

			field.focusedProperty().addListener { _,_, new -> if (!new && field.isEditable) setText(initialValue.value) }

			return field

		}

		private val ENTER_COMBINATION = KeyCodeCombination(KeyCode.ENTER)

		private val ESCAPE_COMBINATION = KeyCodeCombination(KeyCode.ESCAPE)
	}
}
