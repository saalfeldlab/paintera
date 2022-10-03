package org.janelia.saalfeldlab.paintera.control.tools.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.animation.Interpolator
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.scene.Cursor
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.*
import javafx.scene.input.ScrollEvent
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl.TOGGLE_OFF
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl.TOGGLE_ON
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.painteraMidiActionSet
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.midi.FaderAction
import org.janelia.saalfeldlab.fx.midi.MidiFaderEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.PaintActions2D
import org.janelia.saalfeldlab.paintera.control.paint.PaintClickOrDragController
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import java.lang.Double.min

open class PaintBrushTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) :
    PaintTool(activeSourceStateProperty, mode) {

    override val graphic = { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "paint-brush") } }
    override val name = "Paint"
    override val keyTrigger = listOf(KeyCode.SPACE)


    private val currentLabelToPaintProperty = SimpleObjectProperty(Label.INVALID)
    internal var currentLabelToPaint: Long by currentLabelToPaintProperty.nonnull()

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

    private val paint2D by LazyForeignValue({ paintClickOrDrag }) {
        PaintActions2D(activeViewerProperty.createNullableValueBinding { it?.viewer() }).apply {
            brushRadiusProperty().bindBidirectional(brushProperties!!.brushRadiusProperty)
            brushDepthProperty().bindBidirectional(brushProperties!!.brushDepthProperty)
        }
    }

    override val actionSets by LazyForeignValue({ activeViewerAndTransforms }) {
        mutableListOf(
            *super<PaintTool>.actionSets.toTypedArray(),
            *getBrushActions(),
            *getPaintActions(),
            *(midiBrushActions() ?: arrayOf())
        )
    }

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

    /* lateinit so we can self-reference, so it removes itself after being triggered. */
    private lateinit var setCursorWhenDoneApplying: ChangeListener<Boolean>

    init {
        setCursorWhenDoneApplying = ChangeListener { observable, _, isApplying ->
            if (isApplying) {
                paint2D.setBrushCursor(Cursor.WAIT)
            } else {
                paint2D.setBrushCursor(Cursor.NONE)
                observable.removeListener(setCursorWhenDoneApplying)
            }
        }
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
            maskInterval?.let { submitPaint() }
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

    protected fun getPaintActions() = arrayOf(painteraActionSet("paint label", PaintActionType.Paint) {
        /* Handle Painting */
        MOUSE_PRESSED(MouseButton.PRIMARY) {
            name = "start selection paint"
            verifyEventNotNull()
            verify { isLabelValid }
            onAction {
                isPainting = true
                paintClickOrDrag?.startPaint(it!!)
            }
        }

        MOUSE_RELEASED(MouseButton.PRIMARY, onRelease = true) {
            name = "end selection paint"
            verify { paintClickOrDrag?.maskInterval?.let { true } ?: false }
            onAction {
                paintClickOrDrag?.busySubmitPaint()
                isPainting = false
            }
        }

        KEY_RELEASED(KeyCode.SPACE) {
            name = "end selection paint"
            verify { paintClickOrDrag?.maskInterval?.let { true } ?: false }
            onAction {
                paintClickOrDrag?.submitPaint()
                isPainting = false
            }
        }

        /* Handle Erasing */
        MOUSE_PRESSED(MouseButton.SECONDARY) {
            name = "start transparent erase"
            verifyEventNotNull()
            verify { KeyCode.SHIFT !in keyTracker!!.getActiveKeyCodes(true) }
            onAction {
                isPainting = true
                currentLabelToPaint = Label.TRANSPARENT
                paintClickOrDrag?.startPaint(it!!)
            }
        }
        /* Handle painting background */
        MOUSE_PRESSED(MouseButton.SECONDARY) {
            name = "start background erase"
            keysDown(KeyCode.SHIFT, exclusive = false)
            verifyEventNotNull()
            onAction {
                isPainting = true
                currentLabelToPaint = Label.BACKGROUND
                paintClickOrDrag?.startPaint(it!!)
            }
        }
        MOUSE_RELEASED(MouseButton.SECONDARY, onRelease = true) {
            name = "end erase"
            onAction {
                setCurrentLabelToSelection()
                paintClickOrDrag?.busySubmitPaint()
                isPainting = false
            }
        }


        /* Handle Common Mouse Move/Drag Actions*/
        MOUSE_DRAGGED {
            verify { isLabelValid }
            verifyEventNotNull()
            onAction { paintClickOrDrag?.extendPaint(it!!) }
        }
    })

    private fun PaintClickOrDragController.busySubmitPaint() {
        isApplyingMaskProperty()?.apply {
            /* remove first, to ensure we don't add a duplicates */
            removeListener(setCursorWhenDoneApplying)
            addListener(setCursorWhenDoneApplying)
            submitPaint()
        }
    }

    protected fun getBrushActions() = arrayOf(
        painteraActionSet("change brush size", PaintActionType.SetBrushSize) {
            ScrollEvent.SCROLL {
                keysExclusive = false
                name = "change brush size"

                verifyEventNotNull()
                verify { !it!!.isShiftDown }
                onAction { paint2D.changeBrushRadius(it!!.deltaY) }
            }
        },
        painteraActionSet("change brush depth", PaintActionType.SetBrushDepth) {
            ScrollEvent.SCROLL(KeyCode.SHIFT) {
                keysExclusive = false
                name = "change brush depth"
                verifyNotPainting()
                onAction { changeBrushDepth(-ControlUtils.getBiggestScroll(it)) }
            }
        }
    )

    protected fun midiBrushActions() = activeViewer?.let { viewer ->
        DeviceManager.xTouchMini?.let { device ->
            arrayOf(
                painteraMidiActionSet("change brush size with fader", device, viewer, PaintActionType.SetBrushSize) {
                    MidiFaderEvent.FADER(0) {
                        verifyEventNotNull()
                        val maxBinding = (viewer.widthProperty() to viewer.heightProperty()).let { (widthProp, heightProp) ->
                            Bindings.createIntegerBinding({ (min(widthProp.get(), heightProp.get()) / 2).toInt() }, widthProp, heightProp)
                        }
                        min = 1
                        max = maxBinding.get()
                        stepToValueConverter = {
                            max = maxBinding.get()
                            val onePixelRadiusCuttoff = (FaderAction.FADER_MAX * .33).toInt()
                            if (it <= onePixelRadiusCuttoff) {
                                it
                            } else {
                                val currentFraction = (it - min - onePixelRadiusCuttoff) / (FaderAction.FADER_MAX.toDouble() - onePixelRadiusCuttoff - min)
                                Interpolator.EASE_IN.interpolate(onePixelRadiusCuttoff, max, currentFraction)
                            }.toInt().coerceAtMost(max)
                        }
                        onAction { paint2D.setBrushRadius(it!!.value.toDouble()) }
                    }
                },
                painteraMidiActionSet("change brush depth", device, viewer, PaintActionType.SetBrushDepth) {
                    MidiToggleEvent.BUTTON_TOGGLE(7) {
                        verify { !isPainting }
                        verifyEventNotNull()
                        var setSilently = false
                        val brushDepthProperty = paint2D.brushDepthProperty()
                        val brushDepthListener = ChangeListener<Number> { _, _, depth ->
                            setSilently = true
                            control.value = if (depth.toDouble() > 1.0) TOGGLE_ON else TOGGLE_OFF
                            setSilently = false
                        }
                        afterRegisterEvent = { brushDepthProperty.addListener(brushDepthListener) }
                        afterRemoveEvent = { brushDepthProperty.removeListener(brushDepthListener) }
                        onAction {
                            if (isPainting) {
                                control.value = TOGGLE_OFF
                            } else if (!setSilently) {
                                val curDepth = brushDepthProperty.get()
                                if (it!!.isOn && curDepth < 2.0) {
                                    brushProperties!!.brushDepth = 2.0
                                } else if (curDepth > 1.0) {
                                    brushProperties!!.brushDepth = 1.0
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}
