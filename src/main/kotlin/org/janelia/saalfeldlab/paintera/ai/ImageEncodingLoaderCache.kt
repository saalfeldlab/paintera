package org.janelia.saalfeldlab.paintera.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusRuntimeException
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.util.Subscription
import kotlinx.coroutines.*
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.renderState
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState.Companion.withSessionId
import org.janelia.saalfeldlab.paintera.ai.sam.Sam2EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.cache.AsyncCacheWithLoader
import org.janelia.saalfeldlab.paintera.cache.NavigationBasedRequestTimer
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import java.io.InterruptedIOException

abstract class ImageEncodingLoaderCache<V> : AsyncCacheWithLoader<RenderUnitState, V>(), AutoCloseable
where V : EncoderResult {
    abstract val embeddingRequester: ImageEmbeddingRequester<V>

    private var navigationBasedRequestTimer: NavigationBasedRequestTimer? = null
        set(value) {
            if (value == null) field?.stop()
            field = value?.apply { start() }
        }

    suspend fun healthCheck() = embeddingRequester.healthCheck()

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
        val sessionState = (key as? SessionRenderUnitState)
            ?: let {
                val id = runBlocking { embeddingRequester.requestSessionId() }
                key.withSessionId(id)
            }
        return super.load(sessionState)
    }

    override fun close() {
        loaderScope.cancel("Loader Cache Shutdown ")
        loaderQueueScope.cancel("Loader Cache Shutdown ")
        invalidateAll()
        embeddingRequester.close()
    }

    override suspend fun loader(key: RenderUnitState): V {
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return embeddingRequester.getImageEmbedding(key)
            } catch (error: Throwable) {
                if (error is CancellationException)
                    throw error

                lastError = error
                if (isTerminalError(error)) {
                    LOG.warn(error) { "embedding request failed" }
                    throw error
                }
                LOG.debug(error) { "embedding request failed (attempt ${attempt + 1}/$MAX_RETRIES), retrying" }
            }
        }
        throw InterruptedIOException("Exceeded retry attempts").apply {
            lastError?.let { initCause(it) }
        }
    }

    private fun isTerminalError(error: Throwable): Boolean = when {
        error is InterruptedException -> true
        error is InterruptedIOException -> true
        error is StatusRuntimeException && error.status.code == Status.Code.DEADLINE_EXCEEDED -> true
        else -> false
    }

    companion object {
        private const val MAX_RETRIES = 3
        private val LOG = KotlinLogging.logger {}
    }
}

object SamEncoder {

    val healthCheckProperty = SimpleBooleanProperty(false)
    var isHealthy: Boolean by healthCheckProperty.nonnull()

    private var subscriptions: Subscription? = null
    val cacheProperty by lazy {
        SimpleObjectProperty<ImageEncodingLoaderCache<*>>(Sam2EncodingLoaderCache()).apply {
            subscriptions?.unsubscribe()
            isHealthy = false
            subscriptions += subscribe { it ->
                it.loaderScope.launch {
                    isHealthy = it.healthCheck()
                }
            }

        }
    }

    var cache: ImageEncodingLoaderCache<*> by cacheProperty.nonnull()
}