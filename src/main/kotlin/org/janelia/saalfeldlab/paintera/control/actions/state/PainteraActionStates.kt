package org.janelia.saalfeldlab.paintera.control.actions.state

import javafx.event.Event
import javafx.util.Duration
import net.imglib2.RealPoint
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.VerifiablePropertyActionState
import org.janelia.saalfeldlab.fx.actions.verifiable
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

open class PainteraActionState(vararg delegates : Any, var invalidIfDisabled : Boolean = true) : VerifiablePropertyActionState(*delegates) {

	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)

		if (this@PainteraActionState.invalidIfDisabled)
			action.verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
	}
}


open class ViewerAndSourceActionState<S : SourceState<*,*>>(
	viewerActionState : ViewerActionState = ViewerActionState.LastFocused(),
	sourceStateActionState : SourceStateActionState<S> = SourceStateActionState.ActiveSource(),
	vararg additionalDelegates : Any
) :
	PainteraActionState(viewerActionState, sourceStateActionState, *additionalDelegates),
	ViewerActionState by viewerActionState,
	SourceStateActionState<S> by sourceStateActionState

open class PaintableSourceActionState<S : ConnectomicsLabelState<D, T>, D, T>(
	paintContextActionState: PaintContextActionState<S, D, T> = PaintContextActionState.FromCurrentMode(),
	vararg additionalDelegates : Any
) :
	PainteraActionState(paintContextActionState, *additionalDelegates),
	PaintContextActionState<S, D, T> by paintContextActionState
where D : IntegerType<D>, T : RealType<T>, T : Volatile<D>

open class ViewerAndPaintableSourceActionState<S : ConnectomicsLabelState<D, T>, D, T>(
	viewerActionState : ViewerActionState = ViewerActionState.LastFocused(),
	paintContextActionState: PaintContextActionState<S, D, T> = PaintContextActionState.FromCurrentMode(),
	vararg additionalDelegates : Any
) :
	PaintableSourceActionState<S, D, T>(paintContextActionState, viewerActionState, *additionalDelegates),
	ViewerActionState by viewerActionState
where D : IntegerType<D>, T : RealType<T>, T : Volatile<D>

open class NavigationActionState<S : SourceState<*, *>>(
	viewerActionState : ViewerActionState = ViewerActionState.LastFocused(),
	sourceState : SourceStateActionState<S> = SourceStateActionState.ActiveSource(),
	vararg additionalDelegates : Any
) : ViewerAndSourceActionState<S>(viewerActionState, sourceState, *additionalDelegates) {

	var translationController by verifiable("Translation Controller for Active Viewer") { NavigationTool.translationController }
	var rotationController by verifiable("Rotation Controller for Active Viewer") { NavigationTool.rotationController }
	var zoomController by verifiable("Zoom Controller for Active Viewer") { NavigationTool.zoomController }

	fun translateToCoordinate( x: Double, y: Double, z: Double, ) {
		val source = sourceState.dataSource
		val sourceToGlobalTransform = AffineTransform3D().also { source.getSourceTransform(viewer.state.timepoint, 0, it) }
		val currentSourceCoordinate = RealPoint(3).also {
			viewer.displayToSourceCoordinates(viewer.width / 2.0, viewer.height / 2.0, sourceToGlobalTransform, it)
		}

		val sourceDeltaX = x - currentSourceCoordinate.getDoublePosition(0)
		val sourceDeltaY = y - currentSourceCoordinate.getDoublePosition(1)
		val sourceDeltaZ = z - currentSourceCoordinate.getDoublePosition(2)

		val viewerCenterInSource = RealPoint(3)
		viewer.displayToSourceCoordinates(viewer.width / 2.0, viewer.height / 2.0, sourceToGlobalTransform, viewerCenterInSource)

		val newViewerCenter = RealPoint(3)
		viewer.sourceToDisplayCoordinates(
			viewerCenterInSource.getDoublePosition(0) + sourceDeltaX,
			viewerCenterInSource.getDoublePosition(1) + sourceDeltaY,
			viewerCenterInSource.getDoublePosition(2) + sourceDeltaZ,
			sourceToGlobalTransform,
			newViewerCenter
		)

		val deltaX = viewer.width / 2.0 - newViewerCenter.getDoublePosition(0)
		val deltaY = viewer.height / 2.0 - newViewerCenter.getDoublePosition(1)
		val deltaZ = 0 - newViewerCenter.getDoublePosition(2)

		translationController.translate(deltaX, deltaY, deltaZ, Duration(300.0))
	}
}