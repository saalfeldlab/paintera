package org.janelia.saalfeldlab.paintera.ai.sam

import org.janelia.saalfeldlab.samlink.decode.SamDecoder
import org.janelia.saalfeldlab.samlink.decode.SamDecoder.Companion.newDecoder
import org.janelia.saalfeldlab.samlink.decode.SamPrompt
import org.janelia.saalfeldlab.samlink.encode.EncoderResult
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory
import net.imglib2.realtransform.RealViews
import net.imglib2.realtransform.Scale2D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.scale
import org.janelia.saalfeldlab.util.affineReal
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.interpolate
import org.janelia.saalfeldlab.util.interpolateNearestNeighbor
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.raster

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
            val predictionImg = getRaiForResult(result, encodedImage)
            return SamPrediction(result, predictionImg, prompt).also {
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
            val rawMask = result.rawMask
            SamDecoder.grayscaleClosing(
                rawMask,
                result.maskSize to result.maskSize,
                1 to 1
            )
            SamDecoder.grayscaleOpening(
                rawMask,
                result.maskSize to result.maskSize,
                1 to 1
            )
            val decodedImg = ArrayImgs.floats(rawMask, result.maskSize.toLong(), result.maskSize.toLong())

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
                    .extendValue(0.0)
                    .interpolate(NLinearInterpolatorFactory())
                    .affineReal(Scale2D(scale, scale))
                    .raster()
                    .interval(imageSize)
            }

            return croppedImg
        }
    }
}