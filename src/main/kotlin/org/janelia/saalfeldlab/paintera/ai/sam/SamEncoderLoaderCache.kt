package org.janelia.saalfeldlab.paintera.ai.sam

import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.ai.ImageEncodingLoaderCache
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam2EncoderResult
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerEncoderResult


class Sam1EncodingLoaderCache : ImageEncodingLoaderCache<Sam1EncoderResult>(), AutoCloseable {

    override val embeddingRequester by LazyForeignValue(paintera.properties.samServiceConfig::sam1Config) {
        Sam1EncodeRequester()
    }

}

class Sam2EncodingLoaderCache : ImageEncodingLoaderCache<Sam2EncoderResult>(), AutoCloseable {

    override val embeddingRequester by LazyForeignValue(paintera.properties.samServiceConfig::sam2Config) {
        Sam2EncodeRequester()
    }

}

class Sam3EncodingLoaderCache : ImageEncodingLoaderCache<Sam3TrackerEncoderResult>() {

    override val embeddingRequester by LazyForeignValue(paintera.properties.samServiceConfig::sam3Config) {
        Sam3EncodeRequester()
    }
}