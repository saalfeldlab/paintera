package org.janelia.saalfeldlab.paintera.state.label

import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.actions.KeyAction.Companion.onAction
import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination
import org.janelia.saalfeldlab.fx.actions.PainteraActionSet
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.BiFunction

class CommitHandler<S : SourceState<*, *>>(private val state: S, private val fragmentProvider: () -> FragmentSegmentAssignmentState) {

    internal fun makeActionSet(bindings: NamedKeyCombination.CombinationMap, paintera: PainteraBaseView) = PainteraActionSet(LabelSourceStateKeys.COMMIT_DIALOG, MenuActionType.CommitCanvas) {
        KEY_PRESSED.onAction(bindings, LabelSourceStateKeys.COMMIT_DIALOG) {
            showCommitDialog(state, paintera.sourceInfo().indexOf(state.dataSource), true, fragmentSegmentAssignmentState = fragmentProvider())
        }
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        @JvmStatic
        @JvmOverloads
        fun showCommitDialog(
            state: SourceState<*, *>,
            index: Int,
            showDialogIfNothingToCommit: Boolean,
            headerText: BiFunction<Int, String, String> = BiFunction { idx, name -> "Commit fragment-segment assignment and/or canvas for source $idx: $name" },
            clearCanvas: Boolean = true,
            cancelButtonText: String = "_Cancel",
            okButtonText: String = "Commi_t",
            fragmentSegmentAssignmentState: FragmentSegmentAssignmentState
        ) {
            val assignmentsCanBeCommitted = fragmentSegmentAssignmentState.hasPersistableData()
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
                if (assignmentsCanBeCommitted && commitAssignmentCheckbox.isSelected) fragmentSegmentAssignmentState.persist()
                state.dataSource.let {
                    if (canvasCanBeCommitted && commitCanvasCheckbox.isSelected && it is MaskedSource) {
                        it.persistCanvas(clearCanvas)
                    }
                }
            }
        }

    }
}
