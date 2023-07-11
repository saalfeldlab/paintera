package org.janelia.saalfeldlab.paintera.state

import bdv.util.volatiles.SharedQueue
import javafx.scene.Node
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils

interface SourceStateBackend<D, T> {

	//TODO Caleb: For now assume all are, unless overriden to the contrary (for backwards compatibility)
	//  Perhaps remove default at some point.
	fun canWriteToSource() = true

	fun createSource(
		queue: SharedQueue,
		priority: Int,
		name: String
	): DataSource<D, T>

	fun createMetaDataNode(): Node

	val name: String

	val resolution: DoubleArray

	val translation: DoubleArray

	fun updateTransform(resolution: DoubleArray, translation: DoubleArray) {
		val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, translation)
		updateTransform(newTransform)
	}

	fun updateTransform(transform: AffineTransform3D)

    fun shutdown() { }

}
