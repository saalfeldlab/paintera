package org.janelia.saalfeldlab.paintera.control.actions.navigation

import org.janelia.saalfeldlab.paintera.control.actions.state.NavigationActionState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

class GoToLabelState() :
	NavigationActionState<ConnectomicsLabelState<*,*>>(),
	GoToLabelUI.Model by GoToLabelUI.Default() {

	fun initializeWithCurrentLabel() {
		labelProperty.value = sourceState.selectedIds.lastSelection
	}
}

