package org.janelia.saalfeldlab.paintera.ai

import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState

interface SamEncodeRequester<R> : AutoCloseable where R : EncoderResult {

    companion object {
        val embeddingIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName("EMBEDDING_IO")
    }

    val scope : CoroutineScope

    val imageSize: Int

    suspend fun getImageEmbedding(it: RenderUnitState): R

    suspend fun healthCheck() : Boolean

    suspend fun requestSessionId(): String

    fun cancelPendingRequests(vararg ids: String)
}


