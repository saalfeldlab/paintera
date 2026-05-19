package org.janelia.saalfeldlab.paintera.ai.sam

import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam1TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam2EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam2TritonEncoder
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerEncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerTritonEncoder

class Sam1EncodeRequester : SamLinkEncodeRequester<Sam1EncoderResult>() {

    override val imageSize = 1024

    override val samLink = with(paintera.properties.samServiceConfig.sam1Config) {
        Sam1TritonEncoder(
            serviceHost = host,
            grpcPort = port,
            encoderModel = encoderName,
            responseTimeout = responseTimeout
        )
    }
}

class Sam2EncodeRequester : SamLinkEncodeRequester<Sam2EncoderResult>() {

    override val imageSize = 1024

    override val samLink = with(paintera.properties.samServiceConfig.sam2Config) {
        Sam2TritonEncoder(
            serviceHost = host,
            grpcPort = port,
            encoderModel = encoderName,
            responseTimeout = responseTimeout
        )
    }
}

class Sam3EncodeRequester : SamLinkEncodeRequester<Sam3TrackerEncoderResult>() {

    override val imageSize = 1008

    override val samLink = with(paintera.properties.samServiceConfig.sam3Config) {
        Sam3TrackerTritonEncoder(
            serviceHost = host,
            grpcPort = port,
            encoderModel = encoderName,
            responseTimeout = responseTimeout
        )
    }
}