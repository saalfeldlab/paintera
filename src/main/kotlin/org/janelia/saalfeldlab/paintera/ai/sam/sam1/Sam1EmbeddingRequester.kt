package org.janelia.saalfeldlab.paintera.ai.sam.sam1

import org.janelia.saalfeldlab.samlink.encode.ImageFormat
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam1HttpEncoder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.paintera.ai.ImageEmbeddingRequester
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.ImageEncoding
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.calculateTargetScreenScaleFactor
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class Sam1EmbeddingRequester : ImageEmbeddingRequester<Sam1EncoderResult> {


    private val currentSessions = ConcurrentHashMap<String, Job>()
    override val scope = ImageEmbeddingRequester.embeddingIOScope + SupervisorJob() + CoroutineName("SAM_EMBEDDING_IO")

    private val samLink = Sam1HttpEncoder(
        serviceUrl = properties.segmentAnythingConfig.serviceUrl,
        responseTimeout = properties.segmentAnythingConfig.responseTimeout
    )

    override val imageSize = 1024

    override suspend fun getImageEmbedding(it: RenderUnitState): Sam1EncoderResult {

        val config = paintera.properties.segmentAnythingConfig
        val encoding = config.imageEncoding.let {
            when (it) {
                ImageEncoding.JPEG -> ImageFormat.JPEG
                ImageEncoding.PNG -> ImageFormat.PNG
            }
        }

        val scaleFactor = calculateTargetScreenScaleFactor(
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



    override fun healthCheck(): Boolean {
        return samLink.isReady()
    }

    override fun requestSessionId(): String {
        return samLink.newSessionId()
    }

    override fun cancelPendingRequests(vararg id: String) {

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


    override fun close() {
        samLink.close()
    }
    companion object {
        private val LOG = KotlinLogging.logger { }
    }

}