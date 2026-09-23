package org.janelia.saalfeldlab.paintera.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusRuntimeException
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.renderState
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState.Companion.withSessionId
import org.janelia.saalfeldlab.paintera.ai.sam.Sam2EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.ai.sam.SamLinkEncodeRequester
import org.janelia.saalfeldlab.paintera.cache.AsyncCacheWithLoader
import org.janelia.saalfeldlab.paintera.cache.NavigationBasedRequestTimer
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import org.janelia.saalfeldlab.samlink.encode.TritonEncodeOptions
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap

abstract class ImageEncodingLoaderCache<V> : AsyncCacheWithLoader<RenderUnitState, V>(), AutoCloseable
where V : EncoderResult {
    abstract val embeddingRequester: SamLinkEncodeRequester<V, out TritonEncodeOptions>

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
        return load(sessionState)
    }

    /* live eager requests associated with their session ID  */
    private val eagerRequests = ConcurrentHashMap<Deferred<V>, String>()
    private val eagerRenderSlot = Semaphore(1)

    override fun load(key: RenderUnitState): Job {
        val sessionState = (key as? SessionRenderUnitState)
            ?: let {
                val id = runBlocking { embeddingRequester.requestSessionId() }
                key.withSessionId(id)
            }
        /* invalidate if exceptional */
        cache.getIfPresent(sessionState)?.invokeOnCompletion { cause -> cause?.let { invalidate(sessionState) } }
        /* reuse if present */
        cache.getIfPresent(sessionState)?.let { cached -> return loaderQueueScope.launch { cached.join() } }
        /* trigger the load */
        return loaderQueueScope.async {
            if (!isActive) invalidate(sessionState)
            else request(sessionState, clear = false) { state ->
                loaderScope.async { loader(state, EncodePriority.EAGER) }.also { eager ->
                    eagerRequests[eager] = sessionState.sessionId
                    eager.invokeOnCompletion { eagerRequests -= eager }
                }
            }.await()
        }
    }

    /** an immediate request. if this is a promotion from an eager request, remove the eagerRequests reference so it isn't cancelled accidentally  */
    override fun request(key: RenderUnitState, clear: Boolean): Deferred<V> {
        return super.request(key, clear).also { eagerRequests -= it }
    }

    fun cancelEagerRequests(sessionId: String) {
        val stale = eagerRequests.filterValues { it == sessionId }.keys
        stale.forEach { eager ->
            eagerRequests -= eager
            eager.cancel(CancellationException("eager request cancelled for session $sessionId"))
        }
    }

    override fun close() {
        loaderScope.cancel("Loader Cache Shutdown ")
        loaderQueueScope.cancel("Loader Cache Shutdown ")
        invalidateAll()
        embeddingRequester.close()
    }


    override suspend fun loader(key: RenderUnitState) = loader(key, EncodePriority.IMMEDIATE)

    private suspend fun loader(key: RenderUnitState, priority: EncodePriority): V {
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                /* immediate requests trigger immediately, eager requests go one at a time (render is faster than encode, so this doesn't bottleneck the parallel eager requests)*/
                val image = when (priority) {
                    EncodePriority.IMMEDIATE -> embeddingRequester.renderImage(key)
                    EncodePriority.EAGER -> eagerRenderSlot.withPermit { embeddingRequester.renderImage(key) }
                }
                return embeddingRequester.encode(image) { this.priority = priority.level }
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
        private const val MAX_RETRIES = 2
        private val LOG = KotlinLogging.logger {}
    }
}

object SamEncoder {

    val healthCheckProperty = SimpleBooleanProperty(false)
    var isHealthy: Boolean by healthCheckProperty.nonnull()

    /* for control messages, like health checks. */
    private val controlScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun checkHealth(cache: ImageEncodingLoaderCache<*>) {
        controlScope.launch {
            isHealthy = runCatching { cache.healthCheck() }
                .onFailure { LOG.warn(it) { "SAM encoder health check failed" } }
                .getOrDefault(false)
        }
    }

    private var subscriptions: Subscription? = null
    private val lazyCacheProperty = lazy {
        SimpleObjectProperty<ImageEncodingLoaderCache<*>>(Sam2EncodingLoaderCache()).apply {
            subscriptions?.unsubscribe()
            isHealthy = false
            subscriptions += subscribe { it -> checkHealth(it) }
        }
    }
    val cacheProperty by lazyCacheProperty
    var cache: ImageEncodingLoaderCache<*> by cacheProperty.nonnull()

    /**
     * Invalidate all cache entries and cancel in-flight requests.
     *
     */
    fun reset() {
        if (lazyCacheProperty.isInitialized()) {
            cache.apply {
                stopNavigationBasedRequests()
                cancelUnfinishedRequests()
                invalidateAll()
            }
            checkHealth(cache)
        }
    }

    /**
     * Shutdown the current ImageEncodingLoaderCache, if one was initialized.
     *
     */
    fun shutdown() {
        if (lazyCacheProperty.isInitialized()) {
            /* a closed encoder is not healthy; if a new cache replaces this one, its health
             * check will update this again */
            isHealthy = false
            /* failure to close shouldn't block the quit path */
            runCatching { cache.close() }.onFailure {
                LOG.warn(it) { "Failed to close the Image Encoder Cache" }
            }
        }
    }

    private val LOG = KotlinLogging.logger {}
}