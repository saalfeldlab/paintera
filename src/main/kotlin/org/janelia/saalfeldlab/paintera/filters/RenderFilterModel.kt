package org.janelia.saalfeldlab.paintera.filters

import javafx.beans.property.BooleanProperty
import javafx.beans.property.ReadOnlyObjectProperty
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilter

/** What a filter's settings panel reads from its model */
interface RenderFilterSettings {

	val enabledProperty: BooleanProperty

	/** sets every setting but [enabledProperty] to its default */
	fun reset()
}

/** Binds a [config] to its filter; [filterProperty] is `null` while the entry is disabled */
interface RenderFilterModel {

	val config: RenderFilterConfig
	val filterProperty: ReadOnlyObjectProperty<out RenderFilter?>
}
