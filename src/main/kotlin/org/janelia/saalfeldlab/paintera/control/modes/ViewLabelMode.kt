package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.input.KeyEvent.KEY_PRESSED
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.GO_TO_LABEL
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.navigation.GoToLabel
import org.janelia.saalfeldlab.paintera.control.tools.Tool


open class ViewLabelMode : AbstractToolMode() {

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList()

	override val modeActions: List<ActionSet> by lazy {
		listOf(
			goToLabelAction
		)
	}

	override val allowedActions = AllowedActions.VIEW_LABELS

	protected val moveModeActionsToActiveViewer = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
		/* remove the tool triggers from old, add to new */
		modeActions.forEach { actionSet ->
			old?.viewer()?.removeActionSet(actionSet)
			new?.viewer()?.installActionSet(actionSet)
		}
	}

	override fun enter() {
		activeViewerProperty.addListener(moveModeActionsToActiveViewer)
		super.enter()
	}

	override fun exit() {
		activeViewerProperty.removeListener(moveModeActionsToActiveViewer)
		super.exit()
	}

	val goToLabelAction = painteraActionSet(GO_TO_LABEL, NavigationActionType.Pan) {
		KEY_PRESSED(GO_TO_LABEL) {
			onAction { GoToLabel(it) }
		}
	}
}


