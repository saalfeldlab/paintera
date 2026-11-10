package org.janelia.saalfeldlab.paintera.filters

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Node
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilter

/** Binds the entries of a [RenderFiltersConfig] to their filters, applied in the config's order */
class RenderFiltersModel(val config: RenderFiltersConfig, entries: List<Entry>) {

	/** a filter's [model], and the settings node built from it */
	class Entry(val model: RenderFilterModel, val settingsNode: () -> Node)

	private val entriesByConfig = entries.associateBy { it.model.config }

	private val filters = SimpleObjectProperty<List<RenderFilter>>(emptyList())
	val filtersProperty: ReadOnlyObjectProperty<List<RenderFilter>> = filters

	init {
		config.filterConfigs.subscribe { update() }
		entries.forEach { entry -> entry.model.filterProperty.subscribe { _ -> update() } }
		update()
	}

	fun settingsNode(filterConfig: RenderFilterConfig) = entriesByConfig.getValue(filterConfig).settingsNode()

	private fun update() {
		filters.set(config.filterConfigs.mapNotNull { entriesByConfig.getValue(it).model.filterProperty.value })
	}
}
