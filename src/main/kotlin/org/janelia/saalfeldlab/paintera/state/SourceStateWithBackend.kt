package org.janelia.saalfeldlab.paintera.state

import net.imglib2.Interval
import org.janelia.saalfeldlab.paintera.PainteraBaseView

interface SourceStateWithBackend<D, T> : SourceState<D, T> {
	val backend: SourceStateBackend<D, T>
	val resolution: DoubleArray get() = backend.resolution
	val offset: DoubleArray get() = backend.translation
	val crop: Interval? get() = backend.crop

    override fun onShutdown(paintera: PainteraBaseView) {
        backend.shutdown()
    }
}
