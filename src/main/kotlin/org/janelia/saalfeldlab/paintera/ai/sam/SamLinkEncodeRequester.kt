package org.janelia.saalfeldlab.paintera.ai.sam

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.plus
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.paintera.ai.SamEncodeRequester
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import org.janelia.saalfeldlab.samlink.encode.SamEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class SamLinkEncodeRequester<R : EncoderResult> : SamEncodeRequester<R> {

    abstract val samLink: SamEncoder<R, *>

    override suspend fun healthCheck() = samLink.isReady()
    override fun close() = samLink.close()

    protected val currentSessions = ConcurrentHashMap<String, Job>()
    override val scope = SamEncodeRequester.embeddingIOScope + SupervisorJob() + CoroutineName("SAM_EMBEDDING_IO")

    override suspend fun requestSessionId(): String {
        return UUID.randomUUID().toString() //FIXME; either figure out what this means for the triton server, or just make it SAM1 only
    }

    override suspend fun getImageEmbedding(it: RenderUnitState): R {

        val scaleFactor = ImageRenderer.calculateTargetScreenScaleFactor(
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

    companion object {
        private val LOG = KotlinLogging.logger { }
    }
}