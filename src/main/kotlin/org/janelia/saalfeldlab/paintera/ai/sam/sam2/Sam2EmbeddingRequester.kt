package org.janelia.saalfeldlab.paintera.ai.sam.sam2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.janelia.saalfeldlab.samlink.encode.Sam2EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam2TritonEncoder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.plus
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.paintera.ai.ImageEmbeddingRequester
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.calculateTargetScreenScaleFactor
import org.janelia.saalfeldlab.paintera.ai.SamLinkEmbeddingRequester
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState
import org.janelia.saalfeldlab.paintera.paintera
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException


class Sam2EmbeddingRequester : SamLinkEmbeddingRequester<Sam2EncoderResult> {

    private val currentSessions = ConcurrentHashMap<String, Job>()
    override val scope = ImageEmbeddingRequester.embeddingIOScope + SupervisorJob() + CoroutineName("SAM_EMBEDDING_IO")

    override val imageSize = 1024

    override val samLink = with(paintera.properties.samServiceConfig.sam2Config) {
        Sam2TritonEncoder(
            serviceHost = host,
            grpcPort = port,
            encoderModel = encoderName,
            responseTimeout = responseTimeout
        )
    }

    override suspend fun getImageEmbedding(it: RenderUnitState): Sam2EncoderResult {

        val scaleFactor = calculateTargetScreenScaleFactor(
            imageSize.toDouble(),
            it.width.toDouble(),
            it.height.toDouble()
        )
        val screenScales = doubleArrayOf(scaleFactor)

        val img = ImageRenderer.renderBufferedImage(it, screenScales)
        LOG.debug { "rendered ${img.width}x${img.height} image for encoding" }
        val requestId = (it as? SessionRenderUnitState)?.sessionId ?: requestSessionId()
        val encodeJob = scope.async { samLink.encode(img) }.apply {
            invokeOnCompletion {
                currentSessions.remove(requestId)
            }
        }
        currentSessions.replace(requestId, encodeJob)?.cancel()

        try {
            return encodeJob.await()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.DEADLINE_EXCEEDED)
                throw InterruptedException("${e.status}")
            throw e
        }
    }


    override fun requestSessionId(): String {
        return UUID.randomUUID().toString()
    }

    override fun cancelPendingRequests(vararg ids: String) {

        if (ids.isEmpty()) {
            val iter = currentSessions.entries.iterator()
            while (iter.hasNext()) {
                val (_, job) = iter.next()
                job.cancel()
                iter.remove()
            }
        } else {
            ids.forEach { id ->
                currentSessions.remove(id)?.cancel()
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