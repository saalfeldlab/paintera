package org.janelia.saalfeldlab.paintera.ai.sam

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.paintera.ai.SamEncodeRequester
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import org.janelia.saalfeldlab.samlink.encode.SamEncoder
import org.janelia.saalfeldlab.samlink.encode.TritonEncodeOptions
import java.awt.image.BufferedImage
import java.util.UUID

sealed class SamLinkEncodeRequester<R : EncoderResult, O : TritonEncodeOptions> : SamEncodeRequester<R, O> {

    abstract val samLink: SamEncoder<R, O>

    /** Create a new options instance based on the current configuration. [encode] can override options values per-request. */
    abstract fun createOptions(): O

    override suspend fun healthCheck() = samLink.isReady()
    override fun close() = samLink.close()

    override suspend fun requestSessionId(): String {
        return UUID.randomUUID().toString() //FIXME; either figure out what this means for the triton server, or just make it SAM1 only
    }

    override suspend fun renderImage(state: RenderUnitState): BufferedImage {
        val scaleFactor = ImageRenderer.calculateTargetScreenScaleFactor(
            imageSize.toDouble(),
            state.width.toDouble(),
            state.height.toDouble()
        )
        val screenScales = doubleArrayOf(scaleFactor)

        val img = ImageRenderer.renderBufferedImage(state, screenScales)
        LOG.debug { "rendered ${img.width}x${img.height} image for encoding" }
        return img
    }

    override suspend fun encode(image: BufferedImage, withOptions: (O.() -> Unit)?): R {
        val options = createOptions().also { withOptions?.invoke(it) }
        return samLink.encode(image, options)
    }

    companion object {
        private val LOG = KotlinLogging.logger { }
    }
}
