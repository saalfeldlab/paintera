package org.janelia.saalfeldlab.paintera.ui.overlays

import bdv.fx.viewer.ViewerPanelFX
import javafx.scene.Cursor
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

abstract class CursorOverlayWithText(viewer: ViewerPanelFX) : CursorOverlayInViewer(viewer) {

    abstract val overlayText: String

    override fun drawOverlays(g: GraphicsContext) {
        if (visible && viewer.isMouseInside) {
            position.apply {
                viewer.scene.cursor = getCursor()
                g.apply {
                    fill = Color.WHITE
                    font = Font.font(font.family, 15.0)
                    fillText(overlayText, x + 5, y - 5)
                }
            }
            return
        }
        if (wasVisible) {
            viewer.scene.cursor = Cursor.DEFAULT
            wasVisible = false
        }
    }

    override fun setCanvasSize(width: Int, height: Int) {}

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}
