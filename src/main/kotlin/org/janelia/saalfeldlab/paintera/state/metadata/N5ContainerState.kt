package org.janelia.saalfeldlab.paintera.state.metadata

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import java.util.Optional

data class N5ContainerState(val url: String, val reader: N5Reader, @JvmField val writer: N5Writer?) {

    val readerProperty: ObservableValue<N5Reader> by lazy { SimpleObjectProperty(reader) }
    val writerProperty: ObservableValue<N5Writer> by lazy { SimpleObjectProperty(writer) }

    fun getWriter() = Optional.ofNullable(writer)


    override fun equals(other: Any?): Boolean {
        return if (other is N5ContainerState) {
            /* Equal if we are the same url, and we both either have a writer, or have no writer. */
            url == other.url && ((writer == null) == (other.writer == null))
        } else {
            super.equals(other)
        }
    }
}
