package org.janelia.saalfeldlab.paintera.state.metadata

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.util.n5.metadata.PainteraBaseMetadata

data class MetadataState(val containerState: ContainerState, val metadata: PainteraBaseMetadata) {
    val metadataProperty: ObservableValue<PainteraBaseMetadata> by lazy { SimpleObjectProperty(metadata) }

    val reader = containerState.reader
    val writer = containerState.getWriter()
    val group = metadata.path!!

}
