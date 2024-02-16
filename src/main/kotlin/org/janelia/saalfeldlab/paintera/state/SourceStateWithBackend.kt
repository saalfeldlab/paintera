package org.janelia.saalfeldlab.paintera.state

import org.janelia.saalfeldlab.paintera.PainteraBaseView

interface SourceStateWithBackend<D, T> : SourceState<D, T> {
	val backend: SourceStateBackend<D, T>
	val resolution: DoubleArray get() = backend.resolution
	val offset: DoubleArray get() = backend.translation

    override fun onShutdown(paintera: PainteraBaseView) {
        backend.shutdown()
    }
}
