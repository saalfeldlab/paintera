package org.janelia.saalfeldlab.paintera.control.actions.state


import net.imglib2.Volatile
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.verifiable
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

interface ViewerActionState {
	var viewerAndTransforms: OrthogonalViews.ViewerAndTransforms
	var viewer: ViewerPanelFX

	open class LastFocused :
		PainteraActionState(),
		ViewerActionState {

		override var viewerAndTransforms by verifiable("Viewer is Active") {
			val lastFocus = paintera.baseView.lastFocusHolder.value?.viewer()
			paintera.baseView.orthogonalViews().run {
				when (lastFocus) {
					topLeft.viewer() -> topLeft
					topRight.viewer() -> topRight
					bottomLeft.viewer() -> bottomLeft
					else -> null
				}
			}
		}
		override var viewer by verifiable("Viewer is Active") { viewerAndTransforms.viewer() }
	}
}

interface SourceStateActionState<S : SourceState<*, *>> {
	var sourceState: S

	open class ActiveSource<S : SourceState<*, *>> :
		PainteraActionState(),
		SourceStateActionState<S> {
		override var sourceState by verifiable("Source is Active") {
			val currentSource = paintera.baseView.sourceInfo().currentSourceProperty().value
			paintera.baseView.sourceInfo().getState(currentSource) as? S
		}
	}
}

interface MaskedSourceActionState<S : ConnectomicsLabelState<D, T>, D, T> : SourceStateActionState<S>
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {
	var maskedSource: MaskedSource<D, T>

	open class ActiveSource<S : ConnectomicsLabelState<D, T>, D, T> :
		SourceStateActionState.ActiveSource<S>(),
		MaskedSourceActionState<S, D, T>
			where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {
		override var maskedSource by verifiable("Active Source has Masked Source") {
			sourceState.dataSource as? MaskedSource<D, T>
		}
	}
}

interface PaintableActionState<S : ConnectomicsLabelState<D, T>, D, T> : MaskedSourceActionState<S, D, T>
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

}

interface PaintContextActionState {
	var paintContext: StatePaintContext<*, *>

	open class FromCurrentMode :
		PainteraActionState(),
		PaintContextActionState {
		override var paintContext: StatePaintContext<*, *> by verifiable("PaintLabelMode is active and has StatePaintContext") {
			(paintera.currentMode as? PaintLabelMode)?.statePaintContext as? StatePaintContext<*, *>
		}
	}
}


