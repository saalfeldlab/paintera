package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Cursor
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.StyleableImageView
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.FloodFillState
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText

class Fill3DTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) : PaintTool(activeSourceStateProperty, mode) {

    override val graphic = { StyleableImageView().also { it.styleClass += listOf("toolbar-tool", "fill-3d") } }

    override val name = "Fill 3D"
    override val keyTrigger = listOf(KeyCode.F, KeyCode.SHIFT)


    private val floodFillStateProperty = SimpleObjectProperty<FloodFillState?>()
    private var floodFillState: FloodFillState? by floodFillStateProperty.nullable()

    val fill by LazyForeignValue({ statePaintContext }) {
        with(it!!) {
            FloodFill(
                activeViewerProperty.createNullableValueBinding { vat -> vat?.viewer() },
                dataSource,
                assignment,
                { paintera.baseView.orthogonalViews().requestRepaint() },
                { MeshSettings.Defaults.Values.isVisible },
                { floodFillState = it }
            )
        }
    }

    private val overlay by lazy {
        Fill3DOverlay(activeViewerProperty.createNullableValueBinding { it?.viewer() })
    }

    override fun activate() {
        super.activate()
        overlay.visible = true
    }

    override fun deactivate() {
        overlay.visible = false
        super.deactivate()
    }

    override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
        mutableListOf(
            *super<PaintTool>.actionSets.toTypedArray(),
            painteraActionSet("change brush depth", PaintActionType.SetBrushDepth) {
                ScrollEvent.SCROLL {
                    keysExclusive = false
                    onAction { changeBrushDepth(-ControlUtils.getBiggestScroll(it)) }
                }
            },
            painteraActionSet("fill", PaintActionType.Fill) {
                MouseEvent.MOUSE_PRESSED(MouseButton.PRIMARY) {
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
                        val task = fill.fillAt(it!!.x, it.y, statePaintContext?.paintSelection)
                        task?.let {
                            task.onEnd { setFalseAndRemove() }
                        } ?: setFalseAndRemove()
                    }
                }
            },
            painteraActionSet(LabelSourceStateKeys.CANCEL_3D_FLOODFILL, ignoreDisable = true) {
                KEY_PRESSED(LabelSourceStateKeys.namedCombinationsCopy(), LabelSourceStateKeys.CANCEL_3D_FLOODFILL) {
                    graphic = { FontAwesomeIconView().apply { styleClass += listOf("toolbar-tool", "reject") } }
                    filter = true
                    verify { floodFillState != null }
                    onAction {
                        floodFillState!!.interrupt.run()
                        mode?.switchTool(mode.defaultTool)
                    }
                }
            }
        )
    }

    private class Fill3DOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>, override val overlayText: String = "Fill 3D") :
        CursorOverlayWithText(viewerProperty)
}

