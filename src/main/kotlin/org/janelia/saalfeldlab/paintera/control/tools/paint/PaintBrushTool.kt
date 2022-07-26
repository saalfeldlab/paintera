package org.janelia.saalfeldlab.paintera.control.tools.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.Observable
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.*
import javafx.scene.input.ScrollEvent
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.PainteraActionSet
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.PaintActions2D
import org.janelia.saalfeldlab.paintera.control.paint.PaintClickOrDragController
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState

open class PaintBrushTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) :
    PaintTool(activeSourceStateProperty, mode) {

    override val graphic = { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "paint-brush") } }
    override val name = "Paint"
    override val keyTrigger = listOf(KeyCode.SPACE)


    private val currentLabelToPaintProperty = SimpleObjectProperty(Label.INVALID)
    private var currentLabelToPaint by currentLabelToPaintProperty.nonnull()

    private val isLabelValidProperty = currentLabelToPaintProperty.createNullableValueBinding { it != Label.INVALID }.apply {
        addListener { _, _, _ ->
            paint2D.setOverlayValidState()
        }
    }
    private val isLabelValid by isLabelValidProperty.nonnullVal()

    val paintClickOrDrag by LazyForeignValue({ activeViewer to statePaintContext }) {
        it.first?.let { viewer ->
            it.second?.let {
                PaintClickOrDragController(paintera.baseView, viewer, this::currentLabelToPaint, brushProperties!!::brushRadius, brushProperties!!::brushDepth)
            }
        }
    }

    private val filterSpaceHeldDown = EventHandler<KeyEvent> {
        if (paintera.keyTracker.areOnlyTheseKeysDown(KeyCode.SPACE)) {
            it.consume()
        }
    }

    private val paint2D by lazy {
        PaintActions2D(activeViewerProperty.createNullableValueBinding { it?.viewer() }).apply {
            brushRadiusProperty().bindBidirectional(brushProperties!!.brushRadiusProperty)
            brushDepthProperty().bindBidirectional(brushProperties!!.brushDepthProperty)
        }
    }

    override val actionSets: MutableList<ActionSet> = mutableListOf(
        *getBrushActions(),
        *getPaintActions(),
    )

    override val statusProperty = SimpleStringProperty().apply {
        val labelNumToString: (Long) -> String = {
            when (it) {
                Label.BACKGROUND -> "BACKGROUND"
                Label.TRANSPARENT -> "TRANSPARENT"
                Label.INVALID -> "INVALID"
                Label.OUTSIDE -> "OUTSIDE"
                Label.MAX_ID -> "MAX_ID"
                else -> "$it"
            }
        }
        bind(currentLabelToPaintProperty.createNonNullValueBinding { "Painting Label: ${labelNumToString(it)}" })
    }

    private val selectedIdListener: (obs: Observable) -> Unit = {
        statePaintContext?.selectedIds?.lastSelection?.let { currentLabelToPaint = it }
    }

    override fun activate() {
        super.activate()
        setCurrentLabelToSelection()
        statePaintContext?.selectedIds?.apply { addListener(selectedIdListener) }
        activeViewerProperty.get()?.viewer()?.scene?.addEventFilter(KEY_PRESSED, filterSpaceHeldDown)
        paint2D.apply {
            activeViewer?.apply {
                setOverlayValidState()
                setBrushOverlayVisible(true)
            }
        }
    }

    override fun deactivate() {
        paintClickOrDrag?.apply {
            viewerInterval?.let { submitPaint() }
        }
        paint2D.setBrushOverlayVisible(false)
        activeViewerProperty.get()?.viewer()?.scene?.removeEventFilter(KEY_PRESSED, filterSpaceHeldDown)
        currentLabelToPaint = Label.INVALID
        super.deactivate()
    }

    private fun PaintActions2D.setOverlayValidState() {
        setBrushOverlayValid(isLabelValid, if (isLabelValid) null else "No Id Selected")
    }

    private fun setCurrentLabelToSelection() {
        currentLabelToPaint = statePaintContext?.paintSelection?.invoke() ?: Label.INVALID
    }

    private fun getPaintActions() = arrayOf(PainteraActionSet("paint label", PaintActionType.Paint) {
        /* Handle Painting */
        MOUSE_PRESSED(MouseButton.PRIMARY) {
            name = "start selection paint"
            verifyEventNotNull()
            verify { isLabelValid }
            onAction { paintClickOrDrag?.startPaint(it!!) }
        }

        MOUSE_RELEASED(MouseButton.PRIMARY, onRelease = true) {
            name = "end selection paint"
            verify { paintClickOrDrag?.viewerInterval?.let { true } ?: false }
            onAction { paintClickOrDrag?.submitPaint() }
        }

        KEY_RELEASED(KeyCode.SPACE) {
            name = "end selection paint"
            verify { paintClickOrDrag?.viewerInterval?.let { true } ?: false }
            onAction { paintClickOrDrag?.submitPaint() }
        }

        /* Handle Erasing */
        MOUSE_PRESSED(MouseButton.SECONDARY) {
            name = "start transparent erase"
            verifyEventNotNull()
            verify { KeyCode.SHIFT !in keyTracker!!.getActiveKeyCodes(true) }
            onAction {
                currentLabelToPaint = Label.TRANSPARENT
                paintClickOrDrag?.apply { startPaint(it!!) }
            }
        }
        /* Handle painting background */
        MOUSE_PRESSED(MouseButton.SECONDARY) {
            name = "start background erase"
            keysDown(KeyCode.SHIFT, exclusive = false)
            verifyEventNotNull()
            onAction {
                currentLabelToPaint = Label.BACKGROUND
                paintClickOrDrag?.apply { startPaint(it!!) }
            }
        }
        MOUSE_RELEASED(MouseButton.SECONDARY, onRelease = true) {
            name = "end erase"
            onAction {
                setCurrentLabelToSelection()
                paintClickOrDrag?.submitPaint()
            }
        }


        /* Handle Common Mouse Move/Drag Actions*/
        MOUSE_DRAGGED {
            verify { isLabelValid }
            verifyEventNotNull()
            onAction { paintClickOrDrag?.extendPaint(it!!) }
        }
    })

    private fun getBrushActions() = arrayOf(PainteraActionSet("change brush size", PaintActionType.SetBrushSize) {
        ScrollEvent.SCROLL {
            keysExclusive = false
            name = "change brush size"

            verifyEventNotNull()
            verify { !it!!.isShiftDown }
            onAction { paint2D.changeBrushRadius(it!!.deltaY) }
        }
    }, PainteraActionSet("change brush depth", PaintActionType.SetBrushDepth) {
        ScrollEvent.SCROLL(KeyCode.SHIFT) {
            keysExclusive = false
            name = "change brush depth"
            onAction { changeBrushDepth(-ControlUtils.getBiggestScroll(it)) }
        }
    })
}
