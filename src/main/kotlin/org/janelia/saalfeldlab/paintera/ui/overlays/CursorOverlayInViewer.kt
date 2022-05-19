package org.janelia.saalfeldlab.paintera.ui.overlays

import bdv.fx.viewer.ViewerPanelFX
import bdv.fx.viewer.render.OverlayRendererGeneric
import javafx.scene.Cursor
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.MouseEvent
import org.janelia.saalfeldlab.fx.ObservablePosition

abstract class CursorOverlayInViewer(val viewer: ViewerPanelFX) : OverlayRendererGeneric<GraphicsContext> {

    protected var position = ObservablePosition(0.0, 0.0).apply {
        addListener { viewer.display.drawOverlays() }
    }

    protected var wasVisible = false

    var visible = false
        set(value) {
            if (value != field) {
                if (field) {
                    wasVisible = true
                    viewer.removeEventFilter(MouseEvent.MOUSE_MOVED, ::setPosition)
                    viewer.removeEventFilter(MouseEvent.MOUSE_DRAGGED, ::setPosition)
                } else {
                    viewer.addEventFilter(MouseEvent.MOUSE_MOVED, ::setPosition)
                    viewer.addEventFilter(MouseEvent.MOUSE_DRAGGED, ::setPosition)
                }
                field = value
                viewer.display.drawOverlays()
            }
        }

    init {
        viewer.display.addOverlayRenderer(this)
    }

    open fun getCursor(): Cursor = Cursor.CROSSHAIR

    fun setPosition(event: MouseEvent) = position.set(event)

    fun setPosition(x: Double, y: Double) = position.set(x, y)

}
