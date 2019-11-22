package org.janelia.saalfeldlab.paintera.state

import org.janelia.saalfeldlab.n5.N5Writer

interface SourceStateBackendN5<D, T> : SourceStateBackend<D, T> {
	val container: N5Writer
	val dataset: String
}
