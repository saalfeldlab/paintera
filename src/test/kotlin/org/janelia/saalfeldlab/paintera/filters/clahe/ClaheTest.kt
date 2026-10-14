package org.janelia.saalfeldlab.paintera.filters.clahe

import javafx.beans.property.SimpleObjectProperty
import net.imglib2.FinalDimensions
import net.imglib2.realtransform.Scale3D
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenImageProvider
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.ScreenView
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.covered
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.value
import org.janelia.saalfeldlab.bdv.fx.viewer.render.screenspace.volume
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaheTest {

	/* one voxel is one pixel, so a radius is a tile's half width in pixels */
	private val frame = FinalInterval(9, 9)
	private val view = ScreenView(FinalDimensions(9, 9), Scale3D(1.0, 1.0, 1.0))

	/* radius 4 makes one 9 pixel tile of the whole frame; radius 2 makes two along each axis, x = 0..4 and 5..8 */
	private fun clahe(maxSlope: Double, radius: Double = 4.0, range: ClosedFloatingPointRange<Double> = 0.0..1.0) =
		Clahe(radius = radius, maxSlope = maxSlope, bins = 256, range = range)

	private fun Clahe.equalize(input: ScreenImageProvider, region: Interval = frame) = apply(input, view).image(region)

	private val halfAndHalf = volume { x, _, _ -> if (x < 2) 0.0 else 1.0 }

	@Test
	fun `a lower max slope equalizes less`() {
		val ramp = volume { x, _, _ -> if (x < 7) 0.1 else 0.2 }
		val gentle = clahe(maxSlope = 1.0).equalize(ramp).value(7, 4)
		val harsh = clahe(maxSlope = 1000.0).equalize(ramp).value(7, 4)
		assertEquals(1.0, harsh, 1e-4)
		assertTrue(gentle <= harsh)
	}

	@Test
	fun `without clipping, the occupied values spread over the whole range`() {
		val equalized = clahe(maxSlope = 1000.0).equalize(volume { x, _, _ -> if (x < 5) 0.3 else 0.4 })
		assertEquals(0.0, equalized.value(4, 4), 1e-4)
		assertEquals(1.0, equalized.value(5, 4), 1e-4)
	}

	@Test
	fun `the result spans the range`() {
		val equalized = clahe(maxSlope = 1000.0, range = 0.2..0.6).equalize(volume { x, _, _ -> if (x < 5) 0.3 else 0.4 })
		assertEquals(0.2, equalized.value(4, 4), 1e-4)
		assertEquals(0.6, equalized.value(5, 4), 1e-4)
	}

	@Test
	fun `an inverted range swaps the ends`() {
		val equalized = clahe(maxSlope = 1000.0, range = 0.6..0.2).equalize(volume { x, _, _ -> if (x < 5) 0.3 else 0.4 })
		/* 0.4 is nearer the start of the range than 0.3 is */
		assertEquals(0.6, equalized.value(5, 4), 1e-4)
		assertEquals(0.2, equalized.value(4, 4), 1e-4)
	}

	@Test
	fun `a pixel blends the tiles around it by its distance to their centers`() {
		/* the left tile is two fifths 0, three fifths 1; the right tile is flat 1, which maps to the middle */
		val equalized = clahe(maxSlope = 1000.0, radius = 2.0).equalize(halfAndHalf)
		/* left of the left center, the left tile alone */
		assertEquals(0.0, equalized.value(0, 4), 1e-4)
		/* the tiles are 5 wide, 0..4 and 5..8, centered at 2 and 7 (the short one as if whole); 3 is a fifth of the way between them */
		assertEquals(0.8 + 0.2 * 0.5, equalized.value(3, 4), 1e-4)
		/* right of the right center, the right tile alone */
		assertEquals(0.5, equalized.value(8, 4), 1e-4)
	}

	@Test
	fun `a partial repaint equalizes with the whole frame's tiles`() {
		val filter = clahe(maxSlope = 1000.0, radius = 2.0)
		val whole = filter.equalize(halfAndHalf)

		val requested = ArrayList<Interval>()
		val recording = ScreenImageProvider { screenInterval, sliceToScreen -> halfAndHalf.image(screenInterval, sliceToScreen).also { requested += screenInterval } }
		val columns = FinalInterval(longArrayOf(3, 2), longArrayOf(3, 3))
		val partial = filter.equalize(recording, columns)

		assertTrue(Intervals.equals(columns, partial.rasterImg))
		for (y in 2L..3L)
			assertEquals(whole.value(3, y), partial.value(3, y), 1e-6)
		/* column 3 blends both tiles along x, and rows 2 and 3 both tiles along y */
		assertTrue(Intervals.equals(frame, requested.single()))
	}

	@Test
	fun `a flat tile maps to the middle of the range`() {
		assertEquals(0.5, clahe(maxSlope = 1000.0).equalize(volume { _, _, _ -> 0.8 }).value(4, 4), 1e-4)
	}

	@Test
	fun `pixels without data do not count`() {
		/* the tile sees 0 (not covered) and 1: a flat tile of 1s */
		val step = volume(coverage = { x, _, _ -> if (x < 5) 0.0 else 1.0 }) { x, _, _ -> if (x < 5) 0.0 else 1.0 }
		val equalized = clahe(maxSlope = 1000.0).equalize(step)
		assertEquals(0.5, equalized.value(5, 4), 1e-4)
		assertEquals(0.0, equalized.value(3, 4), 1e-4)
		assertEquals(0.0, equalized.covered(3, 4), 1e-4)
	}

	@Test
	fun `the result is complete when its input is`() {
		assertTrue(clahe(maxSlope = 3.0).equalize(volume { x, _, _ -> 0.1 * x }).complete)
		assertFalse(clahe(maxSlope = 3.0).equalize(volume(complete = { false }) { x, _, _ -> 0.1 * x }).complete)
	}

	@Test
	fun `clipping spreads the excess over the free bins`() {
		val clipped = IntArray(4)
		Clahe.clipHistogram(intArrayOf(10, 2, 0, 0), clipped, 4)
		assertArrayEquals(intArrayOf(4, 4, 2, 2), clipped)
		Clahe.clipHistogram(intArrayOf(5, 5, 0, 0), clipped, 4)
		assertArrayEquals(intArrayOf(4, 4, 1, 1), clipped)
	}

	@Test
	fun `the model follows the intensity range`() {
		val intensityRange = SimpleObjectProperty(0.0..1.0)
		val model = ClaheModel(ClaheConfig(), intensityRange)
		model.enabledProperty.set(true)
		assertEquals(0.0..1.0, model.filterProperty.get()!!.range)
		intensityRange.set(10.0..200.0)
		assertEquals(10.0..200.0, model.filterProperty.get()!!.range)
	}

	@Test
	fun `a transfer runs from 0 at the first occupied bin to 1 at the last`() {
		val transfer = FloatArray(4)
		Clahe.createTransfer(intArrayOf(0, 2, 0, 2), 100, transfer, 0)
		assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f), transfer, 1e-6f)
	}
}
