package org.janelia.saalfeldlab.paintera.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.*
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.fx.extensions.lazyVar
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.renderState
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState.Companion.withSessionId
import org.janelia.saalfeldlab.paintera.ai.sam.sam2.Sam2EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.cache.ThrottledAsyncCacheWithLoader
import org.janelia.saalfeldlab.paintera.cache.NavigationBasedRequestTimer
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import java.io.InterruptedIOException

abstract class ImageEncodingLoaderCache<V> : ThrottledAsyncCacheWithLoader<RenderUnitState, V>(), AutoCloseable
where V : EncoderResult {
    abstract val embeddingRequester: ImageEmbeddingRequester<V>

    private val currentSessions = HashSet<String>()

    private var navigationBasedRequestTimer: NavigationBasedRequestTimer? = null
        set(value) {
            if (value == null) field?.stop()
            field = value?.apply { start() }
        }

    fun healthCheck() = embeddingRequester.healthCheck()

    fun stopNavigationBasedRequests() {
        navigationBasedRequestTimer = null
    }

    fun startNavigationBasedRequests(viewerAndTransforms: ViewerAndTransforms) {
        navigationBasedRequestTimer = NavigationBasedRequestTimer(
            this,
            viewerAndTransforms,
        )
    }

    fun request(
        viewer: ViewerPanelFX,
        globalToViewerTransform: AffineTransform3D
    ): Deferred<V> {
        return request(viewer.renderState(globalToViewerTransform, excludeActiveSource = true))
    }

    fun load(viewer: ViewerPanelFX, globalToViewerTransform: AffineTransform3D, sessionId: String?): Job {
        val state = sessionId?.let {
            viewer.renderState(globalToViewerTransform, excludeActiveSource = true).withSessionId(it)
        } ?: viewer.renderState(globalToViewerTransform, excludeActiveSource = true)
        return load(state)
    }

    fun load(renderUnitState: RenderUnitState, id: String): Job {
        val state = (renderUnitState as? SessionRenderUnitState)?.state?.withSessionId(id) ?: renderUnitState

        val sessionState = state.withSessionId(id)
        return super.load(sessionState)
    }

    override fun load(key: RenderUnitState): Job {

        return embeddingRequester.scope.launch {
            coroutineScope {
                val sessionState = (key as? SessionRenderUnitState)
                    ?: key.withSessionId(embeddingRequester.requestSessionId())

                synchronized(currentSessions) {
                    currentSessions += sessionState.sessionId
                }
                super.load(sessionState)
            }
        }
    }

    override fun close() {
        loaderScope.cancel("Loader Cache Shutdown ")
        loaderQueueScope.cancel("Loader Cache Shutdown ")
        invalidateAll()
        embeddingRequester.close()
    }

    override suspend fun loader(key: RenderUnitState): V {
        var retry = 3
        runCatching {
            if (retry-- == 0)
                throw InterruptedIOException("Exceeded Retry Attempts without loading successfully")
            embeddingRequester.getImageEmbedding(key)
        }
            .onSuccess { return it }
            .onFailure {
                if (it is InterruptedException || it is InterruptedIOException || (it is StatusRuntimeException && it.status.code == Status.Code.DEADLINE_EXCEEDED)) {
                    LOG.debug(it) { "embedding request failed" }
                    LOG.warn { "embedding request failed" }
                    throw it
                }
                LOG.debug(it) { "embedding request failed, retrying" }
            }
        return loader(key)
    }

    companion object {
        private val LOG = KotlinLogging.logger {}
    }
}

var ImageEncoderCache : ImageEncodingLoaderCache<*> by lazyVar {
    Sam2EncodingLoaderCache()
}