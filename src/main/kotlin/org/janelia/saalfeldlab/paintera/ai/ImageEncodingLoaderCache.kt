package org.janelia.saalfeldlab.paintera.ai

import ai.onnxruntime.OrtSession
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.fx.extensions.lazyVar
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.ai.sam.sam2.Sam2EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.ai.sam.sam3.Sam3EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.cache.AsyncCacheWithLoader

abstract class ImageEncodingLoaderCache<V> : AsyncCacheWithLoader<RenderUnitState, V>(), AutoCloseable
where V : EncoderResult {
    abstract val embeddingRequester: ImageEmbeddingRequester<V>
    abstract val healthCheck : Boolean
    abstract val createOrtSessionTask: Deferred<OrtSession>

    abstract fun stopNavigationBasedRequests()
    abstract fun startNavigationBasedRequests(viewerAndTransforms: OrthogonalViews.ViewerAndTransforms)

    abstract fun request(viewer: ViewerPanelFX, globalToViewerTransform: AffineTransform3D): Deferred<V>

    abstract fun load(viewer: ViewerPanelFX, globalToViewerTransform: AffineTransform3D, sessionId: String? = null): Job

    abstract fun load(renderUnitState: RenderUnitState, id: String): Job

    override fun close() {
        loaderScope.cancel("Loader Cache Shutdown ")
        loaderQueueScope.cancel("Loader Cache Shutdown ")
        invalidateAll()
        embeddingRequester.close()
    }
}

var ImageEncoderCache : ImageEncodingLoaderCache<*> by lazyVar {
//    Sam1EncodingLoaderCache()
    Sam2EncodingLoaderCache()
//    Sam3EncodingLoaderCache()
}