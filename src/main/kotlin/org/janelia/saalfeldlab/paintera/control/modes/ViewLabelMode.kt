package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.Tool


object ViewLabelMode : AbstractToolMode() {

    override val toolBarTools: ObservableList<Tool> = FXCollections.observableArrayList()

    override val modeActions: List<ActionSet> = listOf()

    override val allowedActions = AllowedActions.VIEW_LABELS

    private val moveToolTriggersToActiveViewer = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
        /* remove the tool triggers from old, add to new */
        modeActions.forEach { actionSet ->
            old?.viewer()?.removeActionSet(actionSet)
            new?.viewer()?.installActionSet(actionSet)
        }

        /* set the currently activeTool for this viewer */
        switchTool(activeTool ?: NavigationTool)
    }

    override fun enter() {
        activeViewerProperty.addListener(moveToolTriggersToActiveViewer)
        super.enter()
    }

    override fun exit() {
        activeViewerProperty.removeListener(moveToolTriggersToActiveViewer)
        super.exit()
    }

}


