package org.janelia.saalfeldlab.paintera.state

interface SourceStateWithBackend<D, T> : SourceState<D, T> {
    val backend: SourceStateBackend<D, T>

    val resolution: DoubleArray get() = backend.getMetadataState().resolution
    val offset: DoubleArray get() = backend.getMetadataState().translation
}
