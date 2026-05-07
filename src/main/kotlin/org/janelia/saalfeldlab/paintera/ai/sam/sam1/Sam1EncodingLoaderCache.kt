package org.janelia.saalfeldlab.paintera.ai.sam.sam1

import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.ai.ImageEncodingLoaderCache
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.samlink.encode.Sam1EncoderResult

class Sam1EncodingLoaderCache : ImageEncodingLoaderCache<Sam1EncoderResult>() {

    override val embeddingRequester
            by LazyForeignValue(paintera.properties.samServiceConfig::sam1Config) {
                Sam1EmbeddingRequester()
            }
}

