package org.janelia.saalfeldlab.paintera.ai.sam

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import org.janelia.saalfeldlab.samlink.encode.ImageFormat
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam1HttpEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam1TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam2EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam2TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerEncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerTritonEncoder
import kotlin.coroutines.cancellation.CancellationException

class Sam1LegacyEncodeRequester : SamLinkEncodeRequester<Sam1EncoderResult>() {

    override val imageSize = 1024

    override val samLink = Sam1HttpEncoder(
        serviceUrl = properties.samServiceConfig.sam1LegacyConfig.serviceUrl,
        responseTimeout = properties.samServiceConfig.sam1LegacyConfig.responseTimeout
    )


    override suspend fun getImageEmbedding(it: RenderUnitState): Sam1EncoderResult {

        val config = paintera.properties.samServiceConfig.sam1LegacyConfig
        val encoding = config.imageEncoding.let {
            when (it) {
                ImageRenderer.ImageEncoding.JPEG -> ImageFormat.JPEG
                ImageRenderer.ImageEncoding.PNG -> ImageFormat.PNG
            }
        }

        val scaleFactor = ImageRenderer.calculateTargetScreenScaleFactor(
            imageSize.toDouble(),
            it.width.toDouble(),
            it.height.toDouble()
        )
        val screenScales = doubleArrayOf(scaleFactor)

        val img = ImageRenderer.renderBufferedImage(it, screenScales)
        val requestId = (it as? SessionRenderUnitState)?.sessionId ?: requestSessionId()
        val options = samLink.options().apply {
            imageFormat = encoding
            compressEncoding = config.compressEncoding
            cancelPending = true
            sessionId = requestId
        }

        val encodeJob = scope
            .async { samLink.encode(img, options) }
            .apply {
                invokeOnCompletion { cause ->
                    when (cause) {
                        null, is CancellationException -> {}
                        else -> LOG.warn { "encode job failed (requestId=$requestId)" }
                    }
                    currentSessions.remove(requestId)
                }
            }
        currentSessions.replace(requestId, encodeJob)?.cancel()

        return encodeJob.await()
    }

    override suspend fun requestSessionId(): String {
        return samLink.newSessionId()
    }

    override fun cancelPendingRequests(vararg ids: String) {

        if (currentSessions.isEmpty())
            return

        val iter = currentSessions.entries.iterator()
        while (iter.hasNext()) {
            val (requestId, job) = iter.next()
            job.cancel()
            iter.remove()
            scope.launch {
                samLink.cancelPending(requestId)
            }
        }

    }
    companion object {
        private val LOG = KotlinLogging.logger { }
    }

}

class Sam1EncodeRequester : SamLinkEncodeRequester<Sam1EncoderResult>() {

    override val imageSize = 1024

    override val samLink = with(paintera.properties.samServiceConfig.sam1Config) {
        Sam1TritonEncoder(
            serviceHost = host,
            grpcPort = port,
            encoderModel = encoderName,
            responseTimeout = responseTimeout
        )
    }
}

class Sam2EncodeRequester : SamLinkEncodeRequester<Sam2EncoderResult>() {

    override val imageSize = 1024

    override val samLink = with(paintera.properties.samServiceConfig.sam2Config) {
        Sam2TritonEncoder(
            serviceHost = host,
            grpcPort = port,
            encoderModel = encoderName,
            responseTimeout = responseTimeout
        )
    }
}

class Sam3EncodeRequester : SamLinkEncodeRequester<Sam3TrackerEncoderResult>() {

    override val imageSize = 1008

    override val samLink = with(paintera.properties.samServiceConfig.sam3Config) {
        Sam3TrackerTritonEncoder(
            serviceHost = host,
            grpcPort = port,
            encoderModel = encoderName,
            responseTimeout = responseTimeout
        )
    }
}