package org.janelia.saalfeldlab.paintera.filters.depthblur

import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.gauss3.Gauss3
import net.imglib2.img.array.ArrayImgs
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.Translation3D
import net.imglib2.type.numeric.real.FloatType
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.RenderFilter
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImage
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImageProvider
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenView
import org.janelia.saalfeldlab.util.translate
import kotlin.math.abs
import kotlin.math.max

/**
 * Blend of [slicesPerSide] slices on either side of the viewer plane, [spacing] global units apart along its normal, whatever the rotation or anisotropy.
 *
 * [gaussianCutoff] places the outermost slice that many sigma from the center, so sigma follows the slice count.
 */
data class DepthBlur(
	val slicesPerSide: Int = 2,
	val spacing: Double = 1.0,
	val blend: Blend = Blend.MEAN,
	val gaussianCutoff: Double = 2.0
) : RenderFilter {

	enum class Blend(val displayName: String) {
		MEAN("Mean"),
		GAUSSIAN("Gaussian");

		/** the weights of slices 0..[slicesPerSide], the outermost sitting [gaussianCutoff] sigma out; not normalized */
		fun halfKernel(slicesPerSide: Int, gaussianCutoff: Double): DoubleArray = when (this) {
			MEAN -> DoubleArray(slicesPerSide + 1) { 1.0 }
			GAUSSIAN -> Gauss3.halfkernel(max(slicesPerSide, 1) / gaussianCutoff, slicesPerSide + 1, false)
		}

		override fun toString() = displayName
	}

	val weights: DoubleArray by lazy {
		val halfKernel = blend.halfKernel(slicesPerSide, gaussianCutoff)
		DoubleArray(2 * slicesPerSide + 1) { halfKernel[abs(it - slicesPerSide)] }
	}

	/** the slices' offsets in screen z units, [toScreenDepth] converting a global length along the viewer normal */
	fun zOffsets(toScreenDepth: (Double) -> Double = { it }) = DoubleArray(2 * slicesPerSide + 1) { toScreenDepth((it - slicesPerSide) * spacing) }

	override fun apply(imgProvider: ScreenImageProvider, view: ScreenView) = ScreenImageProvider { screenInterval, sliceToScreen ->
		val sum = WeightedSum(screenInterval)
		zOffsets(view::screenDepth).forEachIndexed { idx, offset ->
			val sliceToScreenAtDepth = sliceToScreen.copy().preConcatenate(Translation3D(0.0, 0.0, offset))
			sum.add(imgProvider.image(screenInterval, sliceToScreenAtDepth), weights[idx].toFloat())
		}
		sum.image()
	}

	private class WeightedSum(private val interval: Interval) {

		private val sum = zeros()
		private val reached = zeros()
		private var total = 0f
		private var complete = true

		private fun zeros(): RandomAccessibleInterval<FloatType> =
			ArrayImgs.floats(interval.dimension(0), interval.dimension(1)).translate(interval.min(0), interval.min(1))

		fun add(screenImage: ScreenImage, weight: Float) {
			LoopBuilder.setImages(sum, reached, screenImage.rasterImg, screenImage.mask).multiThreaded().forEachPixel { summed, weights, value, coverage ->
				val reaching = weight * coverage.get()
				summed.set(summed.get() + reaching * value.get())
				weights.set(weights.get() + reaching)
			}
			total += weight
			complete = complete && screenImage.complete
		}

		/* a pixel is its sum over the weight that reached it, and is covered by the share of the weights that did */
		fun image(): ScreenImage {
			LoopBuilder.setImages(sum, reached).multiThreaded().forEachPixel { summed, weights ->
				if (weights.get() != 0f)
					summed.set(summed.get() / weights.get())
				weights.set(weights.get() / total)
			}
			return ScreenImage(sum, reached, complete)
		}
	}
}
