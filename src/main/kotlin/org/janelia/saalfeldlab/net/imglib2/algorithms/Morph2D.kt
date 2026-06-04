package org.janelia.saalfeldlab.net.imglib2.algorithms

import kotlin.math.max
import kotlin.math.min

/**
 * Grayscale morphology over flat rectangular structuring elements. Every operation reads from `input` and
 * writes to `output`; passing the same array for both performs the operation in place (the core rank
 * filter buffers its result, so aliasing is safe).
 *
 * `size` is the image `(width, height)`; `kernel` is the structuring element `(width, height)`, a centered
 * box in which every cell participates.
 */
class Morph2D(
    val input: FloatArray,
    val output: FloatArray,
    val size: Pair<Int, Int>
) {

    var kernel: Pair<Int, Int> = THREE_BY_THREE

    fun erosion(kernel: Pair<Int, Int> = this.kernel) = apply {
        erosion(input, output, size, kernel)
    }

    fun dilation(kernel: Pair<Int, Int> = this.kernel) = apply {
        dilation(input, output, size, kernel)
    }

    fun opening(kernel: Pair<Int, Int> = this.kernel) = apply {
        erosion(input, output, size, kernel)
        dilation(output, output, size, kernel)
    }

    fun closing(kernel: Pair<Int, Int> = this.kernel) = apply {
        dilation(input, output, size, kernel)
        erosion(output, output, size, kernel)
    }

    fun openClose(kernel: Pair<Int, Int> = this.kernel) = apply {
        val opened = Morph2D(input, output, size).opening(kernel).output
        Morph2D(opened, output, size).closing(kernel)
    }

    fun closeOpen(kernel: Pair<Int, Int> = this.kernel) = apply {
        val closed = Morph2D(input, output, size).closing(kernel).output
        Morph2D(closed, output, size).opening(kernel)
    }

    fun centre(kernel: Pair<Int, Int> = this.kernel) = apply {
        centre(input, output, size, kernel)
    }
}

fun erosion(input: FloatArray, output: FloatArray, size: Pair<Int, Int>, kernel: Pair<Int, Int>) =
    rankFilter(input, output, size, kernel, Float.POSITIVE_INFINITY) { a, b -> min(a, b) }

fun dilation(input: FloatArray, output: FloatArray, size: Pair<Int, Int>, kernel: Pair<Int, Int>) =
    rankFilter(input, output, size, kernel, Float.NEGATIVE_INFINITY) { a, b -> max(a, b) }

fun opening(input: FloatArray, output: FloatArray, size: Pair<Int, Int>, kernel: Pair<Int, Int>) {
    erosion(input, output, size, kernel)
    dilation(output, output, size, kernel)
}

fun closing(input: FloatArray, output: FloatArray, size: Pair<Int, Int>, kernel: Pair<Int, Int>) {
    dilation(input, output, size, kernel)
    erosion(output, output, size, kernel)
}

/**
 * Morphological centre operator: for each pixel, the median of the triple formed by the original
 * image, its open-close filter, and its close-open filter.
 * This helps remove noisy small bright and small dark features
 */
fun centre(input: FloatArray, output: FloatArray, size: Pair<Int, Int>, kernel: Pair<Int, Int>) {
    require(input.size == output.size) { "input and output must be the same size" }
    val openClose = input.copyOf().also {
        opening(it, it, size, kernel)
        closing(it, it, size, kernel)
    }
    val closeOpen = input.copyOf().also {
        closing(it, it, size, kernel)
        opening(it, it, size, kernel)
    }
    for (i in input.indices) {
        output[i] = medianOfThree(input[i], openClose[i], closeOpen[i])
    }
}

private fun medianOfThree(a: Float, b: Float, c: Float): Float = max(min(a, b), min(max(a, b), c))

/**
 * Common rank filter logic with a rectangular structuring element [kernel].
 * Neighbor coordinates are clamped to the image border.
 * [input] and [output] may be the same array for inplace operations
 */
private inline fun rankFilter(
    input: FloatArray,
    output: FloatArray,
    size: Pair<Int, Int>,
    kernel: Pair<Int, Int>,
    seed: Float,
    operation: (Float, Float) -> Float
) {
    require(input.size == output.size) { "input and output must be the same size" }
    val (width, height) = size
    require(input.size == width * height) { "input size (${input.size}) does not match $width x $height" }
    val (kernelWidth, kernelHeight) = kernel
    require(kernelWidth > 0 && kernelHeight > 0) { "kernel dimensions must be positive; got $kernelWidth x $kernelHeight" }

    val centerX = kernelWidth / 2
    val centerY = kernelHeight / 2
    val result = FloatArray(input.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var extremum = seed
            for (ky in 0 until kernelHeight) {
                for (kx in 0 until kernelWidth) {
                    /* clamp neighbor coordinates to the image border */
                    val sampleX = (x + kx - centerX).coerceIn(0, width - 1)
                    val sampleY = (y + ky - centerY).coerceIn(0, height - 1)
                    extremum = operation(extremum, input[sampleY * width + sampleX])
                }
            }
            result[y * width + x] = extremum
        }
    }
    result.copyInto(output)
}

private val THREE_BY_THREE = 3 to 3
