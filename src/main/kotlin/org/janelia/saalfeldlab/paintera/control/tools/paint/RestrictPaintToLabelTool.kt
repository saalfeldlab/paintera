package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.PainteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.paint.RestrictPainting
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText


class RestrictPaintToLabelTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>) : PaintTool(activeSourceStateProperty) {

    private val overlay by LazyForeignValue({ activeViewer }) {
        it?.let {
            RestrictOverlay(it)
        }
    }

    override fun activate() {
        super.activate()
        activeViewer?.apply { overlay?.setPosition(mouseXProperty.get(), mouseYProperty.get()) }
        overlay?.visible = true
    }

    override fun deactivate() {
        overlay?.visible = false
        super.deactivate()
    }

    override val actionSets: List<ActionSet> = listOf(
        PainteraActionSet("restrict", PaintActionType.Restrict) {
            mouseAction(MouseEvent.MOUSE_PRESSED) {
                keysDown(KeyCode.R, KeyCode.SHIFT)
                verify { it.isPrimaryButtonDown }
                onAction { restrictor?.restrictTo(it.x, it.y) }
            }
        }
    )

    private val restrictor: RestrictPainting?
        get() = activeViewer?.let { viewer ->
            statePaintContext?.let { ctx ->
                RestrictPainting(viewer, paintera.baseView.sourceInfo(), paintera.baseView.orthogonalViews()::requestRepaint, ctx::getMaskForLabel)
            }
        }

    private class RestrictOverlay(viewer: ViewerPanelFX, override val overlayText: String = "Restrict") : CursorOverlayWithText(viewer)
}
