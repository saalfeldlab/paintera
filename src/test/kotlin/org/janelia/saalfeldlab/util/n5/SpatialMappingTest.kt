package org.janelia.saalfeldlab.util.n5

import net.imglib2.FinalInterval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.Intervals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Verifies the two properties [SpatialMapping] relies on: a hyperSlice + permute composition reads back the right
 * nD pixel for arbitrary axis layouts (incl. TYXZC, where no single swap groups x/y/z), and the resulting view
 * writes through to the backing image.
 */
class SpatialMappingTest {

	private val base = 100L

	/** Encode a full nD position into one value, so any sliced/permuted pixel is traceable to its source coordinate. */
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

	private fun assertReorder(name: String, dims: LongArray, xyzAxes: IntArray, fixedPositions: LongArray) {
		val ndImg = filled(dims)
		val view = SpatialMapping(dims.size, xyzAxes, fixedPositions).toXyz(ndImg)
		assertEquals(3, view.numDimensions(), "$name: view should be 3D")
		val access = view.randomAccess()
		for (x in 0L..2L) for (y in 0L..2L) for (z in 0L..2L) {
			access.setPosition(longArrayOf(x, y, z))
			val expected = LongArray(dims.size) { fixedPositions[it] }
			expected[xyzAxes[0]] = x; expected[xyzAxes[1]] = y; expected[xyzAxes[2]] = z
			assertEquals(encode(expected), access.get().get(), "$name: mismatch at view ($x,$y,$z)")
		}
	}

	@Test
	fun `toXyz maps arbitrary axis orders to the canonical slots`() {
		assertReorder("XYZC", longArrayOf(5, 6, 7, 4), intArrayOf(0, 1, 2), longArrayOf(0, 0, 0, 2))
		assertReorder("XYZCT", longArrayOf(5, 6, 7, 4, 3), intArrayOf(0, 1, 2), longArrayOf(0, 0, 0, 1, 2))
		assertReorder("XYCZT channel between", longArrayOf(5, 6, 4, 7, 3), intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		assertReorder("TYXZC no cycle groups xyz", longArrayOf(3, 6, 5, 7, 4), intArrayOf(2, 1, 3), longArrayOf(1, 0, 0, 0, 2))
	}

	@Test
	fun `toXyz view writes through to the backing image`() {
		/* in-memory ArrayImg */
		val array = filled(longArrayOf(8, 8, 8, 4, 3))
		val arrayView = SpatialMapping(5, intArrayOf(0, 1, 2), longArrayOf(0, 0, 0, 2, 1)).toXyz(array)
		arrayView.randomAccess().also { it.setPosition(longArrayOf(3, 4, 5)); it.get().set(999_999L) }
		val arrayBacking = array.randomAccess().also { it.setPosition(longArrayOf(3, 4, 5, 2, 1)) }.get().get()
		assertEquals(999_999L, arrayBacking, "write through ArrayImg should reach the backing pixel")

		/* writable DiskCachedCellImg */
		val options = DiskCachedCellImgOptions().cellDimensions(4, 4, 4, 1, 1)
		val cellImg = DiskCachedCellImgFactory(UnsignedLongType(), options).create(longArrayOf(8, 8, 8, 4, 3)) { cell ->
			val cursor = cell.localizingCursor()
			val position = LongArray(5)
			while (cursor.hasNext()) { cursor.fwd(); cursor.localize(position); cursor.get().set(encode(position)) }
		}
		val cellView = SpatialMapping(5, intArrayOf(0, 1, 2), longArrayOf(0, 0, 0, 2, 1)).toXyz(cellImg)
		cellView.randomAccess().also { it.setPosition(longArrayOf(3, 4, 5)); it.get().set(888_888L) }
		val cellBacking = cellImg.randomAccess().also { it.setPosition(longArrayOf(3, 4, 5, 2, 1)) }.get().get()
		assertEquals(888_888L, cellBacking, "write through DiskCachedCellImg should reach the backing cell")
	}

	@Test
	fun `editing xyz writes through to the interleaved nD spatial indices`() {
		/* XYTZC: the spatial axes live at 0, 1, 3 (T at 2, C at 4). Editing the 3D xyz view must write the backing at
		 * exactly those indices, at the fixed timepoint/channel - this is the commit-correctness question for an
		 * interleaved source: paint "z" lands on axis 3, not axis 2. */
		val dims = longArrayOf(8, 9, 2, 10, 3) // X, Y, T, Z, C
		val fixed = longArrayOf(0, 0, 1, 0, 2) // viewing T = 1, C = 2
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), fixed)

		val options = DiskCachedCellImgOptions().cellDimensions(4, 4, 1, 4, 1)
		val cellImg = DiskCachedCellImgFactory(UnsignedLongType(), options).create(dims) { cell ->
			val cursor = cell.localizingCursor()
			val position = LongArray(5)
			while (cursor.hasNext()) { cursor.fwd(); cursor.localize(position); cursor.get().set(encode(position)) }
		}

		val view = mapping.toXyz(cellImg)
		assertEquals(listOf(8L, 9L, 10L), (0 until 3).map { view.dimension(it) }, "view should present [X, Y, Z]")

		/* paint at view (x=3, y=4, z=5) */
		view.randomAccess().also { it.setPosition(longArrayOf(3, 4, 5)); it.get().set(777_777L) }

		/* it must land at nD [x=3, y=4, t=1, z=5, c=2]: spatial on axes 0, 1, 3; slice fixed on 2, 4 */
		val written = cellImg.randomAccess().also { it.setPosition(longArrayOf(3, 4, 1, 5, 2)) }.get().get()
		assertEquals(777_777L, written, "edit must write the true X, Y, Z indices (0, 1, 3) at the fixed T, C")

		/* the same x, y, z at a different timepoint must be untouched (still its original encoded value) */
		val otherTimepoint = cellImg.randomAccess().also { it.setPosition(longArrayOf(3, 4, 0, 5, 2)) }.get().get()
		assertEquals(encode(longArrayOf(3, 4, 0, 5, 2)), otherTimepoint, "other timepoints must be left untouched")
	}

	@Test
	fun `toXyz embeds a singleton z when there are only two spatial dims`() {
		/* pure 2D */
		val view2d = SpatialMapping(2, intArrayOf(0, 1, -1), longArrayOf(0, 0)).toXyz(filled(longArrayOf(5, 6)))
		assertEquals(listOf(5L, 6L, 1L), (0 until 3).map { view2d.dimension(it) })
		val access2d = view2d.randomAccess()
		for (x in 0L..2L) for (y in 0L..2L) {
			access2d.setPosition(longArrayOf(x, y, 0))
			assertEquals(encode(longArrayOf(x, y)), access2d.get().get(), "2D embed mismatch at ($x,$y)")
		}

		/* nD with two spatial dims (x, y, c): fix the channel and embed z */
		val viewXYC = SpatialMapping(3, intArrayOf(0, 1, -1), longArrayOf(0, 0, 2)).toXyz(filled(longArrayOf(5, 6, 4)))
		assertEquals(listOf(5L, 6L, 1L), (0 until 3).map { viewXYC.dimension(it) })
		val accessXYC = viewXYC.randomAccess()
		for (x in 0L..2L) for (y in 0L..2L) {
			accessXYC.setPosition(longArrayOf(x, y, 0))
			assertEquals(encode(longArrayOf(x, y, 2)), accessXYC.get().get(), "XYC embed mismatch at ($x,$y)")
		}
	}

	@Test
	fun `toXyz inserts singletons at the canonical slot of the absent dimension`() {
		/* one spatial dim (x): pad y and z -> [x, 1, 1] */
		val view1d = SpatialMapping(1, intArrayOf(0, -1, -1), longArrayOf(0)).toXyz(filled(longArrayOf(7)))
		assertEquals(listOf(7L, 1L, 1L), (0 until 3).map { view1d.dimension(it) })
		val access1d = view1d.randomAccess()
		for (x in 0L..2L) {
			access1d.setPosition(longArrayOf(x, 0, 0))
			assertEquals(encode(longArrayOf(x)), access1d.get().get(), "1D embed mismatch at $x")
		}

		/* non-prefix subset (x, z) with no y: the synthesized singleton must land in the y slot -> [x, 1, z] */
		val viewXZ = SpatialMapping(2, intArrayOf(0, -1, 1), longArrayOf(0, 0)).toXyz(filled(longArrayOf(5, 7)))
		assertEquals(listOf(5L, 1L, 7L), (0 until 3).map { viewXZ.dimension(it) })
		val accessXZ = viewXZ.randomAccess()
		for (x in 0L..2L) for (z in 0L..2L) {
			accessXZ.setPosition(longArrayOf(x, 0, z))
			assertEquals(encode(longArrayOf(x, z)), accessXZ.get().get(), "XZ embed mismatch at ($x,$z)")
		}
	}

	@Test
	fun `toSourceView round-trips a non-prefix spatial subset`() {
		/* (x, z), no y: widening drops the y singleton and restores source axes 0 (x) and 1 (z) */
		val mapping = SpatialMapping(2, intArrayOf(0, -1, 1), longArrayOf(0, 0))
		val widened = mapping.toSourceView(mapping.toXyz(filled(longArrayOf(5, 7))))
		assertEquals(listOf(5L, 7L), (0 until 2).map { widened.dimension(it) })
		val access = widened.randomAccess()
		for (x in 0L..2L) for (z in 0L..2L) {
			access.setPosition(longArrayOf(x, z))
			assertEquals(encode(longArrayOf(x, z)), access.get().get(), "XZ widen mismatch at ($x,$z)")
		}
		assertEquals(listOf(50, 1, 70), mapping.spatialProjection(intArrayOf(50, 70)).toList())
		assertEquals(listOf(50, 70), mapping.toSourceBlockSize(intArrayOf(50, 1, 70)).toList())
	}

	@Test
	fun `toSourceView round-trips two-spatial sources`() {
		/* pure 2D: widening drops the embedded z back to 2 dims */
		val mapping2d = SpatialMapping(2, intArrayOf(0, 1, -1), longArrayOf(0, 0))
		val widened2d = mapping2d.toSourceView(mapping2d.toXyz(filled(longArrayOf(5, 6))))
		assertEquals(listOf(5L, 6L), (0 until 2).map { widened2d.dimension(it) })
		assertEquals(listOf(50, 60), mapping2d.toSourceBlockSize(intArrayOf(50, 60, 1)).toList())

		/* x, y, c: widening drops z and restores the channel as a singleton at its fixed position */
		val mappingXYC = SpatialMapping(3, intArrayOf(0, 1, -1), longArrayOf(0, 0, 2))
		val widenedXYC = mappingXYC.toSourceView(mappingXYC.toXyz(filled(longArrayOf(5, 6, 4))))
		assertEquals(listOf(5L, 6L, 1L), (0 until 3).map { widenedXYC.dimension(it) })
		val access = widenedXYC.randomAccess()
		for (x in 0L..2L) for (y in 0L..2L) {
			access.setPosition(longArrayOf(x, y, 2))
			assertEquals(encode(longArrayOf(x, y, 2)), access.get().get(), "XYC widen mismatch at ($x,$y)")
		}
		assertEquals(listOf(50, 60, 1), mappingXYC.toSourceBlockSize(intArrayOf(50, 60, 1)).toList())
	}

	@Test
	fun `toSourceView reinserts singleton axes at the fixed slice`() {
		val dims = longArrayOf(5, 6, 4, 7, 3)
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		val ndImg = filled(dims)
		val widened = mapping.toSourceView(mapping.toXyz(ndImg))
		/* the widened view is nD with singletons at the non-spatial axes, fixed at their positions */
		assertEquals(listOf(5L, 6L, 1L, 7L, 1L), (0 until 5).map { widened.dimension(it) })
		val access = widened.randomAccess()
		for (x in 0L..2L) for (y in 0L..2L) for (z in 0L..2L) {
			access.setPosition(longArrayOf(x, y, 2, z, 1))
			assertEquals(encode(longArrayOf(x, y, 2, z, 1)), access.get().get(), "widened mismatch at ($x,$y,$z)")
		}
	}

	@Test
	fun `toSourceBlockSize puts 1 at non-spatial axes`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		assertEquals(listOf(50, 60, 1, 70, 1), mapping.toSourceBlockSize(intArrayOf(50, 60, 70)).toList())
	}

	@Test
	fun `toSourceInterval reinserts fixed positions`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		val sourceInterval = mapping.toSourceInterval(net.imglib2.FinalInterval(longArrayOf(1, 2, 3), longArrayOf(4, 5, 6)))
		assertEquals(listOf(1L, 2L, 2L, 3L, 1L), (0 until 5).map { sourceInterval.min(it) })
		assertEquals(listOf(4L, 5L, 2L, 6L, 1L), (0 until 5).map { sourceInterval.max(it) })
	}

	@Test
	fun `toXyzPosition and toSourcePosition round-trip at the slice positions`() {
		/* XYCZT: spatial on 0, 1, 3 */
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		assertEquals(listOf(4L, 5L, 6L), mapping.toXyzPosition(longArrayOf(4, 5, 2, 6, 1)).toList())
		assertEquals(listOf(4L, 5L, 2L, 6L, 1L), mapping.toSourcePosition(4, 5, 6).toList())

		/* an absent dimension projects to 0, unlike a shape, where it is the singleton extent 1 */
		val embedded = SpatialMapping(2, intArrayOf(0, -1, 1), longArrayOf(0, 0))
		assertEquals(listOf(3L, 0L, 7L), embedded.toXyzPosition(longArrayOf(3, 7)).toList())
		assertEquals(listOf(50L, 1L, 70L), embedded.spatialProjection(longArrayOf(50, 70)).toList())
	}

	@Test
	fun `the shape projections reject a shape of the wrong length`() {
		/* a shape of the wrong length reads the wrong axes and still returns a plausible-looking grid */
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		assertThrows(IllegalArgumentException::class.java) { mapping.spatialProjection(intArrayOf(8, 9, 10)) }
		assertThrows(IllegalArgumentException::class.java) { mapping.spatialProjection(longArrayOf(8, 9, 10)) }
		assertThrows(IllegalArgumentException::class.java) { mapping.toSourceBlockSize(intArrayOf(8, 9, 10, 1, 1)) }

		/* an absent spatial dimension still projects to a singleton, which is its real extent */
		val embedded = SpatialMapping(2, intArrayOf(0, -1, 1), longArrayOf(0, 0))
		assertEquals(listOf(50, 1, 70), embedded.spatialProjection(intArrayOf(50, 70)).toList())
	}

	@Test
	fun `toXyzInterval drops the non-spatial axes`() {
		/* XYCZT: spatial on 0, 1, 3; the channel (2) and time (4) extents must not reach the 3D interval */
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		val xyz = mapping.toXyzInterval(FinalInterval(longArrayOf(1, 2, 2, 3, 1), longArrayOf(4, 5, 2, 6, 1)))
		assertEquals(listOf(1L, 2L, 3L), (0 until 3).map { xyz.min(it) })
		assertEquals(listOf(4L, 5L, 6L), (0 until 3).map { xyz.max(it) })
	}

	@Test
	fun `toXyzInterval and toSourceInterval round-trip at the slice positions`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		/* an interval already degenerate at the slice positions is what toSourceInterval produces, so it survives both ways */
		val source = FinalInterval(longArrayOf(1, 2, 2, 3, 1), longArrayOf(4, 5, 2, 6, 1))
		val roundTripped = mapping.toSourceInterval(mapping.toXyzInterval(source))
		assertTrue(Intervals.equals(source, roundTripped), "expected ${Intervals.toString(source)}, got ${Intervals.toString(roundTripped)}")

		val xyz = FinalInterval(longArrayOf(1, 2, 3), longArrayOf(4, 5, 6))
		assertTrue(Intervals.equals(xyz, mapping.toXyzInterval(mapping.toSourceInterval(xyz))), "3D interval should survive the round trip")
	}

	@Test
	fun `toXyzInterval gives an absent dimension a singleton`() {
		/* x, y, c with no z: the z slot is synthesized, so it must be [0, 0] and not the channel extent */
		val mapping = SpatialMapping(3, intArrayOf(0, 1, -1), longArrayOf(0, 0, 2))
		val xyz = mapping.toXyzInterval(FinalInterval(longArrayOf(1, 2, 2), longArrayOf(4, 5, 2)))
		assertEquals(listOf(1L, 2L, 0L), (0 until 3).map { xyz.min(it) })
		assertEquals(listOf(4L, 5L, 0L), (0 until 3).map { xyz.max(it) })
	}

	@Test
	fun `a mapping is a snapshot of the arrays it was built from`() {
		val positions = longArrayOf(0, 0, 0, 2, 1)
		val axes = intArrayOf(0, 1, 2)
		val mapping = SpatialMapping(5, axes, positions)

		positions[3] = 3
		positions[4] = 0
		axes[2] = 0

		assertEquals(listOf(0L, 0L, 0L, 2L, 1L), mapping.slicePositions.toList(), "slice positions must not follow the caller's array")
		assertEquals(listOf(0, 1, 2), mapping.xyzSourceAxes.toList(), "xyz axes must not follow the caller's array")

		val view = mapping.toXyz(filled(longArrayOf(4, 4, 4, 4, 4)))
		val access = view.randomAccess().also { it.setPosition(longArrayOf(1, 2, 3)) }
		assertEquals(encode(longArrayOf(1, 2, 3, 2, 1)), access.get().get(), "the view must still read the original slice")
	}
}
