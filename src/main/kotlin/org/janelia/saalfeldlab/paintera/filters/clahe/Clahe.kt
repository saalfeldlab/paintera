package org.janelia.saalfeldlab.paintera.filters.clahe

import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilter
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImage
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImageProvider
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenView
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheCellImage.Companion.blendInterval
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheCellImage.Companion.createClaheCellImage
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheCellImage.Companion.createTileGrid
import org.janelia.saalfeldlab.util.interval

/**
 * Contrast limited adaptive histogram equalization in the viewer plane, tiled: the frame is cut into tiles about
 * 2 [radius] voxels wide, each remapped by its own clipped, equalized histogram in [bins] bins, and each pixel blends
 * the four tiles around it. [maxSlope] is the clip limit as a multiple of the flat histogram, so larger values
 * equalize harder. The histograms and the result span [range], the intensities of the source; the
 * contrast window applies to the result as to the source.
 */
data class Clahe(
	val radius: Double = 16.0,
	val maxSlope: Double = 3.0,
	val bins: Int = 256,
	val range: ClosedFloatingPointRange<Double> = 0.0..1.0
) : RenderFilter {

	override fun apply(imgProvider: ScreenImageProvider, view: ScreenView) = ScreenImageProvider { screenInterval, sliceToScreen ->
		val tileSides = DoubleArray(2) { 2 * view.screenPixels(radius, it) + 1 }
		val tileGrid = createTileGrid(view.size, tileSides)
		val slice = imgProvider.image(blendInterval(tileGrid, screenInterval), sliceToScreen)
		val equalized = createClaheCellImage(slice.masked, tileGrid, ClaheHistogram(bins, range), maxSlope)
		ScreenImage(equalized.img.interval(screenInterval), slice.mask.interval(screenInterval), slice.complete)
	}

	/* ported from mpicbg.ij.clahe.Util */
	companion object Util {

		/** [clippedHist] is [hist] with every bin cut to [limit] and the excess spread over the bins below [limit] */
		fun clipHistogram(hist: IntArray, clippedHist: IntArray, limit: Int) {
			var excess = 0
			var fullBins = 0
			for (bin in hist.indices) {
				val value = hist[bin]
				if (value >= limit) {
					clippedHist[bin] = limit
					excess += value - limit
					fullBins++
				} else
					clippedHist[bin] = value
			}
			while (excess > 0) {
				val freeBins = hist.size - fullBins
				val proRataExcess = excess / freeBins
				var remainder = excess % freeBins
				for (bin in hist.indices) {
					var value = clippedHist[bin]
					if (value == limit)
						continue
					value += proRataExcess
					if (remainder > 0) {
						value++
						remainder--
					}
					if (value >= limit) {
						/* the excess of this bin can still be taken by a bin later in this pass */
						clippedHist[bin] = limit
						remainder += value - limit
						fullBins++
					} else
						clippedHist[bin] = value
				}
				excess = remainder
			}
		}

		/**
		 * Writes the transfer of [hist] over its bins into [into] from [offset], from 0 at the first occupied bin to 1 at
		 * the last. A histogram with a single occupied bin maps to 0.5.
		 *
		 * @param limit the most any bin may hold; at least the histogram's total over its bins
		 */
		fun createTransfer(hist: IntArray, limit: Int, into: FloatArray, offset: Int) {
			val cdfs = IntArray(hist.size)
			clipHistogram(hist, cdfs, limit)
			val firstOccupied = cdfs.indexOfFirst { it != 0 }.coerceAtLeast(0)
			var cdf = 0
			for (bin in firstOccupied until cdfs.size) {
				cdf += cdfs[bin]
				cdfs[bin] = cdf
			}
			val cdfMin = cdfs[firstOccupied]
			val cdfMax = cdfs.last()
			for (bin in cdfs.indices)
				into[offset + bin] = if (cdfMax == cdfMin) 0.5f else ((cdfs[bin] - cdfMin) / (cdfMax - cdfMin).toFloat()).coerceIn(0f, 1f)
		}
	}
}
