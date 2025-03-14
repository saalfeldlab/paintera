package org.janelia.saalfeldlab.paintera.control.actions.navigation

import bdv.viewer.Source
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.Event
import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.paintera.control.actions.ActionState
import org.janelia.saalfeldlab.paintera.control.actions.verify
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.control.navigation.TranslationController
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState

internal class GoToCoordinateState : ActionState<GoToCoordinateState>, GoToCoordinateUIState {

	internal lateinit var sourceState: SourceState<*, *>
	internal lateinit var source: Source<*>
	internal lateinit var viewer: ViewerPanelFX
	internal lateinit var translationController: TranslationController

	override val xProperty = SimpleDoubleProperty()
	override val yProperty = SimpleDoubleProperty()
	override val zProperty = SimpleDoubleProperty()

	override fun <E : Event> Action<E>.verifyState() {
		verify(::source, "Source is Active") { paintera.baseView.sourceInfo().currentSourceProperty().value }
		verify(::sourceState, "Source State is Active") { paintera.baseView.sourceInfo().getState(source) }
		verify(::viewer, "Viewer Detected") { paintera.baseView.lastFocusHolder.value?.viewer() }
		verify(::translationController, "Active Viewer Detected") { NavigationTool.translationController }

		verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
	}

	override fun copyVerified() = GoToCoordinateState().also {
		it.source = source
		it.sourceState = sourceState
		it.viewer = viewer
		it.translationController = translationController
	}

	internal fun initializeWithCurrentCoordinates() {
		with(source) {
			with(viewer) {
				val sourceToGlobalTransform = AffineTransform3D().also { getSourceTransform(state.timepoint, 0, it) }
				val currentSourceCoordinate = RealPoint(3).also { displayToSourceCoordinates(width / 2.0, height / 2.0, sourceToGlobalTransform, it) }
				xProperty.value = currentSourceCoordinate.getDoublePosition(0)
				yProperty.value = currentSourceCoordinate.getDoublePosition(1)
				zProperty.value = currentSourceCoordinate.getDoublePosition(2)
			}
		}
	}
}