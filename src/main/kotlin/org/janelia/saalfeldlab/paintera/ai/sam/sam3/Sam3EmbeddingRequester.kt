package org.janelia.saalfeldlab.paintera.ai.sam.sam3

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.plus
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.paintera.ai.ImageEmbeddingRequester
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.calculateTargetScreenScaleFactor
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState
import org.janelia.saalfeldlab.paintera.properties
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerEncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerTritonEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


class Sam3EmbeddingRequester : ImageEmbeddingRequester<Sam3TrackerEncoderResult> {

    private val currentSessions = ConcurrentHashMap<String, Job>()
    override val scope = ImageEmbeddingRequester.embeddingIOScope + SupervisorJob() + CoroutineName("SAM_EMBEDDING_IO")

    override val imageSize = 1008

    private val samLink = Sam3TrackerTritonEncoder(
        serviceUrl = "saalfelds-ws2",
        modelName = "sam3_tracker_vision_encoder_fp16",
        responseTimeout = properties.segmentAnythingConfig.responseTimeout
    )

    override suspend fun getImageEmbedding(it: RenderUnitState): Sam3TrackerEncoderResult {

        val scaleFactor = calculateTargetScreenScaleFactor(
            imageSize.toDouble(),
            it.width.toDouble(),
            it.height.toDouble()
        )
        val screenScales = doubleArrayOf(scaleFactor)

        val img = ImageRenderer.renderBufferedImage(it, screenScales)
        val requestId = (it as? SessionRenderUnitState)?.sessionId ?: requestSessionId()
        val encodeJob = scope
            .async { samLink.encode(img) }
            .apply { invokeOnCompletion { currentSessions.remove(requestId) } }

        currentSessions.replace(requestId, encodeJob)?.cancel()

        return encodeJob.await()
    }

    override fun healthCheck(): Boolean {
        return samLink.isReady()
    }

    override fun requestSessionId(): String {
        return UUID.randomUUID().toString()
    }

    override fun cancelPendingRequests(vararg ids: String) {

        if (ids.isEmpty()) {
            val iter = currentSessions.entries.iterator()
            while (iter.hasNext()) {
                val (id, job) = iter.next()
                job.cancel()
                iter.remove()
//                TODO() Cancel `id` to server
            }
        } else  {
            ids.forEach { id ->
                currentSessions.remove(id)?.let {
                    it.cancel()
//                TODO() Cancel `id` to server
                }
            }
        }
    }

    override fun close() {
        samLink.close()
    }

}