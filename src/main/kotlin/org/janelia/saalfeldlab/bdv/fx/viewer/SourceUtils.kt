package org.janelia.saalfeldlab.bdv.fx.viewer

import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import net.imglib2.converter.Converter
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilters
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterVolatileType

object SourceUtils {

	/** A [SourceAndConverter] over the [DataSource]'s non-volatile `D` view, or [sourceAndConverter] itself if it is not over a [DataSource] */
	@JvmStatic
	@Suppress("UNCHECKED_CAST")
	fun nonVolatileSourceAndConverter(sourceAndConverter: SourceAndConverter<*>): SourceAndConverter<*> {
		val dataSource = sourceAndConverter.spimSource as? DataSource<*, *> ?: return sourceAndConverter
		val converter = sourceAndConverter.converter.let { (it as? HighlightingStreamConverterVolatileType<*, *>)?.nonVolatileConverter ?: it }
		val source = dataSource.dataSource as Source<Any>
		val nonVolatileConverter = converter as Converter<Any, ARGBType>

		/* the filters of a filtered source read its data the same way */
		val filtered = sourceAndConverter as? RenderFilters
			?: return SourceAndConverter(source, nonVolatileConverter)
		return NonVolatileFilteredSourceAndConverter(source, nonVolatileConverter, filtered)
	}
}

private class NonVolatileFilteredSourceAndConverter<D>(source: Source<D>, converter: Converter<D, ARGBType>, filtered: RenderFilters) :
	SourceAndConverter<D>(source, converter), RenderFilters by filtered
