package org.janelia.saalfeldlab.paintera.state.metadata

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.apache.commons.lang.builder.HashCodeBuilder
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import java.net.URI

data class N5ContainerState(private val n5Container: N5Reader) {

	private val readerProperty: ObservableValue<N5Reader> by lazy { SimpleObjectProperty(n5Container) }
	val reader by readerProperty.nonnullVal()

	private val writerProperty: ObservableValue<N5Writer?> by lazy { SimpleObjectProperty(n5Container as? N5Writer) }
	val writer by writerProperty.nullableVal()

	val uri : URI
		get() = reader.uri

	val isReadOnly: Boolean
		get() = writer == null

	override fun equals(other: Any?): Boolean {
		return if (other is N5ContainerState) {
			/* Equal if we are the same url, and we both either have a writer, or have no writer. */
			uri == other.uri && ((writer == null) == (other.writer == null))
		} else {
			super.equals(other)
		}
	}

	override fun hashCode(): Int {
		val builder = HashCodeBuilder()
			.append(reader.uri)
			.append(writer?.uri ?: 0)
		return builder.toHashCode()
	}
}
