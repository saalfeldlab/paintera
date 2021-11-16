package org.janelia.saalfeldlab.paintera.state.metadata

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.apache.commons.lang.builder.HashCodeBuilder
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.state.raw.n5.urlRepresentation
import java.util.Optional

//TODO Caleb: think about allowing just the url, and getting the rest ourselves. Ther much easier equals/hashCode
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

    override fun hashCode(): Int {
        val builder = HashCodeBuilder()
            .append(url)
            .append(reader.urlRepresentation())
            .append(writer?.urlRepresentation() ?: 0)
        return builder.toHashCode()
    }

    companion object {
        @JvmStatic
        fun tmpFromN5Meta(meta: N5Meta): N5ContainerState {
            val fsWriter = meta.writer as N5FSWriter
            return N5ContainerState(fsWriter.basePath, fsWriter, fsWriter)
        }

        @JvmStatic
        fun tmpFromN5FSWriter(writer: N5Writer): N5ContainerState {
            val fsWriter = writer as N5FSWriter
            return N5ContainerState(fsWriter.basePath, fsWriter, fsWriter)
        }
    }
}
