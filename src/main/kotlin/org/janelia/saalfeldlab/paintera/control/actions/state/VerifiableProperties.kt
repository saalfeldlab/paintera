package org.janelia.saalfeldlab.paintera.control.actions.state


import net.imglib2.Interval
import net.imglib2.Volatile
import net.imglib2.converter.Converter
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.verifiable
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments.Companion.read
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState

interface ViewerActionState {
	var viewerAndTransforms: OrthogonalViews.ViewerAndTransforms
	var viewer: ViewerPanelFX

	open class MostRecentFocus :
		PainteraActionState(),
		ViewerActionState {

		override var viewerAndTransforms by verifiable("Viewer is Active") {
			val lastFocus = paintera.baseView.mostRecentFocusHolder.value.viewer()!!
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

interface PaintContextActionState<S : ConnectomicsLabelState<D, T>, D, T> : MaskedSourceActionState<S, D, T>
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	val assignment get() = sourceState.fragmentSegmentAssignment
	val selectedIds get() = sourceState.selectedIds
	val paintSelection get() = { selectedIds.lastSelection.takeIf { Label.regular(it) } }
	val brushProperties get() = sourceState.brushProperties
	val refreshMeshes get() = sourceState::refreshMeshes

	fun getMaskForLabel(label: Long): Converter<D, BoolType> = sourceState.maskForLabel.apply(label)
	fun getBlocksForLabel(level: Int, label: Long): Array<Interval> = sourceState.labelBlockLookup.read(level, label)
	fun nextId(activate: Boolean = false): Long = sourceState.nextId(activate)

	open class FromCurrentMode<S : ConnectomicsLabelState<D, T>, D, T> :
		PainteraActionState(),
		PaintContextActionState<S, D, T>
			where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

		override var sourceState by verifiable("PaintLabelMode is active and has SourceState") {
			(paintera.currentMode as? PaintLabelMode)
				?.activeSourceStateProperty
				?.get() as? S
		}
		override var maskedSource by verifiable("SourceState has MaskedSource") { sourceState.dataSource as? MaskedSource<D, T> }
	}

	open class FromState<S : ConnectomicsLabelState<D, T>, D, T>(override var sourceState: S) : PainteraActionState(), PaintContextActionState<S, D, T>
			where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

		override var maskedSource = sourceState.dataSource as MaskedSource<D, T>
	}
}


