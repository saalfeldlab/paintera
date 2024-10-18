package org.janelia.saalfeldlab.bdv.fx.viewer

import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import net.imglib2.converter.Converter
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterVolatileLabelMultisetType

interface CompositeSourceSupplier {
	fun getCompositeSource() : Source<*>
}

internal fun <D : Any> getDataSourceAndConverter(sourceAndConverter: SourceAndConverter<*>): SourceAndConverter<*> {
	val data = sourceAndConverter.spimSource as? DataSource<D, *> ?: return  sourceAndConverter
	val dataSource = object : Source<D> {
		override fun isPresent(t: Int) = data.isPresent(t)

		override fun getSource(t: Int, level: Int) = data.getDataSource(t, level)

		override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation?) = data.getInterpolatedDataSource(t, level, method)

		override fun getSourceTransform(t: Int, level: Int, transform: AffineTransform3D?) {
			data.getSourceTransform(t, level, transform)
		}

		override fun getType() = data.dataType

		override fun getName() = sourceAndConverter.spimSource.name

		override fun getVoxelDimensions() = data.voxelDimensions

		override fun getNumMipmapLevels() = data.numMipmapLevels
	}

	val converter = sourceAndConverter.converter.let {
		(it as? HighlightingStreamConverterVolatileLabelMultisetType)?.nonVolatileConverter ?: it
	} as Converter<D, ARGBType>

	return object : SourceAndConverter<D>(dataSource, converter), CompositeSourceSupplier {
		override fun getCompositeSource(): Source<*> {
			return sourceAndConverter.spimSource
		}
	}

}