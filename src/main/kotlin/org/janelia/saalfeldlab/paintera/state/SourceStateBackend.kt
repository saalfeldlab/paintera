package org.janelia.saalfeldlab.paintera.state

import bdv.util.volatiles.SharedQueue
import javafx.scene.Node
import org.janelia.saalfeldlab.paintera.data.DataSource

interface SourceStateBackend<D, T> {

    //TODO Caleb: For now assume all are, unless overriden to the contrary (for backwards compatibility)
    //  Perhaps remove default at some point.
    fun canWriteToSource() = true

    fun createSource(
        queue: SharedQueue,
        priority: Int,
        name: String,
        resolution: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0),
        offset: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0)
    ): DataSource<D, T>

    fun createMetaDataNode(): Node

    val defaultSourceName: String

}
