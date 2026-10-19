package org.janelia.saalfeldlab.paintera.filters

import javafx.beans.property.BooleanProperty

/** The user's settings for one entry of [RenderFiltersConfig] */
interface RenderFilterConfig {

	val name: String
	val description: String
	/** the entry's name in a saved project */
	val typeKey: String
	val enabledProperty: BooleanProperty

	/** sets every setting from [that], which must be the same type */
	fun set(that: RenderFilterConfig)
}
