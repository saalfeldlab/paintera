package org.janelia.saalfeldlab.paintera.ai

import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.samlink.encode.SamEncoder

interface ImageEmbeddingRequester<out V> : AutoCloseable where V : EncoderResult {

    companion object {
        val embeddingIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName("EMBEDDING_IO")
    }

    val scope : CoroutineScope

    val imageSize: Int

    suspend fun getImageEmbedding(it: RenderUnitState): V

    fun healthCheck() : Boolean

    fun requestSessionId(): String

    fun cancelPendingRequests(vararg ids: String)
}

interface SamLinkEmbeddingRequester<out V : EncoderResult> : ImageEmbeddingRequester<V> {

    val samLink: SamEncoder<*>

    override fun healthCheck() = samLink.isReady()
    override fun close() = samLink.close()
}


