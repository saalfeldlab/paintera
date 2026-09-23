package org.janelia.saalfeldlab.paintera.ai.sam

import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam1TritonOptions
import org.janelia.saalfeldlab.samlink.models.Sam1Model
import org.janelia.saalfeldlab.samlink.encode.triton.Sam1TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam2EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam2TritonOptions
import org.janelia.saalfeldlab.samlink.models.Sam2Model
import org.janelia.saalfeldlab.samlink.models.Sam3TrackerModel
import org.janelia.saalfeldlab.samlink.encode.triton.Sam2TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerEncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerTritonOptions
import org.janelia.saalfeldlab.samlink.encode.triton.Sam3TrackerTritonEncoder

class Sam1EncodeRequester : SamLinkEncodeRequester<Sam1EncoderResult, Sam1TritonOptions>() {

    private val config
        get() = paintera.properties.samServiceConfig.sam1Config
    override val imageSize = Sam1Model.Encoder.INPUT_EDGE_SIZE.toInt()
    override val samLink = with(config) {
        Sam1TritonEncoder(host, port, encoderName, responseTimeout)
    }

    override fun createOptions() = Sam1TritonOptions(imageEncoding = config.imageEncoding)
}

class Sam2EncodeRequester : SamLinkEncodeRequester<Sam2EncoderResult, Sam2TritonOptions>() {

    private val config
        get() = paintera.properties.samServiceConfig.sam2Config
    override val imageSize = Sam2Model.Encoder.INPUT_EDGE_SIZE.toInt()
    override val samLink = with(config) {
        Sam2TritonEncoder(host, port, encoderName, responseTimeout)
    }

    override fun createOptions() = Sam2TritonOptions(imageEncoding = config.imageEncoding)
}

class Sam3EncodeRequester : SamLinkEncodeRequester<Sam3TrackerEncoderResult, Sam3TrackerTritonOptions>() {

    private val config
        get() = paintera.properties.samServiceConfig.sam3Config
    override val imageSize = Sam3TrackerModel.Encoder.INPUT_EDGE_SIZE.toInt()
    override val samLink = with(config) {
        Sam3TrackerTritonEncoder(host, port, encoderName, responseTimeout)
    }

    override fun createOptions() = Sam3TrackerTritonOptions(imageEncoding = config.imageEncoding)
}
