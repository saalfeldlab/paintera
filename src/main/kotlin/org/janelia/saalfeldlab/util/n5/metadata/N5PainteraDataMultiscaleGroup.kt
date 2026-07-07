package org.janelia.saalfeldlab.util.n5.metadata

import net.imglib2.realtransform.AffineGet
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisMetadata

/**
 * A Paintera group whose scale levels live in a child `data` group; the spatial transforms and axes all delegate
 * to that [dataGroupMetadata].
 */
abstract class N5PainteraDataMultiscaleGroup(
	basePath: String,
	val dataGroupMetadata: N5PainteraDataMultiscaleMetadata
) : SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>(basePath, dataGroupMetadata.childrenMetadata), AxisMetadata {

	override fun spatialTransform(): AffineGet = dataGroupMetadata.spatialTransform()

	override fun unit(): String = dataGroupMetadata.unit()

	override fun spatialTransform3d(): AffineTransform3D = dataGroupMetadata.spatialTransform3d()

	override fun spatialTransforms(): Array<out AffineGet> = dataGroupMetadata.spatialTransforms3d()

	override fun spatialTransforms3d(): Array<AffineTransform3D> = dataGroupMetadata.spatialTransforms3d()

	override fun getAxes(): Array<Axis> = dataGroupMetadata.childrenMetadata[0].axes
}
