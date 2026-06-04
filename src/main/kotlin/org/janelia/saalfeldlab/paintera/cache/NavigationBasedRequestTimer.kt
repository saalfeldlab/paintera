package org.janelia.saalfeldlab.paintera.cache

import bdv.viewer.TransformListener
import javafx.animation.AnimationTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.ai.ImageEncodingLoaderCache
import java.util.concurrent.atomic.AtomicInteger

class NavigationBasedRequestTimer(
    val embeddingCache: ImageEncodingLoaderCache<*>,
    val viewerAndTransforms: OrthogonalViews.ViewerAndTransforms
    ) : AnimationTimer() {

    companion object {
        private const val REQUEST_COUNTDOWN = 15 // Unit is pulses of the Animation timer; roughly targets 60 FPS.
    }

    private val viewer
        get() = viewerAndTransforms.viewer()
    private val globalToViewerTransform
        get() = viewerAndTransforms.globalToViewerTransform.transformCopy

    private val sessionId by lazy { runBlocking { embeddingCache.embeddingRequester.requestSessionId() } }

    private var previousJob: Job = Job()
        set(value) {
            field.cancel()
            field = value
        }
    private var requestCountDown = AtomicInteger(REQUEST_COUNTDOWN)
    val countdownResetListener = TransformListener<AffineTransform3D> {
        requestCountDown.set(REQUEST_COUNTDOWN)
    }

    override fun handle(now: Long) {
        /* currently using -1 to indicate no change to the transform */
        if (requestCountDown.get() == -1) return
        else if (requestCountDown.getAndDecrement() == 0) {

            previousJob = embeddingCache.load(viewer, globalToViewerTransform, sessionId)
            requestCountDown.getAndSet(-1)
        }
    }

    override fun start() {
        viewerAndTransforms.globalToViewerTransform.addListener(countdownResetListener)
        super.start()
    }

    override fun stop() {
        super.stop()
        viewerAndTransforms.globalToViewerTransform.removeListener(countdownResetListener)
    }
}