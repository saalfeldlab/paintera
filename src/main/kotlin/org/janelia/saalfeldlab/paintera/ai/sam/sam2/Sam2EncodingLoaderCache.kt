package org.janelia.saalfeldlab.paintera.ai.sam.sam2

import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.ai.ImageEncodingLoaderCache
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.samlink.encode.Sam2EncoderResult

class Sam2EncodingLoaderCache : ImageEncodingLoaderCache<Sam2EncoderResult>(), AutoCloseable {

    override val embeddingRequester
            by LazyForeignValue(paintera.properties.samServiceConfig::sam2Config) {
                Sam2EmbeddingRequester()
            }

}

