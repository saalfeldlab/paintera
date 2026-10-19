package org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace

import bdv.viewer.Source
import bdv.viewer.SourceAndConverter
import javafx.beans.value.ObservableValue
import net.imglib2.converter.Converter
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.RealType

/** Implemented by a [SourceAndConverter] the renderer draws through [filters]; [filters] holds immutable lists, replaced whole. */
interface RenderFilters {

	val filters: ObservableValue<List<RenderFilter>>
}

/** The filters read a sample as a real number and color the result with [converter], which must only read its sample as one too */
class FilteredSourceAndConverter<T : RealType<T>>(
	source: Source<T>,
	converter: Converter<T, ARGBType>,
	override val filters: ObservableValue<List<RenderFilter>>
) : SourceAndConverter<T>(source, converter), RenderFilters
