package org.janelia.saalfeldlab.paintera.control.actions.navigation

import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
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

	override val positionProperties = metadataState!!.axes.map { axis ->
		if (axis.type == Axis.SPACE)
			DoublePositionProperty(axis.name, 0.0)
		else
			LongPositionProperty(axis.name, 0L)
	}.toList()

	override val xProperty = getAxesByName("x") as? DoublePositionProperty
	override val yProperty = getAxesByName("y") as? DoublePositionProperty
	override val zProperty = getAxesByName("z") as? DoublePositionProperty

	private fun getAxesByName(name: String): PositionProperty<*>? {
		metadataState ?: return null

		val idx = metadataState.axes
			.indexOfFirst { it.name.equals(name, ignoreCase = true) }
			.takeIf { it >= 0 }
			?: return null

		return positionProperties[idx]
	}

	internal fun initializeCurrentCoordinates() {
		with(sourceState.dataSource) {
			with(viewer) {
				metadataState!!.apply {
					val sourceToGlobalTransform = AffineTransform3D().also { getSourceTransform(state.timepoint, 0, it) }
					val currentSourceCoordinate = RealPoint(3).also { displayToSourceCoordinates(width / 2.0, height / 2.0, sourceToGlobalTransform, it) }
					val spatialIndices = axes.mapIndexed { index, axis -> index.takeIf { axis.type == Axis.SPACE } }.filterNotNull().toList()
					spatialIndices.forEach { index ->
						val spatialProperty = (positionProperties[index] as DoublePositionProperty).property
						spatialProperty.value = currentSourceCoordinate.getDoublePosition(index)
					}
					val nonSpatialIndices = axes.mapIndexed { index, axis -> index.takeIf { axis.type != Axis.SPACE } }.filterNotNull().toList()
					nonSpatialIndices.forEach { index ->
						val nonSpatialProperty = (positionProperties[index] as LongPositionProperty).property
						nonSpatialProperty.value = slicePositions[index]
					}
				}
			}
		}
	}

	internal fun updateSlicePositions() {
		val nonSpatialIndices = metadataState!!.axes.mapIndexed { index, axis -> index.takeIf { axis.type != Axis.SPACE } }.filterNotNull().toList()
		nonSpatialIndices.forEach { idx ->
			metadataState.slicePositions[idx] = positionProperties[idx].property.value.toLong()
		}
		paintera.baseView.orthogonalViews().requestRepaint()
	}

	private fun activeMetadataState(): MetadataState? {
		val sourceState = paintera.baseView.sourceInfo().currentState().get() ?: return null
		return ((sourceState as? SourceStateWithBackend<*, *>)?.backend as? SourceStateBackendN5<*, *>)?.metadataState
	}
}