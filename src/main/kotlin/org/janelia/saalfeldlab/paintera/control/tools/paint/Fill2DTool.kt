package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.Cursor
import javafx.scene.input.*
import net.imglib2.Interval
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.ui.StyleableImageView
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText

open class Fill2DTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) :
    PaintTool(activeSourceStateProperty, mode) {

    override val graphic = { StyleableImageView().also { it.styleClass += listOf("toolbar-tool", "fill-2d") } }

    override val name = "Fill 2D"
    override val keyTrigger = listOf(KeyCode.F)
    var fillLabel: () -> Long = { statePaintContext?.paintSelection?.invoke() ?: Label.INVALID }

    val fill2D by LazyForeignValue({ statePaintContext }) {
        with(it!!) {
            val floodFill2D = FloodFill2D(
                activeViewerProperty.createNullableValueBinding { vat -> vat?.viewer() },
                dataSource,
            ) { MeshSettings.Defaults.Values.isVisible }
            floodFill2D.fillDepthProperty().bindBidirectional(brushProperties.brushDepthProperty)
            floodFill2D
        }
    }

    private var fillTask: UtilityTask<*>? = null

    private val overlay by lazy {
        Fill2DOverlay(activeViewerProperty.createNullableValueBinding { it?.viewer() }).apply {
            brushPropertiesBinding.addListener { _, old, new ->
                old?.brushDepthProperty?.let { brushDepthProperty.unbindBidirectional(it) }
                new?.brushDepthProperty?.let { brushDepthProperty.bindBidirectional(it) }
            }
            brushDepthProperty.bindBidirectional(brushProperties!!.brushDepthProperty)
        }
    }


    override fun activate() {
        super.activate()
        activeViewer?.apply { overlay.setPosition(mouseXProperty.get(), mouseYProperty.get()) }
        overlay.visible = true
    }


    override fun deactivate() {
        if (fillIsRunning) return

        overlay.visible = false
        super.deactivate()
    }

    override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
        mutableListOf(
            *super.actionSets.toTypedArray(),
            painteraActionSet("change brush depth", PaintActionType.SetBrushDepth) {
                ScrollEvent.SCROLL {
                    keysExclusive = false
                    onAction {
                        changeBrushDepth(-ControlUtils.getBiggestScroll(it))
                        overlay.brushDepthProperty
                    }
                }
            },
            painteraActionSet("fill 2d", PaintActionType.Fill) {
                MouseEvent.MOUSE_PRESSED(MouseButton.PRIMARY) {
                    name = "fill 2d"
                    keysExclusive = false
                    verifyEventNotNull()
                    onAction { executeFill2DAction(it!!.x, it.y) }
                }
            },
            painteraActionSet(LabelSourceStateKeys.CANCEL, ignoreDisable = true) {
                KeyEvent.KEY_PRESSED(LabelSourceStateKeys.namedCombinationsCopy(), LabelSourceStateKeys.CANCEL) {
                    graphic = { FontAwesomeIconView().apply { styleClass += listOf("toolbar-tool", "reject", "ignore-disable") } }
                    filter = true
                    onAction {
                        fillTask?.run { if (!isCancelled) cancel() }
                        fillIsRunningProperty.set(false)
                        mode?.switchTool(mode.defaultTool)
                    }
                }
            }
        )
    }

    private val fillIsRunningProperty = SimpleBooleanProperty(false, "Fill2D is Running")
    private var fillIsRunning by fillIsRunningProperty.nonnull()

    internal fun executeFill2DAction(x: Double, y: Double, afterFill: (Interval) -> Unit = {}): UtilityTask<Interval> {
        lateinit var setFalseAndRemoveListener: ChangeListener<Boolean>
        setFalseAndRemoveListener = ChangeListener { obs, _, isBusy ->
            if (isBusy) {
                overlay.cursor = Cursor.WAIT
            } else {
                overlay.cursor = Cursor.CROSSHAIR
                if (!paintera.keyTracker.areKeysDown(*keyTrigger.toTypedArray()) && !enteredWithoutKeyTrigger) {
                    InvokeOnJavaFXApplicationThread { mode?.switchTool(mode.defaultTool) }
                }
                obs.removeListener(setFalseAndRemoveListener)
            }
        }

        fillIsRunningProperty.set(true)
        return fill2D.fillViewerAt(x, y, fillLabel(), statePaintContext!!.assignment).also { task ->
            fillTask = task

            paintera.baseView.isDisabledProperty.addListener(setFalseAndRemoveListener)
            paintera.baseView.disabledPropertyBindings[this] = fillIsRunningProperty

            if (task.isDone) {
                /* If it's already done, do this now*/
                if (!task.isCancelled) afterFill(task.get())
                fillIsRunningProperty.set(false)
                paintera.baseView.disabledPropertyBindings -= this
            } else {
                /* Otherwise, do it when it's done */
                task.onEnd {
                    fillIsRunningProperty.set(false)
                    paintera.baseView.disabledPropertyBindings -= this
                }
                task.onSuccess { _, _ -> afterFill(task.get()) }
            }
        }
    }

    private class Fill2DOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>) : CursorOverlayWithText(viewerProperty) {

        val brushDepthProperty = SimpleDoubleProperty().apply { addListener { _, _, _ -> viewer?.display?.drawOverlays() } }
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
