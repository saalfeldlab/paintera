package org.janelia.saalfeldlab.paintera.state

import javafx.scene.Node
import org.janelia.saalfeldlab.paintera.data.DataSource

interface SourceStateBackend<D, T> {

	val source: DataSource<D, T>

	fun createMetaDataNode(): Node

}
