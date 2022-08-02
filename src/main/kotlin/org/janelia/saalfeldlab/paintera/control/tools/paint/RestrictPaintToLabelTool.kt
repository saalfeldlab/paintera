package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.RestrictPainting
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText


class RestrictPaintToLabelTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) :
    PaintTool(activeSourceStateProperty, mode) {

    override val graphic = { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "restrict-tool") } }
    override val name = "Restrict Paint To Label"
    override val keyTrigger = listOf(KeyCode.R, KeyCode.SHIFT)

    private val overlay by lazy {
        RestrictOverlay(activeViewerProperty.createNullableValueBinding { it?.viewer() })
    }

    override fun activate() {
        super.activate()
        overlay.visible = true
    }

    override fun deactivate() {
        overlay.visible = false
        super.deactivate()
    }

    override val actionSets: MutableList<ActionSet> = mutableListOf(
        painteraActionSet("restrict", PaintActionType.Restrict) {
            MouseEvent.MOUSE_PRESSED(MouseButton.PRIMARY) {
                keysExclusive = false
                verifyEventNotNull()
                onAction { restrictor?.restrictTo(it!!.x, it.y) }
            }
        }
    )

    private val restrictor: RestrictPainting?
        get() = activeViewer?.let { viewer ->
            statePaintContext?.let { ctx ->
                RestrictPainting(viewer, paintera.baseView.sourceInfo(), paintera.baseView.orthogonalViews()::requestRepaint, ctx::getMaskForLabel)
            }
        }

    private class RestrictOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>, override val overlayText: String = "Restrict") :
        CursorOverlayWithText(viewerProperty)
}
