package org.janelia.saalfeldlab.paintera.state.metadata

import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.Paintera

data class N5ContainerState(val reader: N5Reader) {

	val writer by lazy {
		(reader as? N5Writer) ?: Paintera.n5Factory.openWriterOrNull(uri.toString())
	}

	val uri by lazy { reader.uri!! }

	override fun equals(other: Any?) = (other as? N5ContainerState)?.uri == uri || super.equals(other)
	override fun hashCode() = uri.hashCode()
}
