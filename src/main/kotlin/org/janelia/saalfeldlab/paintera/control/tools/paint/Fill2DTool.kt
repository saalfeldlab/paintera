package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.Cursor
import javafx.scene.input.*
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.ui.StyleableImageView
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

open class Fill2DTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) : PaintTool(activeSourceStateProperty, mode) {

    override val graphic = { StyleableImageView().also { it.styleClass += listOf("toolbar-tool", "fill-2d") } }

    override val name = "Fill 2D"
    override val keyTrigger = listOf(KeyCode.F)
    var fillLabel: () -> Long = { statePaintContext?.paintSelection?.invoke() ?: Label.INVALID }

    val fill2D by LazyForeignValue({ statePaintContext }) {
        with(it!!) {
            val floodFill2D = FloodFill2D(
                activeViewerProperty.createNullableValueBinding { vat -> vat?.viewer() },
                dataSource,
                assignment
            ) { MeshSettings.Defaults.Values.isVisible }
            floodFill2D.fillDepthProperty().bindBidirectional(brushProperties.brushDepthProperty)
            floodFill2D
        }
    }

    private var fillTask: UtilityTask<*>? = null

    private val overlay by lazy {
        Fill2DOverlay(activeViewerProperty.createNullableValueBinding { it?.viewer() }).apply {
            brushDepthProperty.bindBidirectional(brushProperties!!.brushDepthProperty)
        }
    }
    private val filterKeyHeldDown = EventHandler<KeyEvent> {
        if (paintera.keyTracker.areOnlyTheseKeysDown(KeyCode.F)) {
            it.consume()
        }
    }


    override fun activate() {
        super.activate()
        activeViewer?.apply { overlay.setPosition(mouseXProperty.get(), mouseYProperty.get()) }
        activeViewerProperty.get()?.viewer()?.scene?.addEventFilter(KeyEvent.KEY_PRESSED, filterKeyHeldDown)
        overlay.visible = true
    }

    override fun deactivate() {
        overlay.visible = false
        activeViewerProperty.get()?.viewer()?.scene?.removeEventFilter(KeyEvent.KEY_PRESSED, filterKeyHeldDown)
        super.deactivate()
    }

    override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
        mutableListOf(
            *super<PaintTool>.actionSets.toTypedArray(),
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
                    onAction {
                        val disableUntilDone = SimpleBooleanProperty(true)
                        paintera.baseView.disabledPropertyBindings[this] = disableUntilDone
                        overlay.cursor = Cursor.WAIT
                        val setFalseAndRemove = {
                            paintera.baseView.disabledPropertyBindings -= this
                            disableUntilDone.set(false)
                            overlay.cursor = Cursor.CROSSHAIR
                            if (!paintera.keyTracker.areKeysDown(*keyTrigger.toTypedArray())) {
                                mode?.switchTool(mode.defaultTool)
                            }
                        }
                        fillTask = fill2D.fillAt(it!!.x, it.y, fillLabel())
                        fillTask?.let {task ->
                            task.onEnd { setFalseAndRemove() }
                        } ?: setFalseAndRemove()
                    }
                }
            },
            painteraActionSet(LabelSourceStateKeys.CANCEL_2D_FLOODFILL, ignoreDisable = true) {
                KeyEvent.KEY_PRESSED(LabelSourceStateKeys.namedCombinationsCopy(), LabelSourceStateKeys.CANCEL_2D_FLOODFILL) {
                    graphic = { FontAwesomeIconView().apply { styleClass += listOf("toolbar-tool", "reject") } }
                    filter = true
                    onAction {
                        fillTask?.cancel()
                        mode?.switchTool(mode.defaultTool)
                    }
                }
            }
        )
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
