package org.janelia.saalfeldlab.paintera.util.debug

import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.stage.Stage
import javafx.stage.Window
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.paintera

internal class DebugWindow : Stage() {

	init {
		val root = DebugNodes()
		scene = Scene(root, 400.0, 60.0)
	}


	companion object {

		fun initDebugWindowSubscription() {
			DebugModeProperty.subscribe { debug ->
				if (debug) {
					Paintera.whenPaintable {
						InvokeOnJavaFXApplicationThread {
								DebugWindow().let { window ->
									DebugModeProperty.trackDebugSubscription { window.close() }
									window.properties["debug_window"] = true
									window.show()
								}
						}
					}
				}
			}
		}
	}
}


internal open class DebugWindowAction(text : String = "Debug Window...") : MenuAction(text, KeyEvent.KEY_TYPED) {

	fun startOrFocusDebugWindow() {
		Window.getWindows().firstOrNull { it.properties["debug_window"] == true }?.also {
			it.requestFocus()
		} ?: DebugWindow.initDebugWindowSubscription()
	}

	init {
		keyTracker = { paintera.keyTracker }
		filter = true
		consume = false
		keysDown = listOf(KeyCode.BACK_SLASH, KeyCode.W)
		verify("Debug Mode is Active") { DebugModeProperty.get() }
		onAction { startOrFocusDebugWindow() }
	}
}


