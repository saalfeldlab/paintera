package org.janelia.saalfeldlab.paintera.ai

import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState
import org.janelia.saalfeldlab.samlink.encode.TritonEncodeOptions
import java.awt.image.BufferedImage

enum class EncodePriority(val level: Long) {
    IMMEDIATE(1),
    EAGER(2)
}

interface SamEncodeRequester<R, O> : AutoCloseable where R : EncoderResult, O : TritonEncodeOptions {

    val imageSize: Int

    /** render [state] at the encoder's input size */
    suspend fun renderImage(state: RenderUnitState): BufferedImage

    suspend fun encode(image: BufferedImage, withOptions: (O.() -> Unit)? = null): R

    suspend fun healthCheck() : Boolean

    suspend fun requestSessionId(): String
}


