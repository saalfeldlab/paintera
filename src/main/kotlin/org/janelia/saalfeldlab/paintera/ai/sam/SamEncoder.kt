package org.janelia.saalfeldlab.paintera.ai.sam

import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult
import org.janelia.saalfeldlab.samlink.models.Sam1Model
import org.janelia.saalfeldlab.samlink.encode.triton.Sam1TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam2EncoderResult
import org.janelia.saalfeldlab.samlink.models.Sam2Model
import org.janelia.saalfeldlab.samlink.models.Sam3TrackerModel
import org.janelia.saalfeldlab.samlink.encode.triton.Sam2TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerEncoderResult
import org.janelia.saalfeldlab.samlink.encode.triton.Sam3TrackerTritonEncoder

class Sam1EncodeRequester : SamLinkEncodeRequester<Sam1EncoderResult>() {

    override val imageSize = Sam1Model.Encoder.INPUT_EDGE_SIZE.toInt()
    override val samLink = with(paintera.properties.samServiceConfig.sam1Config) {
        Sam1TritonEncoder(host, port, encoderName, responseTimeout)
    }
}

class Sam2EncodeRequester : SamLinkEncodeRequester<Sam2EncoderResult>() {

    override val imageSize = Sam2Model.Encoder.INPUT_EDGE_SIZE.toInt()
    override val samLink = with(paintera.properties.samServiceConfig.sam2Config) {
        Sam2TritonEncoder(host, port, encoderName, responseTimeout)
    }
}

class Sam3EncodeRequester : SamLinkEncodeRequester<Sam3TrackerEncoderResult>() {

    override val imageSize = Sam3TrackerModel.Encoder.INPUT_EDGE_SIZE.toInt()
    override val samLink = with(paintera.properties.samServiceConfig.sam3Config) {
        Sam3TrackerTritonEncoder(host, port, encoderName, responseTimeout)
    }
}