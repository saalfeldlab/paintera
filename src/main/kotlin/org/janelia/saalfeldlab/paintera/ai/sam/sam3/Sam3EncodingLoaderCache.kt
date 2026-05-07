package org.janelia.saalfeldlab.paintera.ai.sam.sam3

import kotlinx.coroutines.Deferred
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.paintera.ai.ImageEncodingLoaderCache
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.samlink.encode.Sam3TrackerEncoderResult

class Sam3EncodingLoaderCache : ImageEncodingLoaderCache<Sam3TrackerEncoderResult>() {

    override val embeddingRequester by LazyForeignValue(paintera.properties.samServiceConfig::sam3Config) {
        Sam3EmbeddingRequester()
    }
}

