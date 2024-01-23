
package org.janelia.saalfeldlab.paintera.util.algorithms

import kotlin.math.pow

/**
 * Otsu threshold prediction, adapted for kotlin from:
 * [ComputeOtsuThreshold.java](https://github.com/imagej/imagej-ops/blob/master/src/main/java/net/imagej/ops/threshold/otsu/ComputeOtsuThreshold.java)
 *
 * @param histogram array of bins, whose values are frequensies at that bin
 * @return index of the bin to use for threshold
 *
 * @author Caleb Hulbert
 * @author Barry DeZonia
 * @author Gabriel Landini
 */
fun otsuThresholdPrediction(histogram: LongArray): Long {

	val (histogramIntensity, numPoints) = histogram.foldIndexed(LongArray(2)) { idx, acc, freq ->
		acc[0] += idx * freq
		acc[1] += freq
		acc
	}

	var intensitySumBelowThreshold: Long = 0
	var numPointsBelowThreshold = histogram[0]

	var interClassVariance: Double
	var maxInterClassVariance = 0.0
	var predictedThresholdIdx = 0

	for (i in 1 until histogram.size - 1) {
		intensitySumBelowThreshold += i * histogram[i]
		numPointsBelowThreshold += histogram[i]

		val denom = numPointsBelowThreshold.toDouble() * (numPoints - numPointsBelowThreshold)

		if (denom != 0.0) {
			val num = ((numPointsBelowThreshold.toDouble() / numPoints) * histogramIntensity - intensitySumBelowThreshold).pow(2)
			interClassVariance = num / denom
		} else interClassVariance = 0.0

		if (interClassVariance >= maxInterClassVariance) {
			maxInterClassVariance = interClassVariance
			predictedThresholdIdx = i
		}
	}
	return predictedThresholdIdx.toLong()
}