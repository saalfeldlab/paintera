package org.janelia.saalfeldlab.fx.event

import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.input.*
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class MouseCombination private constructor(keyCombination: KeyCombination) {

	constructor(keyCode: KeyCode, vararg modifier: KeyCombination.Modifier): this(KeyCodeCombination(keyCode, *modifier))

	constructor(vararg modifier: KeyCombination.Modifier): this(OnlyModifierKeyCombination(*modifier))

	private val _keyCombination: ObjectProperty<KeyCombination> = SimpleObjectProperty(keyCombination)

	var keyCombination: KeyCombination
		get() = _keyCombination.value
		set(keyCombination) = setKeyCombinationChecked(keyCombination)

	private fun setKeyCombinationChecked(keyCombination: KeyCombination) {
		require(keyCombination is KeyCodeCombination || keyCombination is OnlyModifierKeyCombination) {
			"Currently only ${KeyCodeCombination::class} and ${OnlyModifierKeyCombination::class} are supported but got $keyCombination."
		}
		_keyCombination.value = keyCombination
	}

	fun match(event: MouseEvent, tracker: KeyTracker): Boolean {

		val keyCodes = tracker.getActiveKeyCodes(false);

		return if (keyCodes.size > 1) {
			false.also { LOG.trace("Mouse combinations with more than one non-modifier key are not supported.") }
		} else {
			val keyCode = if (keyCodes.size == 0) null else keyCodes[0]
			val keyEvent = KeyEvent(
					null,
					null,
					null,
					null,
					null,
					keyCode,
					event.isShiftDown,
					event.isControlDown,
					event.isAltDown,
					event.isMetaDown)
			keyCombination.match(keyEvent)
		}
	}

	val deepCopy: MouseCombination
		get() = MouseCombination(keyCombination)

	class OnlyModifierKeyCombination(vararg modifier: Modifier): KeyCombination(*modifier)

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
	}


}

fun main() {
	PlatformImpl.startup {  }
	Platform.runLater {
		val node = Button("DSLFKJSDLKFJSDLKGFJSDLGKDSJGSDG")


		val keyTracker = KeyTracker()
		val combination1 = MouseCombination(KeyCode.F, KeyCombination.CONTROL_DOWN)
		val combination2 = MouseCombination(KeyCombination.CONTROL_ANY)

		node.addEventHandler(MouseEvent.MOUSE_MOVED) {
			if (combination1.match(it, keyTracker)) {
				it.consume()
				println("MATCHED1!")
			}
		}
		node.addEventHandler(MouseEvent.MOUSE_MOVED) {
			if (combination2.match(it, keyTracker)) {
				it.consume()
				println("MATCHED2!")
			}
		}

		val stage = Stage().also { it.scene = Scene(node) }
		keyTracker.installInto(stage)
		stage.show()
	}
}
