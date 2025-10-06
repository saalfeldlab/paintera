package org.janelia.saalfeldlab.bdv.fx.viewer

import bdv.viewer.Interpolation
import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import net.imglib2.converter.Converter
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterVolatileLabelMultisetType
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterVolatileType

//FIXME Caleb: These are both private because imho this is a bit of a hack.
// We want Paintera's DataSource to implement Source over the volatile type,
// and the corresponding SourceAndConverter has a converter also over the volatile type
// This makes it not possible get a non-volatile SourceAndConverter over the DataSource.
// So what we do here is manually unwrap, but we still want it to play nicely with the
// volatile renderer, so we need it to "equal" it's volatile version.
// I think SourceAndConverter has a `volatileConverter` that we should instead be using
// when we want volatile (most the time) and support a proper non-volatile version without
// wrapping/unwrapping this way.
private class UnwrappedDataSource<D>(val dataSource : DataSource<D, *>) : Source<D> {
	override fun isPresent(t: Int) = dataSource.isPresent(t)

	override fun getSource(t: Int, level: Int) = dataSource.getDataSource(t, level)

	override fun getInterpolatedSource(t: Int, level: Int, method: Interpolation?) = dataSource.getInterpolatedDataSource(t, level, method)

	override fun getSourceTransform(t: Int, level: Int, transform: AffineTransform3D?) {
		dataSource.getSourceTransform(t, level, transform)
	}

	override fun getType() = dataSource.dataType

	override fun getName() = dataSource.name

	override fun getVoxelDimensions() = dataSource.voxelDimensions

	override fun getNumMipmapLevels() = dataSource.numMipmapLevels

	override fun hashCode() = dataSource.hashCode()

	override fun equals(other: Any?) = dataSource == ((other as? UnwrappedDataSource<*>)?.dataSource ?: other)
}


private class WrappedSourceAndConverter<D>(val sourceAndConverter : SourceAndConverter<*>, source : Source<D>, converter : Converter<D, ARGBType>) : SourceAndConverter<D>(source, converter) {

	override fun equals(other: Any?): Boolean {
		return sourceAndConverter == ((other as? WrappedSourceAndConverter<*>)?.sourceAndConverter ?: other)
	}

	override fun hashCode(): Int {
		return sourceAndConverter.hashCode()
	}
}

internal fun <D : Any> getDataSourceAndConverter(sourceAndConverter: SourceAndConverter<*>): SourceAndConverter<*> {
	val data = sourceAndConverter.spimSource as? DataSource<D, *> ?: return sourceAndConverter
	val unwrappedDataSource = UnwrappedDataSource(data)

	val converter = sourceAndConverter.converter.let {
		(it as? HighlightingStreamConverterVolatileType<*, *>)?.nonVolatileConverter ?: it
	} as Converter<D, ARGBType>

	return WrappedSourceAndConverter(sourceAndConverter, unwrappedDataSource, converter)
}