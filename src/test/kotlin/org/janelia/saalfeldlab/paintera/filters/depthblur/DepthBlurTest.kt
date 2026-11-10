package org.janelia.saalfeldlab.paintera.filters.depthblur

import net.imglib2.FinalDimensions
import net.imglib2.FinalInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.Scale3D
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImageProvider
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenView
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.covered
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.value
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.volume
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlur.Blend
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.exp

class DepthBlurTest {

	private val region = FinalInterval(3, 3)
	private val view = ScreenView(FinalDimensions(3, 3), Scale3D(1.0, 1.0, 1.0))

	private val squares = volume { _, _, z -> z * z }

	private fun DepthBlur.at(input: ScreenImageProvider, z: Double) = apply(input, view).image(region, AffineTransform3D().apply { translate(0.0, 0.0, z) })

	@Test
	fun `weights are symmetric, and gaussian peaks at the center`() {
		for (blend in Blend.entries) {
			for (slicesPerSide in 0..4) {
				val weights = DepthBlur(slicesPerSide, 1.0, blend).weights
				assertEquals(2 * slicesPerSide + 1, weights.size)
				assertArrayEquals(weights, weights.reversedArray(), 1e-12)
				if (blend == Blend.GAUSSIAN && slicesPerSide > 0)
					assertTrue(weights[slicesPerSide] > weights[slicesPerSide + 1])
				else
					assertTrue(weights.all { it == 1.0 })
			}
		}
	}

	@Test
	fun `the gaussian cutoff places the outermost slice that many sigma out`() {
		/* Gauss3 tapers the tail below the sampled gaussian, so the outermost weight is at most exp(-cutoff^2 / 2) */
		var previousTail = Double.MAX_VALUE
		for (cutoff in doubleArrayOf(0.5, 1.0, 2.0, 3.0)) {
			val weights = DepthBlur(3, 1.0, Blend.GAUSSIAN, gaussianCutoff = cutoff).weights
			val tail = weights.last() / weights[3]
			assertTrue(tail > 0.0 && tail <= exp(-cutoff * cutoff / 2) + 1e-12, "cutoff $cutoff: tail $tail")
			assertTrue(tail < previousTail, "a larger cutoff gives a smaller tail")
			previousTail = tail
		}
		/* the untapered interior matches the sampled gaussian */
		val weights = DepthBlur(3, 1.0, Blend.GAUSSIAN, gaussianCutoff = 1.0).weights
		assertEquals(exp(-1.0 / (2 * 9.0)), weights[4] / weights[3], 1e-12)
	}

	@Test
	fun `the slices are spacing voxels apart`() {
		assertArrayEquals(doubleArrayOf(-6.0, -3.0, 0.0, 3.0, 6.0), DepthBlur(2, 3.0).zOffsets(), 1e-12)
		assertArrayEquals(doubleArrayOf(-0.5, 0.0, 0.5), DepthBlur(1, 0.5).zOffsets(), 1e-12)
	}

	@Test
	fun `each pixel is the blend of the slices around its z`() {
		val expected = mapOf(
			Blend.MEAN to (16.0 + 25.0 + 36.0) / 3,
			Blend.GAUSSIAN to DepthBlur(1, 1.0, Blend.GAUSSIAN).weights.let { w -> (w[0] * 16.0 + w[1] * 25.0 + w[2] * 36.0) / w.sum() }
		)
		for ((blend, value) in expected)
			assertEquals(value, DepthBlur(1, 1.0, blend).at(squares, 5.0).value(1, 1), 1e-4, blend.name)

		assertEquals((9.0 + 25.0 + 49.0) / 3, DepthBlur(1, 2.0, Blend.MEAN).at(squares, 5.0).value(1, 1), 1e-4)
		assertEquals((20.25 + 25.0 + 30.25) / 3, DepthBlur(1, 0.5, Blend.MEAN).at(squares, 5.0).value(1, 1), 1e-4)
	}

	@Test
	fun `slices without data drop out, and the rest renormalize`() {
		val fromZero = volume(coverage = { _, _, z -> if (z < 0) 0.0 else 1.0 }) { _, _, z -> z * z }
		val atFirst = DepthBlur(1, 1.0, Blend.MEAN).at(fromZero, 0.0)
		assertEquals((0.0 + 1.0) / 2, atFirst.value(1, 1), 1e-4)
		assertEquals(2.0 / 3, atFirst.covered(1, 1), 1e-4)

		val outside = DepthBlur(1, 1.0, Blend.MEAN).at(fromZero, -20.0)
		assertEquals(0.0, outside.value(1, 1), 1e-4)
		assertEquals(0.0, outside.covered(1, 1), 1e-4)
	}

	@Test
	fun `partial coverage counts by its weight`() {
		val halfAtFour = volume(coverage = { _, _, z -> if (abs(z - 4.0) < 0.5) 0.5 else 1.0 }) { _, _, z -> z * z }
		assertEquals((0.5 * 16.0 + 25.0 + 36.0) / 2.5, DepthBlur(1, 1.0, Blend.MEAN).at(halfAtFour, 5.0).value(1, 1), 1e-4)
	}

	@Test
	fun `a blend is complete only if every slice it reads is`() {
		val loadingAtSix = volume(complete = { z -> abs(z - 6.0) > 0.5 }) { _, _, z -> z }
		val depthBlur = DepthBlur(1, 1.0, Blend.MEAN)
		assertFalse(depthBlur.at(loadingAtSix, 5.0).complete)
		assertTrue(depthBlur.at(loadingAtSix, 3.0).complete)
	}
}
