package org.janelia.saalfeldlab.paintera.state.metadata

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import java.util.Optional

data class ContainerState(val url: String, val reader: N5Reader, @JvmField val writer: N5Writer?) {

    val readerProperty : ObservableValue<N5Reader> by lazy { SimpleObjectProperty(reader) }
    val writerProperty : ObservableValue<N5Writer> by lazy { SimpleObjectProperty(writer) }

    fun getWriter() = Optional.ofNullable(writer)
}
