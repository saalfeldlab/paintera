package org.janelia.saalfeldlab.paintera.state

import net.imglib2.Interval
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.state.metadata.Slice3D

interface SourceStateWithBackend<D, T> : SourceState<D, T> {
	val backend: SourceStateBackend<D, T>
	val resolution: DoubleArray get() = backend.resolution
	val offset: DoubleArray get() = backend.translation
	val virtualCrop: Interval? get() = backend.virtualCrop
	val slice3D: Slice3D? get() = backend.slice3D

    override fun onShutdown(paintera: PainteraBaseView) {
        backend.shutdown()
    }
}
