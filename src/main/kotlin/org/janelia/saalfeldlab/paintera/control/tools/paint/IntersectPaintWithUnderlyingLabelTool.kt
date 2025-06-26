package org.janelia.saalfeldlab.paintera.control.tools.paint

import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.IntersectPainting
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText


class IntersectPaintWithUnderlyingLabelTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) :
	PaintTool(activeSourceStateProperty, mode) {

	override fun newToolBarControl()  = super.newToolBarControl().also { item ->
		item.addStyleClass("intersect-tool")
	}
	override val name = "Intersect Paint with Underlying Label"
	override val keyTrigger = LabelSourceStateKeys.INTERSECT_UNDERLYING_LABEL

	private val overlay by lazy {
		IntersecttOverlay(activeViewerProperty.createNullableValueBinding { it?.viewer() })
	}

	override fun activate() {
		super.activate()
		overlay.visible = true
	}

	override fun deactivate() {
		overlay.visible = false
		super.deactivate()
	}

	override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
		mutableListOf(
			*super.actionSets.toTypedArray(),
			painteraActionSet("intersect", PaintActionType.Intersect) {
				MouseEvent.MOUSE_PRESSED(MouseButton.PRIMARY) {
					keysExclusive = false
					verifyEventNotNull()
					onAction { intersector?.intersectAt(it!!.x, it.y) }
				}
			}
		)
	}

	private val intersector: IntersectPainting?
		get() = activeViewer?.let { viewer ->
			statePaintContext?.let { ctx ->
				IntersectPainting(viewer, paintera.baseView.sourceInfo(), paintera.baseView.orthogonalViews()::requestRepaint, ctx::getMaskForLabel)
			}
		}

	private class IntersecttOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>, override val overlayText: String = "Intersect") :
		CursorOverlayWithText(viewerProperty)
}
