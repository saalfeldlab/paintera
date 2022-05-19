package org.janelia.saalfeldlab.paintera.ui.overlays

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.property.SimpleDoubleProperty
import javafx.scene.Cursor
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.stream.IntStream
import kotlin.math.sqrt

class BrushOverlay(viewer: ViewerPanelFX) : CursorOverlayInViewer(viewer) {

    private var width = 0.0
    private var height = 0.0
    var canPaint = true
    var reason: String? = null

    private val viewerTransform = AffineTransform3D()

    val brushDepthProperty = SimpleDoubleProperty().apply { addListener { _, _, _ -> viewer.display.drawOverlays() } }
    private val brushDepth by brushDepthProperty.nonnullVal()

    private val viewerRadiusProperty = SimpleDoubleProperty().apply {
        addListener { _, _, _ -> viewer.display.drawOverlays() }
        addListener { _, _, _ ->
            LOG.debug(
                "Updating paint brush overlay radius: physical radius={}, viewer radius={}, viewer transform={}",
                physicalRadiusProperty,
                this,
                viewerTransform
            )
        }
    }
    private var viewerRadius by viewerRadiusProperty.nonnull()

    val physicalRadiusProperty = SimpleDoubleProperty().apply {
        addListener { _, _, _ ->
            updateViewerRadius(viewerTransform.copy())
        }
    }
    private var physicalRadius by physicalRadiusProperty.nonnull()

    init {
        viewer.addTransformListener(viewerTransform::set)
        viewer.addTransformListener(this::updateViewerRadius)
        viewer.state.getViewerTransform(viewerTransform)
        updateViewerRadius(viewerTransform)
    }

    override fun getCursor(): Cursor = Cursor.NONE

    override fun drawOverlays(g: GraphicsContext) {

        if (visible && viewer.isMouseInside) {
            val scaledRadius: Double = viewerRadius
            position.apply {
                if (x + scaledRadius > 0 && x - scaledRadius < width && y + scaledRadius > 0 && y - scaledRadius < height) {
                    g.apply {
                        val depthScaleFactor = 5.0
                        val depth = brushDepth
                        if (depth > 1) {
                            stroke = Color.WHEAT.deriveColor(0.0, 1.0, 1.0, 0.5)
                            fill = Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5)
                            font = Font.font(font.family, 15.0)
                            lineWidth = STROKE_WIDTH
                            strokeOval(
                                x - scaledRadius,
                                y - scaledRadius + depth * depthScaleFactor,
                                2 * scaledRadius + 1,
                                2 * scaledRadius + 1
                            )
                            strokeLine(x - scaledRadius, y + depth * depthScaleFactor, x - scaledRadius, y)
                            strokeLine(x + scaledRadius + 1, y + depth * depthScaleFactor, x + scaledRadius + 1, y)
                            fillText("depth=$depth", x + scaledRadius + 1, y + depth * depthScaleFactor + scaledRadius + 1)
                        }

                        stroke = Color.WHITE
                        lineWidth = STROKE_WIDTH
                        strokeOval(x - scaledRadius, y - scaledRadius, 2 * scaledRadius + 1, 2 * scaledRadius + 1)
                        viewer.scene.cursor = getCursor()

                        /* Overlay reason text if not valid */
                        if (!canPaint) {
                            reason?.let {
                                fill = Color.WHITE.deriveColor(0.0, 0.0, 0.0, .75)
                                font = Font.font(font.family, 20.0)
                                fillText(it, x - 50, y - scaledRadius - 5)
                            }
                        }
                    }
                }
            }
        }
        if (wasVisible) {
            viewer.scene.cursor = Cursor.DEFAULT
            wasVisible = false
        }
    }

    override fun setCanvasSize(width: Int, height: Int) {
        this.width = width.toDouble()
        this.height = height.toDouble()
    }

    private fun updateViewerRadius(transform: AffineTransform3D) {
        viewerRadius = viewerRadius(transform, this.physicalRadius)
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
        private const val STROKE_WIDTH = 1.5

        fun viewerRadius(transform: AffineTransform3D, physicalRadius: Double): Double {
            val sum11 = IntStream.range(0, 3).mapToDouble { transform.inverse()[it, 0] }.map { it * it }.sum()
            return physicalRadius / sqrt(sum11)
        }
    }

}
