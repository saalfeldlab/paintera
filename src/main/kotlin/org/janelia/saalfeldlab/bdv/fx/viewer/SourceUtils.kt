package org.janelia.saalfeldlab.bdv.fx.viewer

import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import net.imglib2.converter.Converter
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterVolatileType

object SourceUtils {

	/** A [SourceAndConverter] over the [DataSource]'s non-volatile `D` view, or [sourceAndConverter] itself if it is not over a [DataSource] */
	@JvmStatic
	@Suppress("UNCHECKED_CAST")
	fun nonVolatileSourceAndConverter(sourceAndConverter: SourceAndConverter<*>): SourceAndConverter<*> {
		val dataSource = sourceAndConverter.spimSource as? DataSource<*, *> ?: return sourceAndConverter
		val converter = sourceAndConverter.converter.let { (it as? HighlightingStreamConverterVolatileType<*, *>)?.nonVolatileConverter ?: it }
		return SourceAndConverter(dataSource.dataSource as Source<Any>, converter as Converter<Any, ARGBType>)
	}
}
