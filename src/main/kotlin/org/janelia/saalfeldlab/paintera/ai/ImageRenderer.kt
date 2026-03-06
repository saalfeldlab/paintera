package org.janelia.saalfeldlab.paintera.ai

import bdv.cache.SharedQueue
import bdv.viewer.Interpolation
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.embed.swing.SwingFXUtils
import kotlinx.coroutines.CompletableDeferred
import net.imglib2.parallel.TaskExecutors
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.getDataSourceAndConverter
import org.janelia.saalfeldlab.bdv.fx.viewer.render.BaseRenderUnit
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply
import org.janelia.saalfeldlab.paintera.paintera
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object ImageRenderer {

    enum class ImageEncoding {
        JPEG,
        PNG
    }

    private val LOG = KotlinLogging.logger { }

    suspend fun renderBufferedImage(state: RenderUnitState, screenScales: DoubleArray): BufferedImage {
        val threadGroup = ThreadGroup(this.toString())
        val sharedQueue = SharedQueue(PainteraBaseView.reasonableNumFetcherThreads(), 50)


        val imageRenderer = BaseRenderUnit(
            threadGroup,
            { state },
            { Interpolation.NLINEAR },
            CompositeProjectorPreMultiply.CompositeProjectorFactory(paintera.baseView.sourceInfo().composites()),
            sharedQueue,
            30 * 1000000L,
            TaskExecutors.singleThreaded(),
            skipOverlays = true,
            screenScales = screenScales,
            dimensions = longArrayOf(state.width, state.height),
            useVolatileIfAvailable = false
        )

        val renderedImage = CompletableDeferred<BufferedImage>()

        val sub = imageRenderer.renderedImageProperty.subscribe { _, result ->
            result.image?.let { img ->
                val rgbImage = BufferedImage(
                    img.width.toInt(),
                    img.height.toInt(),
                    BufferedImage.TYPE_INT_RGB
                )
                renderedImage.complete(SwingFXUtils.fromFXImage(img, rgbImage))
            }
        }

        try {
            imageRenderer.requestRepaint()
            return renderedImage.await()
        } finally {
            sub.unsubscribe()
            sharedQueue.shutdown()
            imageRenderer.stopRendering()
        }
    }

    fun ViewerPanelFX.renderState(
        globalToViewerTransform: AffineTransform3D? = null,
        size: Pair<Long, Long>? = null,
        excludeActiveSource: Boolean = true
    ): RenderUnitState {
        val activeSourceToSkip = paintera.currentSource?.sourceAndConverter?.spimSource?.takeIf { excludeActiveSource }
        val sacs = state.sources
            .filterNot { it.spimSource == activeSourceToSkip }
            .map { sac -> getDataSourceAndConverter<Any>(sac) } // to ensure non-volatile
            .toList()
        return RenderUnitState(
            globalToViewerTransform?.copy() ?: AffineTransform3D().also { state.getViewerTransform(it) },
            state.timepoint,
            sacs,
            size?.first ?: width.toLong(),
            size?.second ?: height.toLong()
        )
    }

    /**
     * Calculates the target screen scale factor based on the highest screen scale and the viewer's dimensions.
     * The resulting scale factor will always be the smallest of either:
     *  1. the highest explicitly specified factor, or
     *  2. [maxTargetSize] / `max(width, height)`
     *
     *  This means if the `scaleFactor * maxEdge` is less than [maxTargetSize] it will be used,
     *  but if the `scaleFactor * maxEdge` is still larger than [maxTargetSize], then a more
     *  aggressive scale factor will be returned.
     *
     * @return The calculated scale factor.
     */
    private fun calculateTargetScreenScaleFactor(
        width: Double,
        height: Double,
        highestScreenScale: Double,
        maxTargetSize: Double,
    ): Double {
        val maxEdge = max(ceil(width * highestScreenScale), ceil(height * highestScreenScale))
        return min(highestScreenScale, maxTargetSize / maxEdge)
    }

    internal fun calculateTargetScreenScaleFactor(
        maxTargetEdge: Double,
        renderWidth: Double,
        renderHeight: Double
    ): Double {
        val maxScreenScale = paintera.properties.screenScalesConfig.screenScalesProperty().get().scalesCopy.max()
        return calculateTargetScreenScaleFactor(
            renderWidth,
            renderHeight,
            maxScreenScale,
            maxTargetEdge,
        )
    }

}


