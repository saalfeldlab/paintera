package org.janelia.saalfeldlab.paintera.config.sam

import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin


class Sam1Config : SamTritonConfig<Sam1Config>(DEFAULT_ENCODER_NAME) {

    override fun getValue() = this

    companion object {
        internal const val DEFAULT_ENCODER_NAME = "sam1_encoder"
    }
}

internal class Sam1ConfigNode(
    config: SamTritonConfig<Sam1Config>
) : SamTritonConfigNode(config)


@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class Sam1Adapter : SamTritonAdapter<Sam1Config>() {
    override fun newInstance() = Sam1Config()
    override fun getTargetClass() = Sam1Config::class.java
}