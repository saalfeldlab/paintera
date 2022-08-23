package org.janelia.saalfeldlab.paintera.ui.overlays

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Cursor
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class BrushOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>) : CursorOverlayInViewer(viewerProperty) {

    private var width = 0.0
    private var height = 0.0
    var canPaint = true
    var reason: String? = null

    init {
        cursor = Cursor.NONE
    }

    val brushDepthProperty = SimpleDoubleProperty().apply { addListener { _, _, _ -> viewer?.display?.drawOverlays() } }
    private val brushDepth by brushDepthProperty.nonnullVal()

    val physicalRadiusProperty = SimpleDoubleProperty().apply {
        addListener { _, _, _ -> viewer?.display?.drawOverlays() }
    }
    private var physicalRadius by physicalRadiusProperty.nonnull()

    override fun drawOverlays(g: GraphicsContext) {

        if (visible && isMouseInside() && viewer?.cursor != Cursor.WAIT) {
            position.apply {
                if (x + physicalRadius > 0 && x - physicalRadius < width && y + physicalRadius > 0 && y - physicalRadius < height) {
                    g.apply {
                        val depthScaleFactor = 5.0
                        val depth = brushDepth
                        if (depth > 1) {
                            stroke = Color.WHEAT.deriveColor(0.0, 1.0, 1.0, 0.5)
                            fill = Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5)
                            font = Font.font(font.family, 15.0)
                            lineWidth = STROKE_WIDTH
                            strokeOval(
                                x - physicalRadius,
                                y - physicalRadius + depth * depthScaleFactor,
                                2 * physicalRadius + 1,
                                2 * physicalRadius + 1
                            )
                            strokeLine(x - physicalRadius, y + depth * depthScaleFactor, x - physicalRadius, y)
                            strokeLine(x + physicalRadius + 1, y + depth * depthScaleFactor, x + physicalRadius + 1, y)
                            fillText("depth=$depth", x + physicalRadius + 1, y + depth * depthScaleFactor + physicalRadius + 1)
                        }

                        stroke = Color.WHITE
                        lineWidth = STROKE_WIDTH
                        strokeOval(x - physicalRadius, y - physicalRadius, 2 * physicalRadius, 2 * physicalRadius)

                        /* Overlay reason text if not valid */
                        if (!canPaint) {
                            reason?.let {
                                fill = Color.WHITE.deriveColor(0.0, 0.0, 0.0, .75)
                                font = Font.font(font.family, 20.0)
                                fillText(it, x - 50, y - physicalRadius - 5)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun setCanvasSize(width: Int, height: Int) {
        this.width = width.toDouble()
        this.height = height.toDouble()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private const val STROKE_WIDTH = 1.5
    }

}
