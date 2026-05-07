package org.janelia.saalfeldlab.paintera.config.sam

import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin

class Sam3Config : SamTritonConfig<Sam3Config>(DEFAULT_ENCODER_NAME) {

    override fun getValue() = this

    companion object {
        internal const val DEFAULT_ENCODER_NAME = "sam3_tracker_vision_encoder_fp16"
    }
}

internal class Sam3ConfigNode(
    config: SamTritonConfig<Sam3Config>
) : SamTritonConfigNode(config)


@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class Sam3Adapter : SamTritonAdapter<Sam3Config>() {
    override fun newInstance() = Sam3Config()
    override fun getTargetClass() = Sam3Config::class.java
}