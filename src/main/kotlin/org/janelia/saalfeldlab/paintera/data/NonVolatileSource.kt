package org.janelia.saalfeldlab.paintera.data

import bdv.viewer.Interpolation
import bdv.viewer.Source
import net.imglib2.realtransform.AffineTransform3D

/** The non-volatile data view of a [DataSource] */
internal class NonVolatileSource<D>(val dataSource: DataSource<D, *>) : Source<D> {

	override fun isPresent(t: Int) = dataSource.isPresent(t)
	override fun getSource(t: Int, level: Int) = dataSource.getDataSource(t, level)
	override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation?) = dataSource.getInterpolatedDataSource(t, level, method)
	override fun getInterpolatedMaskedSource(t: Int, level: Int, method: Interpolation?) = dataSource.getInterpolatedMaskedDataSource(t, level, method)
	override fun getSourceTransform(t: Int, level: Int, transform: AffineTransform3D?) = dataSource.getSourceTransform(t, level, transform)
	override fun getType(): D = dataSource.dataType
	override fun getName(): String = dataSource.name
	override fun getVoxelDimensions() = dataSource.voxelDimensions
	override fun getNumMipmapLevels() = dataSource.numMipmapLevels
}
