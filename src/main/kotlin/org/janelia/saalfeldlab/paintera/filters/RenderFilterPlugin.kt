package org.janelia.saalfeldlab.paintera.filters

import javafx.beans.value.ObservableValue
import javafx.scene.Node
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.scijava.plugin.SciJavaPlugin

/**
 * Adds a screen-space filter to the Render Filters of a raw source; implement it with `@Plugin(type = RenderFilterPlugin::class)`,
 * and its [priority][org.scijava.plugin.Plugin.priority] orders the filters, highest first.
 *
 * A config is saved by its own `PainteraSerialization.PainteraAdapter`.
 */
interface RenderFilterPlugin : SciJavaPlugin {

	/** the name of the filter's entry in a saved project */
	val typeKey: String
		get() = newConfig().typeKey

	fun newConfig(): RenderFilterConfig

	/**
	 * @param metadataState the physical resolution and unit of the source, if it has them
	 * @param intensityRange the source's min and max intensity as set in its converter
	 */
	fun newModel(config: RenderFilterConfig, metadataState: MetadataState?, intensityRange: ObservableValue<ClosedFloatingPointRange<Double>>): RenderFilterModel

	fun newUI(model: RenderFilterModel): Node
}
