package org.janelia.saalfeldlab.paintera.state.metadata

import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.Paintera

/**
 * N5Container state is a wrapper class for N5Reader that can be compared aagainst, and
 * can lazily get a writer if necessary. if `readOnly` then a writer will always be null,
 * even if it's possible to get one (or we previously had one).
 *
 * @property readerx
 * @property readOnly
 * @constructor Create empty N5container state
 */
data class N5ContainerState(val reader: N5Reader) {

	var readOnly = false
		private set

	constructor(reader: N5Reader, readOnly: Boolean) : this(getAppropriateN5(reader, readOnly)) {
		this.readOnly = readOnly
	}

	val writer by lazy {
		when {
			readOnly -> null
			reader is N5Writer -> reader
			else -> Paintera.n5Factory.openWriter(reader.uri.toString())
		}
	}

	val uri by lazy { reader.uri!! }

	override fun equals(other: Any?) = (other as? N5ContainerState)?.uri == uri || super.equals(other)
	override fun hashCode() = uri.hashCode()

	fun readOnlyCopy() : N5ContainerState {
		return when {
			readOnly -> this
			else -> N5ContainerState(reader, readOnly = true)
		}
	}

	companion object {

		private fun getAppropriateN5(n5: N5Reader, readOnly: Boolean) : N5Reader {
			return when {
				!readOnly-> n5 //We don't care if it's an N5Writer also
				n5 !is N5Writer -> n5 //We know it's not an N5Writer also
				else -> Paintera.n5Factory.openReader(n5.uri.toString(), allowWriter = false) // try and open a read-only N5Reader
			}
		}
	}
}
