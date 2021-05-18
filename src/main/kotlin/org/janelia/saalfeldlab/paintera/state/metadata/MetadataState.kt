package org.janelia.saalfeldlab.paintera.state.metadata

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.util.n5.metadata.PainteraBaseMetadata

open class MetadataState constructor(open val n5ContainerState: N5ContainerState, open val metadata: PainteraBaseMetadata) {
    val metadataProperty: ObservableValue<PainteraBaseMetadata> by lazy { SimpleObjectProperty(metadata) }

    val reader
        get() = n5ContainerState.reader
    val writer
        get() = n5ContainerState.getWriter()
    val group
        get() = metadata.path!!

    companion object {

    }
}
