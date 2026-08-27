package org.janelia.saalfeldlab.paintera.data

import net.imglib2.FinalInterval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [XyzView] is where the region and the mapping meet, so these check the seam: a slice is not a crop, a mapping
 * read out of the view is a snapshot rather than a live alias, [XyzView.toXyz] slices and crops in the right
 * order without breaking write-through, and the region never escapes the full extent however it is set.
 */
class XyzViewTest {

	private val base = 100L

	private fun encode(position: LongArray): Long {
		var value = 0L
		var factor = 1L
		for (coordinate in position) {
			value += coordinate * factor
			factor *= base
		}
		return value
	}

	private fun filled(dims: LongArray): RandomAccessibleInterval<UnsignedLongType> = ArrayImgs.unsignedLongs(*dims).also { img ->
		val cursor = img.localizingCursor()
		val position = LongArray(dims.size)
		while (cursor.hasNext()) {
			cursor.fwd(); cursor.localize(position); cursor.get().set(encode(position))
		}
	}

	/* XYCZT: x, y, z on source axes 0, 1, 3; channel at 2 and time at 4 */
	private val interleavedDimensions = longArrayOf(8, 9, 3, 10, 4)

	private fun interleaved() = XyzView(intArrayOf(0, 1, 3), interleavedDimensions)

	@Test
	fun `the mapping follows the region's slice positions`() {
		val xyzView = interleaved()
		assertEquals(listOf(0L, 0L, 0L, 0L, 0L), xyzView.spatialMapping().slicePositions.toList())

		xyzView.sliceAt(2, 1)
		xyzView.sliceAt(4, 3)
		assertEquals(listOf(0L, 0L, 1L, 0L, 3L), xyzView.spatialMapping().slicePositions.toList())
		assertEquals(5, xyzView.spatialMapping().numDimensions)
		assertEquals(listOf(0, 1, 3), xyzView.spatialMapping().xyzSourceAxes.toList())
	}

	@Test
	fun `a mapping already read stays at the slice it was read for`() {
		val xyzView = interleaved()
		xyzView.sliceAt(4, 1)
		val mapping = xyzView.spatialMapping()

		xyzView.sliceAt(4, 3)

		assertEquals(1L, mapping.slicePositions[4], "the mapping must not follow the region after the fact")
		assertEquals(3L, xyzView.spatialMapping().slicePositions[4], "a freshly read mapping must see the new slice")
	}

	@Test
	fun `cropping through the region leaves the slices alone`() {
		/* the only way to crop is to assign the whole nD region, so the sliced axes have to survive the round trip */
		val xyzView = interleaved()
		xyzView.sliceAt(2, 1)
		xyzView.sliceAt(4, 3)

		xyzView.xyzInterval = (FinalInterval(longArrayOf(2, 0, 0), longArrayOf(5, 8, 9)))

		assertEquals(listOf(1L, 3L), listOf(xyzView.activeInterval.min(2), xyzView.activeInterval.min(4)), "channel 1 and time 3 must survive")
		assertEquals(listOf(1L, 3L), listOf(xyzView.spatialMapping().slicePositions[2], xyzView.spatialMapping().slicePositions[4]))
		assertEquals(listOf(2L, 0L, 0L), (0 until 3).map { xyzView.xyzInterval.min(it) })
		assertEquals(listOf(5L, 8L, 9L), (0 until 3).map { xyzView.xyzInterval.max(it) })
	}

	@Test
	fun `slicing is not cropping`() {
		val xyzView = interleaved()
		xyzView.sliceAt(2, 1)
		xyzView.sliceAt(4, 3)

		assertFalse(xyzView.isCropped, "collapsing an axis the mapping drops does not narrow the 3D view")
		assertEquals(listOf(0L, 0L, 0L), (0 until 3).map { xyzView.xyzInterval.min(it) })
		assertEquals(listOf(7L, 8L, 9L), (0 until 3).map { xyzView.xyzInterval.max(it) }, "the interval is the full spatial extent")
	}

	@Test
	fun `cropping a kept axis projects to the 3D interval`() {
		val xyzView = interleaved()
		xyzView.sliceAt(4, 2)
		xyzView.xyzInterval = (FinalInterval(longArrayOf(2, 0, 1), longArrayOf(5, 8, 6)))

		val interval = xyzView.xyzInterval
		assertEquals(3, interval.numDimensions())
		assertEquals(listOf(2L, 0L, 1L), (0 until 3).map { interval.min(it) }, "x from axis 0, z from axis 3")
		assertEquals(listOf(5L, 8L, 6L), (0 until 3).map { interval.max(it) })
		assertTrue(xyzView.isCropped)
	}

	@Test
	fun `toXyz slices then crops, and still writes through`() {
		val xyzView = interleaved()
		xyzView.sliceAt(2, 1)
		xyzView.sliceAt(4, 3)
		xyzView.xyzInterval = (FinalInterval(longArrayOf(2, 0, 0), longArrayOf(5, 8, 9)))

		val backing = filled(interleavedDimensions)
		val view = xyzView.toXyz(backing)

		assertEquals(3, view.numDimensions())
		assertEquals(listOf(2L, 0L, 0L), (0 until 3).map { view.min(it) }, "the crop keeps its source offset")
		assertEquals(listOf(5L, 8L, 9L), (0 until 3).map { view.max(it) })

		val access = view.randomAccess().also { it.setPosition(longArrayOf(3, 4, 5)) }
		assertEquals(encode(longArrayOf(3, 4, 1, 5, 3)), access.get().get(), "must read the interleaved nD pixel at the slice")

		access.get().set(777_777L)
		val written = backing.randomAccess().also { it.setPosition(longArrayOf(3, 4, 1, 5, 3)) }.get().get()
		assertEquals(777_777L, written, "the cropped 3D view must still write through to the backing")
	}

	@Test
	fun `toXyz on an uncropped region is the plain mapping view`() {
		val xyzView = interleaved()
		xyzView.sliceAt(4, 2)

		val view = xyzView.toXyz(filled(interleavedDimensions))
		assertEquals(listOf(8L, 9L, 10L), (0 until 3).map { view.dimension(it) }, "the full spatial extent survives")
	}

	@Test
	fun `toXyz takes a caller-supplied interval for a lower level`() {
		/* the view never rescales; a lower level is the caller's own interval, in that level's coordinates */
		val xyzView = interleaved()
		xyzView.sliceAt(2, 1)
		xyzView.sliceAt(4, 3)
		xyzView.xyzInterval = (FinalInterval(longArrayOf(4, 0, 0), longArrayOf(7, 8, 9)))

		val halfScaleDimensions = longArrayOf(4, 5, 3, 5, 4)
		val backing = filled(halfScaleDimensions)
		val view = xyzView.toXyz(backing, FinalInterval(longArrayOf(2, 0, 1, 0, 3), longArrayOf(3, 4, 1, 4, 3)))

		assertEquals(listOf(2L, 0L, 0L), (0 until 3).map { view.min(it) })
		assertEquals(listOf(3L, 4L, 4L), (0 until 3).map { view.max(it) })
		val access = view.randomAccess().also { it.setPosition(longArrayOf(2, 1, 3)) }
		assertEquals(encode(longArrayOf(2, 1, 1, 3, 3)), access.get().get(), "the slice still comes from the region")

		access.get().set(555_555L)
		assertEquals(555_555L, backing.randomAccess().also { it.setPosition(longArrayOf(2, 1, 1, 3, 3)) }.get().get())
	}

	@Test
	fun `nonSpatialAxes lists the axes that supply no spatial dimension`() {
		assertEquals(listOf(2, 4), interleaved().nonSpatialAxes, "channel at 2 and time at 4")
		assertEquals(emptyList<Int>(), XyzView(intArrayOf(0, 1, 2), longArrayOf(8, 9, 10)).nonSpatialAxes)
		/* x, y, c with no z: the absent z is not an axis, so only the channel is non-spatial */
		assertEquals(listOf(2), XyzView(intArrayOf(0, 1, -1), longArrayOf(8, 9, 3)).nonSpatialAxes)
	}

	@Test
	fun `blockInterval divides the active interval by the block size`() {
		val xyzView = interleaved()
		val grid = CellGrid(interleavedDimensions, intArrayOf(4, 4, 1, 4, 1))
		xyzView.sliceAt(2, 2)
		xyzView.sliceAt(4, 3)
		xyzView.xyzInterval = FinalInterval(longArrayOf(5, 0, 6), longArrayOf(7, 8, 9))

		val blocks = xyzView.blockInterval(grid)
		assertEquals(listOf(1L, 0L, 2L, 1L, 3L), blocks.minAsLongArray().toList(), "x 5/4=1, z 6/4=1; unit-size axes are unchanged")
		assertEquals(listOf(1L, 2L, 2L, 2L, 3L), blocks.maxAsLongArray().toList(), "x 7/4=1, y 8/4=2, z 9/4=2")
	}

	@Test
	fun `blockInterval divides a non-unit slice axis too`() {
		/* a timepoint chunked 2-per-block: t = 3 lives in block 1 */
		val xyzView = interleaved()
		val grid = CellGrid(interleavedDimensions, intArrayOf(4, 4, 1, 4, 2))
		xyzView.sliceAt(4, 3)

		assertEquals(1L, xyzView.blockInterval(grid).min(4))
		assertEquals(1L, xyzView.blockInterval(grid).max(4))
	}

	@Test
	fun `toXyz over a blockInterval slices the cells image in block units`() {
		/* the prefetch case: the cells image is nD in block coordinates, so its XYZ view is a plain toXyz */
		val xyzView = interleaved()
		val grid = CellGrid(interleavedDimensions, intArrayOf(4, 4, 1, 4, 1))
		xyzView.sliceAt(2, 2)
		xyzView.sliceAt(4, 3)

		val cells = filled(grid.gridDimensions)
		val cellsXyz = xyzView.toXyz(cells, xyzView.blockInterval(grid))

		assertEquals(3, cellsXyz.numDimensions())
		val access = cellsXyz.randomAccess().also { it.setPosition(longArrayOf(1, 2, 1)) }
		assertEquals(encode(longArrayOf(1, 2, 2, 1, 3)), access.get().get(), "the block-space slice is c=2, t=3, not the voxel positions")
	}

	@Test
	fun `blockInterval rejects a grid of the wrong dimensionality`() {
		assertThrows(IllegalArgumentException::class.java) {
			interleaved().blockInterval(CellGrid(longArrayOf(8, 9, 10), intArrayOf(4, 4, 4)))
		}
	}

	@Test
	fun `the interval handed to toXyz carries its own slice`() {
		/* slicing and cropping are one interval, so the min of whatever you pass is the slice; the view does not move */
		val xyzView = interleaved()
		val backing = filled(interleavedDimensions)

		val view = xyzView.toXyz(backing, FinalInterval(longArrayOf(0, 0, 1, 0, 3), longArrayOf(7, 8, 1, 9, 3)))

		val access = view.randomAccess().also { it.setPosition(longArrayOf(3, 4, 5)) }
		assertEquals(encode(longArrayOf(3, 4, 1, 5, 3)), access.get().get(), "channel 1 and time 3 come from the interval")
		assertEquals(listOf(0L, 0L), listOf(xyzView.activeInterval.min(2), xyzView.activeInterval.min(4)), "the view itself is still parked at 0")
	}

	@Test
	fun `toXyz adds no wrapper when the interval is the whole mapping view`() {
		/* an identity view must hand back the backing untouched; that is what makes a plain 3D source free */
		val canonical = XyzView(intArrayOf(0, 1, 2), longArrayOf(8, 9, 10))
		val backing = filled(longArrayOf(8, 9, 10))
		assertSame(backing, canonical.toXyz(backing), "an uncropped canonical source must not gain an interval wrapper")

		canonical.xyzInterval = (FinalInterval(longArrayOf(0, 2, 0), longArrayOf(7, 5, 9)))
		assertNotSame(backing, canonical.toXyz(backing), "a crop has to wrap")
		assertEquals(listOf(2L, 5L), listOf(canonical.toXyz(backing).min(1), canonical.toXyz(backing).max(1)))
	}

	@Test
	fun `of builds the view from axis metadata`() {
		val axes = arrayOf(
			Axis(Axis.TIME, "t", null),
			Axis(Axis.SPACE, "z", "pixel"),
			Axis(Axis.SPACE, "y", "pixel"),
			Axis(Axis.SPACE, "x", "pixel")
		)
		val xyzView = XyzView.of(axes, longArrayOf(4, 10, 9, 8))

		assertEquals(listOf(3, 2, 1), xyzView.xyzSourceAxes.toList(), "x, y, z come from the reversed axis order")
		assertEquals(4, xyzView.numDimensions)

		xyzView.sliceAt(0, 2)
		val view = xyzView.toXyz(filled(longArrayOf(4, 10, 9, 8)))
		assertEquals(listOf(8L, 9L, 10L), (0 until 3).map { view.dimension(it) })
		val access = view.randomAccess().also { it.setPosition(longArrayOf(1, 2, 3)) }
		assertEquals(encode(longArrayOf(2, 3, 2, 1)), access.get().get(), "t=2, z=3, y=2, x=1")
	}

	@Test
	fun `the xyzView copies the axes it was given`() {
		val xyzSourceAxes = intArrayOf(0, 1, 3)
		val xyzView = XyzView(xyzSourceAxes, interleavedDimensions)
		xyzSourceAxes[2] = 2
		assertEquals(listOf(0, 1, 3), xyzView.xyzSourceAxes.toList())
	}

	@Test
	fun `a fully cropped away axis still projects`() {
		/* x, y, c with no z: the synthesized z slot must stay the singleton [0, 0] whatever the channel is cropped to */
		val xyzView = XyzView(intArrayOf(0, 1, -1), longArrayOf(8, 9, 3))
		xyzView.sliceAt(2, 2)
		assertFalse(xyzView.isCropped, "slicing the channel is not a crop")

		xyzView.xyzInterval = (FinalInterval(longArrayOf(0, 3, 0), longArrayOf(7, 5, 0)))
		val interval = xyzView.xyzInterval
		assertEquals(listOf(0L, 3L, 0L), (0 until 3).map { interval.min(it) })
		assertEquals(listOf(7L, 5L, 0L), (0 until 3).map { interval.max(it) })

		val view = xyzView.toXyz(filled(longArrayOf(8, 9, 3)))
		assertEquals(listOf(8L, 3L, 1L), (0 until 3).map { view.dimension(it) })
		val access = view.randomAccess().also { it.setPosition(longArrayOf(2, 4, 0)) }
		assertEquals(encode(longArrayOf(2, 4, 2)), access.get().get())
	}

	/* the region invariants: it never escapes the full extent whichever way it is set, and it only notifies on a real change */

	@Test
	fun `starts at the full extent`() {
		val xyzView = XyzView(intArrayOf(0, 1, 2), longArrayOf(8, 9, 10))
		assertEquals(listOf(0L, 0L, 0L), xyzView.activeInterval.minAsLongArray().toList())
		assertEquals(listOf(7L, 8L, 9L), xyzView.activeInterval.maxAsLongArray().toList())
		assertEquals(3, xyzView.numDimensions)
	}

	@Test
	fun `a sliced axis is degenerate at its position`() {
		val xyzView = interleaved()
		xyzView.sliceAt(2, 1)
		xyzView.sliceAt(4, 3)

		assertEquals(listOf(1L, 3L), listOf(xyzView.activeInterval.min(2), xyzView.activeInterval.min(4)))
		assertEquals(listOf(1L, 3L), listOf(xyzView.activeInterval.max(2), xyzView.activeInterval.max(4)))
		assertEquals(listOf(1L, 1L), listOf(xyzView.activeInterval.dimension(2), xyzView.activeInterval.dimension(4)))
	}

	@Test
	fun `sliceAt leaves the other axes alone`() {
		val xyzView = interleaved()
		xyzView.sliceAt(4, 2)

		assertEquals(listOf(0L, 0L, 0L, 0L), (0..3).map { xyzView.activeInterval.min(it) })
		assertEquals(listOf(7L, 8L, 2L, 9L), (0..3).map { xyzView.activeInterval.max(it) })
	}

	@Test
	fun `an out of range position clamps to the extent`() {
		val xyzView = interleaved()

		xyzView.sliceAt(4, 99)
		assertEquals(3L, xyzView.activeInterval.min(4), "past the end clamps to the last position")

		xyzView.sliceAt(4, -5)
		assertEquals(0L, xyzView.activeInterval.min(4), "before the start clamps to the first position")
	}

	@Test
	fun `an inverted axis collapses to its min`() {
		val xyzView = interleaved()
		xyzView.activeInterval = FinalInterval(longArrayOf(0, 6, 0, 0, 0), longArrayOf(7, 2, 2, 9, 3))
		assertEquals(6L, xyzView.activeInterval.min(1))
		assertEquals(6L, xyzView.activeInterval.max(1))
	}

	@Test
	fun `assigning the region directly is clamped too`() {
		val xyzView = interleaved()
		xyzView.activeInterval = FinalInterval(longArrayOf(-4, 0, 0, 0, 0), longArrayOf(100, 8, 2, 9, 3))
		assertEquals(0L, xyzView.activeInterval.min(0))
		assertEquals(7L, xyzView.activeInterval.max(0))

		xyzView.regionProperty.value = FinalInterval(longArrayOf(0, 0, 0, 0, 0), longArrayOf(100, 8, 2, 9, 3))
		assertEquals(7L, xyzView.activeInterval.max(0), "setting through the property must be clamped the same way")
	}

	@Test
	fun `the property fires on a real change and stays quiet otherwise`() {
		val xyzView = interleaved()
		var notifications = 0
		xyzView.regionProperty.subscribe { _, _ -> notifications++ }

		xyzView.sliceAt(4, 2)
		assertEquals(1, notifications)

		xyzView.sliceAt(4, 2)
		assertEquals(1, notifications, "setting the same slice again must not notify")

		xyzView.activeInterval = FinalInterval(xyzView.activeInterval.minAsLongArray(), xyzView.activeInterval.maxAsLongArray())
		assertEquals(1, notifications, "an equal interval that is a different instance must not notify")

		xyzView.sliceAt(4, 99)
		assertEquals(2, notifications, "clamping to a new position is still a change")

		xyzView.sliceAt(4, 100)
		assertEquals(2, notifications, "clamping to the position it already holds is not")
	}

	@Test
	fun `reset restores the full extent`() {
		val xyzView = interleaved()
		xyzView.sliceAt(2, 1)
		xyzView.xyzInterval = FinalInterval(longArrayOf(2, 0, 0), longArrayOf(5, 8, 9))
		assertFalse(Intervals.equals(xyzView.fullInterval, xyzView.activeInterval))

		xyzView.reset()
		assertTrue(Intervals.equals(xyzView.fullInterval, xyzView.activeInterval))
	}

	@Test
	fun `a bad axis or dimensionality is rejected`() {
		val xyzView = interleaved()
		assertThrows(IllegalArgumentException::class.java) { xyzView.sliceAt(5, 0) }
		assertThrows(IllegalArgumentException::class.java) { xyzView.sliceAt(-1, 0) }
		assertThrows(IllegalArgumentException::class.java) { xyzView.activeInterval = FinalInterval(longArrayOf(0, 0, 0), longArrayOf(1, 1, 1)) }
	}
}
