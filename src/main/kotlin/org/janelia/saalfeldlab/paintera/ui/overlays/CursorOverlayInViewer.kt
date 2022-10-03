package org.janelia.saalfeldlab.paintera.ui.overlays

import bdv.fx.viewer.ViewerPanelFX
import bdv.fx.viewer.render.OverlayRendererGeneric
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.event.EventType
import javafx.scene.Cursor
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.MouseEvent
import org.janelia.saalfeldlab.fx.ObservablePosition
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.paintera.paintera

abstract class CursorOverlayInViewer(protected val viewerProperty: ObservableValue<ViewerPanelFX?>) : OverlayRendererGeneric<GraphicsContext> {

    private var listeningToViewerProp = false

    protected val viewer: ViewerPanelFX? by viewerProperty.nullableVal()
    protected val isMouseInside = { viewer?.isMouseInside ?: false }

    protected var position = ObservablePosition(0.0, 0.0).apply {
        addListener {
            viewer?.display?.drawOverlays()
        }
    }

    protected var wasVisible = false

    private var previousCursor: Cursor? = null

    private var listeners = mutableListOf<Triple<ViewerPanelFX, EventType<MouseEvent>, EventHandler<MouseEvent>>>()

    var visible = false
        set(value) {
            if (value) {
                if (!field) {
                    /* we are changing to visible, store the cursor*/
                    previousCursor = paintera.baseView.node.scene.cursor
                }
                updateCursorInViewer()
            } else {
                removeListenerFromViewer()
            }
            viewer?.apply {
                setPosition(mouseXProperty.doubleValue(), mouseYProperty.doubleValue())
            }
            field = value
            viewer?.display?.drawOverlays()
        }

    var cursor: Cursor = Cursor.CROSSHAIR
        set(value) {
            field = value
            viewer?.cursor = field
        }

    private val cursorViewerChangeListener: ChangeListener<ViewerPanelFX?> = ChangeListener { _, old, new ->
        old?.cursor = previousCursor
        new?.cursor = cursor
    }

    private fun updateCursorInViewer() {
        /* set it first*/
        viewerProperty.value?.let { viewer ->
            previousCursor = viewer.cursor
            viewer.cursor = cursor
        }

        /* then listen for future changes*/
        if (!listeningToViewerProp) {
            viewerProperty.addListener(cursorViewerChangeListener)
            listeningToViewerProp = true
        }
    }

    init {

        viewerProperty.addListener { _, old, new ->
            old?.let {
                removeListenerFromViewer()
            }
            new?.apply {
                listenOnViewer()
                if (visible) {
                    display?.drawOverlays()
                }
            }
        }
        /* run manually the first time, then listen. */
        viewer?.listenOnViewer()
    }

    private fun ViewerPanelFX.listenOnViewer() {
        /* Just incase, so there are no duplicates */
        removeListenerFromViewer()

        val setPos = EventHandler<MouseEvent> { setPosition(it) }
        listeners.add(Triple(this, MouseEvent.MOUSE_MOVED, setPos))
        listeners.add(Triple(this, MouseEvent.MOUSE_DRAGGED, setPos))
        addEventFilter(MouseEvent.MOUSE_MOVED, setPos)
        addEventFilter(MouseEvent.MOUSE_DRAGGED, setPos)

        setPosition(mouseXProperty.doubleValue(), mouseYProperty.doubleValue())
        display?.addOverlayRenderer(this@CursorOverlayInViewer)
    }

    private fun removeListenerFromViewer() {

        /* reset the cursor state*/
        previousCursor?.let {
            viewerProperty.value?.cursor = previousCursor
            previousCursor = null
        }

        /* remove the event filters */
        listeners.forEach { (viewer, eventType, handler) ->
            viewer.removeEventFilter(eventType, handler)
        }

        /* remove the renderer */
        listeners.map { it.first }.toSet().forEach {
            it.apply {
                display?.removeOverlayRenderer(this@CursorOverlayInViewer)
                display?.drawOverlays()
            }
        }
        listeners.clear()
    }


    fun setPosition(event: MouseEvent) = position.set(event)

    fun setPosition(x: Double, y: Double) = position.set(x, y)

}
