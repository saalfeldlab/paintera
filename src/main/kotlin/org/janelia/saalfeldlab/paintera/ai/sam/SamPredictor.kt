package org.janelia.saalfeldlab.paintera.ai.sam

import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.samlink.decode.DecoderResult
import org.janelia.saalfeldlab.samlink.decode.SamDecoder
import org.janelia.saalfeldlab.samlink.decode.SamDecoder.Companion.newDecoder
import org.janelia.saalfeldlab.samlink.decode.SamPrompt
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import org.janelia.saalfeldlab.util.*

const val MAX_DIM_TARGET = 1024

private var cachedDecoder: SamDecoder<*>? = null
private fun cachedOrNewDecoder(encodedImage: EncoderResult): SamDecoder<*> {

    val decoder = cachedDecoder
        ?.takeIf { SamDecoder.matches(it, encodedImage) }
        ?: newDecoder(encodedImage)
    cachedDecoder = decoder
    return decoder
}


class SamPredictor(
    val encodeResult: EncoderResult,
    val decoder: SamDecoder<*> = cachedOrNewDecoder(encodeResult)
) {

    lateinit var lastPrediction: SamPrediction
    lateinit var result: RandomAccessibleInterval<NativeType<*>>

    fun predict(prompt: SamPrompt): SamPrediction {

        synchronized(this) {
            val result = SamDecoder.decode(decoder, encodeResult, prompt)
            /* TODO: Consider exposing this, or choosing a different default.
            *   1 mask-based refinement seems reasonable, and to work well. */
            var refinePrompt: SamPrompt
            var refinedResult = result
            repeat(1) {
                refinePrompt = prompt.copy().addMask(result.bestMask)
                refinedResult = SamDecoder.decode(decoder, encodeResult, refinePrompt)
            }
            return SamPrediction(encodeResult, refinedResult, prompt).also {
                lastPrediction = it
            }
        }
    }

    data class SamPrediction(
        val encodeResult: EncoderResult,
        val decodeResult: DecoderResult,
        val prompt: SamPrompt
    ) {

        /**
         * Return the decoded result as a RandomAccessibleInterval cropped to just the content region,
         * in the decoded output space. Post processing will need to map this to the original content space
         * with some scale factor based on [encoderResult.sourceWidth] / [@return.dimension(0)]
         *
         * @param logits to converter to RandomAccessibleInterval. By default [DecoderResult.bestMask] but can be provided.
         * @return the RandomAccessibleInterval.
         */
        fun raiFromResult(logits: FloatArray = decodeResult.bestMask) : RandomAccessibleInterval<FloatType> {
            val decodedImg = ArrayImgs.floats(
                logits,
                decodeResult.maskSize.toLong(),
                decodeResult.maskSize.toLong(),
            )
            val scale = encodeResult.inputSize.toDouble() / decodeResult.maskSize
            val croppedWidth = (encodeResult.scaledWidth / scale).toLong()
                .coerceAtMost(decodeResult.maskSize.toLong())
            val croppedHeight = (encodeResult.scaledHeight / scale).toLong()
                .coerceAtMost(decodeResult.maskSize.toLong())
            val decoderContentInterval = Intervals.createMinSize(0, 0, croppedWidth, croppedHeight)
            return decodedImg
                .extendBorder()
                .interval(decoderContentInterval)
        }
    }
}