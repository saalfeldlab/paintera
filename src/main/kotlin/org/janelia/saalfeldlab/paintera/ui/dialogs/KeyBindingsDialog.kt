package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.scene.control.Alert
import javafx.scene.control.TitledPane
import javafx.stage.Modality
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfigNode
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory

internal object KeyBindingsDialog {

	private val LOG = LoggerFactory.getLogger(this::class.java)

	val keyBindingsDialog = KeyAndMouseConfigNode(properties.keyAndMouseConfig, paintera.baseView.sourceInfo()).makeNode()

	val keyBindingsPane = TitledPane("Key Bindings", keyBindingsDialog).also {
		it.isExpanded = true
		it.isCollapsible = false
	}
	val dialog: Alert by lazy {
		PainteraAlerts.information("_Close").apply {
			initModality(Modality.NONE)
			dialogPane.content = keyBindingsPane
			graphic = null
			headerText = null
			dialogPane.minWidth = 1000.0
			dialogPane.minHeight = 800.0
		}
	}


	fun show() {
		dialog.show()
	}
}
