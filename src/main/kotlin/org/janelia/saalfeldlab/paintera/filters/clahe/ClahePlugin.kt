package org.janelia.saalfeldlab.paintera.filters.clahe

import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.paintera.filters.RenderFilterConfig
import org.janelia.saalfeldlab.paintera.filters.RenderFilterModel
import org.janelia.saalfeldlab.paintera.filters.RenderFilterPlugin
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.scijava.Priority
import org.scijava.plugin.Plugin

@Plugin(type = RenderFilterPlugin::class, priority = Priority.HIGH - 1)
class ClahePlugin : RenderFilterPlugin {

	override fun newConfig() = ClaheConfig()

	override fun newModel(config: RenderFilterConfig, metadataState: MetadataState?, intensityRange: ObservableValue<ClosedFloatingPointRange<Double>>) = ClaheModel(config as ClaheConfig, intensityRange)

	override fun newUI(model: RenderFilterModel) = ClaheUI(model as ClaheModel)
}
