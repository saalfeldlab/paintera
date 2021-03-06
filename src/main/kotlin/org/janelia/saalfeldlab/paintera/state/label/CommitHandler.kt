package org.janelia.saalfeldlab.paintera.state.label

import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.BiFunction

class CommitHandler(private val state: ConnectomicsLabelState<*, *>) {

    fun globalHandler(
        paintera: PainteraBaseView,
        bindings: KeyAndMouseBindings,
        keyTracker: KeyTracker
    ) = makeHandler(paintera, bindings, keyTracker)

    private fun makeHandler(
        paintera: PainteraBaseView,
        bindings: KeyAndMouseBindings,
        keyTracker: KeyTracker
    ): EventHandler<Event> {
        val handler = DelegateEventHandlers.handleAny()
        handler.addOnKeyPressed { ev ->
            if (bindings.keyCombinations[ConnectomicsLabelState.BindingKeys.COMMIT_DIALOG]!!.primaryCombination.match(ev)) {
                if (paintera.allowedActionsProperty().get().isAllowed(MenuActionType.CommitCanvas)) {
                    ev.consume()
                    showCommitDialog(state, paintera.sourceInfo().indexOf(state.getDataSource()), true)
                }
            }
        }
        return handler
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        @JvmStatic
        @JvmOverloads
        fun showCommitDialog(
            state: ConnectomicsLabelState<*, *>,
            index: Int,
            showDialogIfNothingToCommit: Boolean,
            headerText: BiFunction<Int, String, String> = BiFunction { idx, name -> "Commit fragment-segment assignment and/or canvas for source $idx: $name" },
            clearCanvas: Boolean = true,
            cancelButtonText: String = "_Cancel",
            okButtonText: String = "Commi_t"
        ) {
            val assignmentsCanBeCommitted = state.fragmentSegmentAssignment.hasPersistableData()
            val canvasCanBeCommitted = state.getDataSource().let { it is MaskedSource && it.getAffectedBlocks().isNotEmpty() }
            val commitAssignmentCheckbox = CheckBox("Fragment-segment assignment").also { it.isSelected = assignmentsCanBeCommitted }
            val commitCanvasCheckbox = CheckBox("Canvas").also { it.isSelected = canvasCanBeCommitted }
            val anythingToCommit = assignmentsCanBeCommitted || canvasCanBeCommitted
            val name = state.nameProperty().get()
            val dialog = if (anythingToCommit) {
                val contents = VBox()
                if (assignmentsCanBeCommitted) contents.children.add(commitAssignmentCheckbox)
                if (canvasCanBeCommitted) contents.children.add(commitCanvasCheckbox)
                PainteraAlerts
                    .confirmation(okButtonText, cancelButtonText, true)
                    .also { it.headerText = headerText.apply(index, name) }
                    .also { it.dialogPane.content = contents }
            } else {
                if (showDialogIfNothingToCommit)
                    PainteraAlerts
                        .alert(Alert.AlertType.INFORMATION, true)
                        .also { (it.dialogPane.lookupButton(ButtonType.OK) as Button).text = "_OK" }
                        .also { it.headerText = "Nothing to commit for source $index: $name" }
                else
                    null
            }
            if (dialog?.showAndWait()?.filter { ButtonType.OK == it }?.isPresent == true && anythingToCommit) {
                if (assignmentsCanBeCommitted && commitAssignmentCheckbox.isSelected) state.fragmentSegmentAssignment.persist()
                state.getDataSource().let {
                    if (canvasCanBeCommitted && commitCanvasCheckbox.isSelected && it is MaskedSource) {
                        it.persistCanvas(clearCanvas)
                    }
                }
            }
        }

    }
}
