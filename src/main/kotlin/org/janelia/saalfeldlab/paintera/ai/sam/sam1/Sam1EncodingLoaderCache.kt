package org.janelia.saalfeldlab.paintera.ai.sam.sam1

import ai.onnxruntime.OrtEnvironment.getEnvironment
import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult
import kotlinx.coroutines.*
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.paintera.ai.ImageEncodingLoaderCache
import org.janelia.saalfeldlab.paintera.ai.ImageEmbeddingRequester
import org.janelia.saalfeldlab.paintera.ai.ImageRenderer.renderState
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState
import org.janelia.saalfeldlab.paintera.ai.SessionRenderUnitState.Companion.withSessionId
import org.janelia.saalfeldlab.paintera.ai.sam.sam2.Sam2EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.cache.NavigationBasedRequestTimer
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.cancellation.CancellationException

class Sam1EncodingLoaderCache : ImageEncodingLoaderCache<Sam1EncoderResult>() {

    override val cache: LoaderCache<RenderUnitState, Deferred<Sam1EncoderResult>> = SoftRefLoaderCache()

    override val embeddingRequester: ImageEmbeddingRequester<Sam1EncoderResult> by LazyForeignValue({
        paintera.properties.segmentAnythingConfig.run { "$serviceUrl?responseTimeout=${responseTimeout}" }
    }) {
        Sam1EmbeddingRequester()
    }

    override tailrec suspend fun loader(key: RenderUnitState): Sam1EncoderResult {
        runCatching { embeddingRequester.getImageEmbedding(key) }
            .onSuccess { return it }
            .onFailure {
                if (it is SocketTimeoutException) {
                    LOG.error(it) { "embedding request failed" }
                    throw it
                }
                LOG.debug(it) { "embedding request failed, retrying" }
            }
        return loader(key)
    }

    override val healthCheck by LazyForeignValue({ embeddingRequester }) {
        it.healthCheck()
    }


    private var navigationBasedRequestTimer: NavigationBasedRequestTimer? = null
        set(value) {
            if (value == null) field?.stop()
            field = value?.apply { start() }
        }

    override fun stopNavigationBasedRequests() {
        navigationBasedRequestTimer = null
    }

    override fun startNavigationBasedRequests(viewerAndTransforms: ViewerAndTransforms) {
        navigationBasedRequestTimer = NavigationBasedRequestTimer(
            this,
            viewerAndTransforms,
        )
    }


    private val currentSessions = HashSet<String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val createOrtSessionTask by LazyForeignValue({ properties.segmentAnythingConfig.modelLocation }) { modelLocation ->

        embeddingRequester.scope.async {
            val modelArray = try {
                this::class.java.classLoader.getResourceAsStream(modelLocation)!!.readAllBytes()
            } catch (e: Exception) {
                Files.readAllBytes(Paths.get(modelLocation))
            }
            val session = getEnvironment().createSession(modelArray)
            session
        }
    }.beforeValueChange { job ->
        job?.invokeOnCompletion {
            job.getCompleted().close()
        }
        job?.cancel(CancellationException("Ort Model Location Changed"))
    }

    override fun request(viewer: ViewerPanelFX, globalToViewerTransform: AffineTransform3D): Deferred<Sam1EncoderResult> {
        return request(viewer.renderState(globalToViewerTransform, excludeActiveSource = true))
    }

    override fun load(viewer: ViewerPanelFX, globalToViewerTransform: AffineTransform3D, sessionId: String?): Job {
        val state = sessionId?.let {
            viewer.renderState(globalToViewerTransform, excludeActiveSource = true).withSessionId(it)
        } ?: viewer.renderState(globalToViewerTransform, excludeActiveSource = true)
        return load(state)
    }

    override fun load(renderUnitState: RenderUnitState, id: String): Job {
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

    companion object {
        private val LOG = KotlinLogging.logger { }
    }
}

