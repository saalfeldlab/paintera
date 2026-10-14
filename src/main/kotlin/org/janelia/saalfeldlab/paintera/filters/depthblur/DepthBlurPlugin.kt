package org.janelia.saalfeldlab.paintera.filters.depthblur

import javafx.beans.value.ObservableValue
import org.janelia.saalfeldlab.paintera.filters.RenderFilterConfig
import org.janelia.saalfeldlab.paintera.filters.RenderFilterModel
import org.janelia.saalfeldlab.paintera.filters.RenderFilterPlugin
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.scijava.Priority
import org.scijava.plugin.Plugin

@Plugin(type = RenderFilterPlugin::class, priority = Priority.HIGH)
class DepthBlurPlugin : RenderFilterPlugin {

	override fun newConfig() = DepthBlurConfig()

	override fun newModel(config: RenderFilterConfig, metadataState: MetadataState?, intensityRange: ObservableValue<ClosedFloatingPointRange<Double>>) = DepthBlurConfigModel(config as DepthBlurConfig, metadataState)

	override fun newUI(model: RenderFilterModel) = DepthBlurUI(model as DepthBlurConfigModel)
}
