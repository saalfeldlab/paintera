package org.janelia.saalfeldlab.paintera.state

import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class LabelSourceStateCommitHandler(private val state: LabelSourceState<*, *>) {

	fun globalHandler(paintera: PainteraBaseView, keyTracker: KeyTracker) = makeHandler(paintera, keyTracker)

    private fun makeHandler(paintera: PainteraBaseView, keyTracker: KeyTracker): EventHandler<Event>? {
		val handler = DelegateEventHandlers.handleAny()
		handler.addOnKeyPressed { ev ->
			if (COMMIT_KEY.match(ev)) {
				ev.consume()
				val assignmentsCanBeCommitted = state.assignment().hasPersistableData()
				val canvasCanBeCommitted = state.getDataSource().let { it is MaskedSource && it.getAffectedBlocks().isNotEmpty() }
				val commitAssignmentCheckbox = CheckBox("Fragment-segment assignment").also { it.isSelected = assignmentsCanBeCommitted }
				val commitCanvasCheckbox = CheckBox("Canvas").also { it.isSelected = canvasCanBeCommitted }
				val anythingToCommit = assignmentsCanBeCommitted || canvasCanBeCommitted
				val name = state.nameProperty().get()
				val index = paintera.sourceInfo().indexOf(state.getDataSource())
				val dialog = if (anythingToCommit) {
					val contents = VBox()
					if (assignmentsCanBeCommitted) contents.children.add(commitAssignmentCheckbox)
					if (canvasCanBeCommitted) contents.children.add(commitCanvasCheckbox)
					PainteraAlerts
							.confirmation("Commi_t", "_Cancel", true)
							.also { it.headerText = "Commit fragment-segment assignment and/or canvas for source $index: $name" }
							.also { it.dialogPane.content = contents }
				} else {
					PainteraAlerts
							.alert(Alert.AlertType.INFORMATION, true)
							.also { (it.dialogPane.lookupButton(ButtonType.OK) as Button).text = "_OK" }
							.also { it.headerText = "Nothing to commit for source $index: $name" }
				}
				if (dialog.showAndWait().filter { ButtonType.OK == it }.isPresent && anythingToCommit) {
					if (assignmentsCanBeCommitted && commitAssignmentCheckbox.isSelected) state.assignment().persist()
					state.getDataSource().let { if (canvasCanBeCommitted && commitCanvasCheckbox.isSelected && it is MaskedSource) it.persistCanvas() }
				}
			}
		}
        return handler
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val COMMIT_KEY = KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN)

    }
}
