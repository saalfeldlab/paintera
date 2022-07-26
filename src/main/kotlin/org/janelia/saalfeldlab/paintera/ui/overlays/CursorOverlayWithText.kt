package org.janelia.saalfeldlab.paintera.ui.overlays

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.value.ObservableValue
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

abstract class CursorOverlayWithText(viewerProperty: ObservableValue<ViewerPanelFX?>) : CursorOverlayInViewer(viewerProperty) {

    abstract val overlayText: String

    override fun drawOverlays(g: GraphicsContext) {
        if (visible && isMouseInside()) {
            position.apply {
                g.apply {
                    fill = Color.WHITE
                    font = Font.font(font.family, 15.0)
                    fillText(overlayText, x + 5, y - 5)
                }
            }
            return
        }
    }

    override fun setCanvasSize(width: Int, height: Int) {}

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}
