package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.stage.Modality
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts.initAppDialog
import org.scijava.Context
import org.scijava.scripting.fx.SciJavaReplFXDialog

internal class ReplDialog(
	private val context: Context,
	private vararg val bindings: Pair<String, *>,
) {
	private val dialog by lazy {
		SciJavaReplFXDialog(context, *bindings).apply {
			initAppDialog(null, Modality.NONE)
			title = "${Constants.NAME} - Scripting REPL"
		}
	}

	fun show() {
		dialog.show()
		dialog.dialogPane.addEventHandler(KeyEvent.KEY_PRESSED) {
			if (KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN).match(it)) {
				it.consume()
				dialog.hide()
			}
		}
	}
}
