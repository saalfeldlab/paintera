package org.janelia.saalfeldlab.paintera.state

interface SourceStateWithBackend<D, T> : SourceState<D, T> {
	val backend: SourceStateBackend<D, T>
}
