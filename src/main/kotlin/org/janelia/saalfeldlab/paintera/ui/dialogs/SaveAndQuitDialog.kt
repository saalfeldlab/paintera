package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import kotlin.jvm.optionals.getOrNull

internal object SaveAndQuitDialog {
	private const val DIALOG_HEADER = "Save project state before exiting?"
	private const val CANCEL_LABEL = "_Cancel"
	private const val SAVE_AS_AND_QUIT = "Save _As And Quit"
	private const val SAVE_AND_QUIT = "_Save And Quit"
	private const val OK_BUTTON_TEXT = "_Quit Without Saving"

	private val saveAs = ButtonType(SAVE_AS_AND_QUIT, ButtonBar.ButtonData.OK_DONE)
	private val save = ButtonType(SAVE_AND_QUIT, ButtonBar.ButtonData.APPLY)

	private val dialog: Alert by lazy {
		PainteraAlerts.confirmation(OK_BUTTON_TEXT, CANCEL_LABEL, false).apply {
			headerText = DIALOG_HEADER
			dialogPane.buttonTypes.addAll(save, saveAs)
			dialogPane.buttonTypes.map { dialogPane.lookupButton(it) as Button }.forEach { it.isDefaultButton = false }
		}
	}

	internal fun showAndWaitForResponse(): Boolean {
		val saveButton = dialog.dialogPane.lookupButton(save) as Button
		val saveAsButton = dialog.dialogPane.lookupButton(saveAs) as Button
		val okButton = dialog.dialogPane.lookupButton(ButtonType.OK) as Button

		if (paintera.projectDirectory.directory === null) {
			saveAsButton.isDefaultButton = true
			saveButton.isDisable = true
		} else
			saveButton.isDefaultButton = true

		saveButton.onAction = EventHandler {
			paintera.save(false)
			okButton.fire()
		}
		// to display other dialog before closing, event filter is necessary:
		// https://stackoverflow.com/a/38696246
		saveAsButton.addEventFilter(ActionEvent.ACTION) {
			it.consume()
			if (paintera.saveAs(notify = false)) {
				okButton.fire()
			}
		}
		return dialog.showAndWait().getOrNull() == ButtonType.OK
	}

}
