package org.janelia.saalfeldlab.paintera.config.sam

import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin


class Sam2Config : SamTritonConfig<Sam2Config>(DEFAULT_ENCODER_NAME) {

    override fun getValue() = this

    companion object {
        internal const val DEFAULT_ENCODER_NAME = "sam2.1_large_encoder"
    }
}

internal class Sam2ConfigNode(
    config: SamTritonConfig<Sam2Config>
) : SamTritonConfigNode(config)


@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class Sam2Adapter : SamTritonAdapter<Sam2Config>() {
    override fun newInstance() = Sam2Config()
    override fun getTargetClass() = Sam2Config::class.java
}