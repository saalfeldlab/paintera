package org.janelia.saalfeldlab.paintera.control.actions.navigation

import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.control.actions.state.NavigationActionState
import org.janelia.saalfeldlab.paintera.state.SourceState

internal class GoToSourceCoordinateState :
	NavigationActionState<SourceState<*, *>>(),
	GoToCoordinateUI.Model by GoToCoordinateUI.Default(){

	internal fun initializeWithCurrentCoordinates() {
		with(sourceState.dataSource) {
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