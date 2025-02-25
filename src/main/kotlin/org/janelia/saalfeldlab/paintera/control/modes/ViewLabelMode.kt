package org.janelia.saalfeldlab.paintera.control.modes

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.Tool


object ViewLabelMode : AbstractToolMode() {

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList()

	override val activeViewerActions: List<ActionSet> = listOf()

	override val allowedActions = AllowedActions.VIEW_LABELS
}


