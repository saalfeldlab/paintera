package org.janelia.saalfeldlab.paintera.state

import bdv.util.volatiles.SharedQueue
import javafx.scene.Node
import org.janelia.saalfeldlab.paintera.data.DataSource

interface SourceStateBackend<D, T> {

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
