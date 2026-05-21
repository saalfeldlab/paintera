package org.janelia.saalfeldlab.paintera.ai.sam

import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.realtransform.Scale2D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
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
    val encodedImage: EncoderResult,
    val decoder: SamDecoder<*> = cachedOrNewDecoder(encodedImage)
) {

    lateinit var lastPrediction: SamPrediction
    lateinit var result: RandomAccessibleInterval<NativeType<*>>

    fun predict(prompt: SamPrompt): SamPrediction {

        synchronized(this) {
            val result = SamDecoder.decode(decoder, encodedImage, prompt)
            /* TODO: Consider exposing this, or choosing a different default.
            *   1 mask-based refinement seems reasonable, and to work well. */
            var refinePrompt: SamPrompt
            var refinedResult = result
            repeat(1) {
                refinePrompt = prompt.copy().addMask(result.bestMask)
                refinedResult = SamDecoder.decode(decoder, encodedImage, refinePrompt)
            }
            val predictionImg = getRaiForResult(refinedResult, encodedImage)
            return SamPrediction(refinedResult, predictionImg, prompt).also {
                lastPrediction = it
            }
        }
    }

    data class SamPrediction(
        val result: SamDecoder.DecoderResult,
        val image: RandomAccessibleInterval<FloatType>,
        val prompt: SamPrompt
    )

    companion object {

        private fun getRaiForResult(
            result: SamDecoder.DecoderResult,
            encoderResult: EncoderResult
        ): RandomAccessibleInterval<FloatType> {

            val scale = encoderResult.inputSize.toDouble() / result.maskSize
            val decodedImg = ArrayImgs.floats(result.bestMask, result.maskSize.toLong(), result.maskSize.toLong())

            val imageSize = Intervals.createMinSize(
                0,
                0,
                encoderResult.imageWidth.toLong(),
                encoderResult.imageHeight.toLong()
            )
            val croppedImg = if (scale == 1.0 && encoderResult.imageWidth <= result.maskSize && encoderResult.imageHeight <= result.maskSize) {
                /* if we don't need scaling, and we can crop without getting out of bounds, just crop; avoids real transforms */
                decodedImg.interval(imageSize)
            } else {
                decodedImg
                    .extendBorder()
                    .interpolate(NLinearInterpolatorFactory())
                    .affineReal(Scale2D(scale, scale))
                    .raster()
                    .interval(imageSize)
            }

            return croppedImg
        }
    }
}