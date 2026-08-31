package org.janelia.saalfeldlab.paintera.control.actions.navigation

import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.control.actions.state.NavigationActionState
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.SourceStateWithBackend
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState

internal class GoToSourceCoordinateState :
	NavigationActionState<SourceState<*, *>>(),
	GoToCoordinateModel {

	private val metadataState: MetadataState? = activeMetadataState()

	private val xyzView = metadataState!!.xyzView

	override val positionProperties = metadataState!!.axes.mapIndexed { axis, axisMetadata ->
		if (axis in xyzView.nonSpatialAxes)
			LongPositionProperty(axisMetadata.name, 0L)
		else
			DoublePositionProperty(axisMetadata.name, 0.0)
	}

	override val xProperty = spatialProperty(0)
	override val yProperty = spatialProperty(1)
	override val zProperty = spatialProperty(2)

	/** The property for the source axis supplying canonical [slot], or null when that dimension is absent. */
	private fun spatialProperty(slot: Int) = xyzView.xyzSourceAxes[slot]
		.takeIf { it >= 0 }
		?.let { positionProperties[it] as DoublePositionProperty }

	internal fun initializeCurrentCoordinates() {
		with(sourceState.dataSource) {
			with(viewer) {
				val sourceToGlobalTransform = AffineTransform3D().also { getSourceTransform(state.timepoint, 0, it) }
				val xyzPosition = RealPoint(3).also { displayToSourceCoordinates(width / 2.0, height / 2.0, sourceToGlobalTransform, it) }
				/* the point is in xyz view space, so read it by canonical slot and write it to the axis that supplies it */
				xyzView.xyzSourceAxes.forEachIndexed { slot, axis ->
					if (axis >= 0)
						(positionProperties[axis] as DoublePositionProperty).property.value = xyzPosition.getDoublePosition(slot)
				}
				xyzView.nonSpatialAxes.forEach { axis ->
					(positionProperties[axis] as LongPositionProperty).property.value = xyzView.slicePosition(axis)
				}
			}
		}
	}

	internal fun updateSlicePositions() {
		xyzView.nonSpatialAxes.forEach { axis ->
			xyzView.sliceAt(axis, positionProperties[axis].property.value.toLong())
		}
	}

	private fun activeMetadataState(): MetadataState? {
		val sourceState = paintera.baseView.sourceInfo().currentState().get() ?: return null
		return ((sourceState as? SourceStateWithBackend<*, *>)?.backend as? SourceStateBackendN5<*, *>)?.metadataState
	}
}