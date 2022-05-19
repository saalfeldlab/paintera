package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.PainteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText

class Fill2DTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>) : PaintTool(activeSourceStateProperty) {
    val fill2D by LazyForeignValue({ activeViewer to statePaintContext }) {
        with(it.second!!) {
            val floodFill2D = FloodFill2D(
                activeViewer,
                dataSource,
                assignment
            ) { MeshSettings.Defaults.Values.isVisible }
            floodFill2D.fillDepthProperty().bindBidirectional(brushProperties.brushDepthProperty)
            floodFill2D
        }
    }

    private val overlay by LazyForeignValue({ activeViewer }) {
        it?.let {
            Fill2DOverlay(it).apply {
                brushDepthProperty.bindBidirectional(brushProperties!!.brushDepthProperty)
            }
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
        PainteraActionSet("change brush depth", PaintActionType.SetBrushDepth) {
            action(ScrollEvent.SCROLL) {
                keysDown(KeyCode.F)
                onAction { changeBrushDepth(-ControlUtils.getBiggestScroll(it)) }
            }
        },
        PainteraActionSet("fill 2d", PaintActionType.Fill) {
            mouseAction(MouseEvent.MOUSE_PRESSED) {
                keysDown(KeyCode.F)
                verify { it.isPrimaryButtonDown }
                onAction { fill2D.fillAt(it.x, it.y, statePaintContext?.paintSelection) }
            }
        }
    )

    private class Fill2DOverlay(viewer: ViewerPanelFX) : CursorOverlayWithText(viewer) {

        val brushDepthProperty = SimpleDoubleProperty()
        private val brushDepth by brushDepthProperty.nonnullVal()

        override val overlayText: String
            get() = when {
                brushDepth > 1 -> "$OVERLAY_TEXT depth= $brushDepth"
                else -> OVERLAY_TEXT
            }

        companion object {
            private const val OVERLAY_TEXT = "Fill 2D"
        }
    }
}
