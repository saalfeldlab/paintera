package org.janelia.saalfeldlab.paintera.control.actions.navigation

import bdv.viewer.Source
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleLongProperty
import javafx.event.Event
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.paintera.control.actions.ActionState
import org.janelia.saalfeldlab.paintera.control.actions.verify
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.control.navigation.TranslationController
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

internal class GoToLabelState : ActionState<GoToLabelState>, GoToLabelUIState {

	internal lateinit var sourceState: ConnectomicsLabelState<*, *>
	internal lateinit var source: Source<out IntegerType<*>>
	internal lateinit var viewer: ViewerPanelFX
	internal lateinit var translationController: TranslationController

	override val labelProperty = SimpleLongProperty()
	override val activateLabelProperty = SimpleBooleanProperty(true)


	override fun <E : Event> Action<E>.verifyState() {
		verify(::source, "Source is Active") { paintera.baseView.sourceInfo().currentSourceProperty().value as? Source<out IntegerType<*>> }
		verify(::sourceState, "Label Source is Active") { paintera.baseView.sourceInfo().getState(source) as? ConnectomicsLabelState<*, *> }
		verify(::viewer, "Viewer Detected") { paintera.baseView.lastFocusHolder.value?.viewer() }
		verify(::translationController, "Active Viewer Detected") { NavigationTool.translationController }

		verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
	}

	override fun copyVerified() = GoToLabelState().also {
		it.source = source
		it.sourceState = sourceState
		it.viewer = viewer
		it.translationController = translationController
	}

	internal fun initializeWithCurrentLabel() {
		labelProperty.value = sourceState.selectedIds.lastSelection
	}
}