package org.janelia.saalfeldlab.util.n5

import net.imglib2.FinalInterval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.cell.CellGrid
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
	fun `toSource block size puts 1 at non-spatial axes`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		assertEquals(listOf(50, 60, 1, 70, 1), mapping.toSource(intArrayOf(50, 60, 70), IntArray(5) { 1 }).toList())
	}

	@Test
	fun `toSource interval reinserts fixed positions`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		val sourceInterval = mapping.toSource(net.imglib2.FinalInterval(longArrayOf(1, 2, 3), longArrayOf(4, 5, 6)))
		assertEquals(listOf(1L, 2L, 2L, 3L, 1L), (0 until 5).map { sourceInterval.min(it) })
		assertEquals(listOf(4L, 5L, 2L, 6L, 1L), (0 until 5).map { sourceInterval.max(it) })
	}

	@Test
	fun `toSpatial and toSource positions round-trip at the slice positions`() {
		/* XYCZT: spatial on 0, 1, 3 */
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		assertEquals(listOf(4L, 5L, 6L), mapping.toSpatial(longArrayOf(4, 5, 2, 6, 1), 0L).toList())
		assertEquals(listOf(4L, 5L, 2L, 6L, 1L), mapping.toSource(longArrayOf(4, 5, 6)).toList())

		/* an absent dimension projects to 0, unlike a shape, where it is the singleton extent 1 */
		val embedded = SpatialMapping(2, intArrayOf(0, -1, 1), longArrayOf(0, 0))
		assertEquals(listOf(3L, 0L, 7L), embedded.toSpatial(longArrayOf(3, 7), 0L).toList())
		assertEquals(listOf(50L, 1L, 70L), embedded.toSpatial(longArrayOf(50, 70), 1L).toList())
	}

	@Test
	fun `the shape projections reject a shape of the wrong length`() {
		/* a shape of the wrong length reads the wrong axes and still returns a plausible-looking grid */
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		assertThrows(IllegalArgumentException::class.java) { mapping.toSpatial(intArrayOf(8, 9, 10), 1) }
		assertThrows(IllegalArgumentException::class.java) { mapping.toSpatial(longArrayOf(8, 9, 10), 1L) }
		assertThrows(IllegalArgumentException::class.java) { mapping.toSource(intArrayOf(8, 9, 10, 1, 1), IntArray(5) { 1 }) }

		/* an absent spatial dimension still projects to a singleton, which is its real extent */
		val embedded = SpatialMapping(2, intArrayOf(0, -1, 1), longArrayOf(0, 0))
		assertEquals(listOf(50, 1, 70), embedded.toSpatial(intArrayOf(50, 70), 1).toList())
	}

	@Test
	fun `toSpatial interval drops the non-spatial axes`() {
		/* XYCZT: spatial on 0, 1, 3; the channel (2) and time (4) extents must not reach the 3D interval */
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		val xyz = mapping.toSpatial(FinalInterval(longArrayOf(1, 2, 2, 3, 1), longArrayOf(4, 5, 2, 6, 1)), 0L)
		assertEquals(listOf(1L, 2L, 3L), (0 until 3).map { xyz.min(it) })
		assertEquals(listOf(4L, 5L, 6L), (0 until 3).map { xyz.max(it) })
	}

	@Test
	fun `toSpatial and toSource intervals round-trip at the slice positions`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 3), longArrayOf(0, 0, 2, 0, 1))
		/* an interval already degenerate at the slice positions is what toSourceInterval produces, so it survives both ways */
		val source = FinalInterval(longArrayOf(1, 2, 2, 3, 1), longArrayOf(4, 5, 2, 6, 1))
		val roundTripped = mapping.toSource(mapping.toSpatial(source, 0L))
		assertTrue(Intervals.equals(source, roundTripped), "expected ${Intervals.toString(source)}, got ${Intervals.toString(roundTripped)}")

		val xyz = FinalInterval(longArrayOf(1, 2, 3), longArrayOf(4, 5, 6))
		assertTrue(Intervals.equals(xyz, mapping.toSpatial(mapping.toSource(xyz), 0L)), "3D interval should survive the round trip")
	}

	@Test
	fun `toSpatial interval gives an absent dimension a singleton`() {
		/* x, y, c with no z: the z slot is synthesized, so it must be [0, 0] and not the channel extent */
		val mapping = SpatialMapping(3, intArrayOf(0, 1, -1), longArrayOf(0, 0, 2))
		val xyz = mapping.toSpatial(FinalInterval(longArrayOf(1, 2, 2), longArrayOf(4, 5, 2)), 0L)
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

	@Test
	fun `toSpatialOrNull keeps a block that covers the slice`() {
		/* xyzct sliced at c=1, t=3; the block spans t 2..3, so it covers the slice */
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 2), longArrayOf(0, 0, 0, 1, 3))
		val block = FinalInterval(longArrayOf(0, 0, 0, 1, 2), longArrayOf(31, 31, 31, 1, 3))
		val xyz = mapping.toSpatialOrNull(block, 0L)
		assertTrue(xyz != null, "a block spanning the sliced position must be kept")
		assertEquals(listOf(0L, 0L, 0L), (0 until 3).map { xyz!!.min(it) })
		assertEquals(listOf(31L, 31L, 31L), (0 until 3).map { xyz!!.max(it) })
	}

	@Test
	fun `toSpatialOrNull drops a block from another slice`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 2), longArrayOf(0, 0, 0, 1, 3))
		/* same spatial block, but only timepoints 0..1 */
		assertEquals(null, mapping.toSpatialOrNull(FinalInterval(longArrayOf(0, 0, 0, 1, 0), longArrayOf(31, 31, 31, 1, 1)), 0L))
		/* right timepoint, wrong channel */
		assertEquals(null, mapping.toSpatialOrNull(FinalInterval(longArrayOf(0, 0, 0, 0, 3), longArrayOf(31, 31, 31, 0, 3)), 0L))
	}

	@Test
	fun `toSpatialOrNull reads the non-spatial axes by role, not by index`() {
		/* cxyzt: the channel is axis 0, so a positional check would test x against the channel slice */
		val mapping = SpatialMapping(5, intArrayOf(1, 2, 3), longArrayOf(2, 0, 0, 0, 1))
		val covering = FinalInterval(longArrayOf(2, 10, 20, 30, 1), longArrayOf(2, 41, 51, 61, 1))
		val xyz = mapping.toSpatialOrNull(covering, 0L)
		assertEquals(listOf(10L, 20L, 30L), (0 until 3).map { xyz!!.min(it) })
		assertEquals(listOf(41L, 51L, 61L), (0 until 3).map { xyz!!.max(it) })
		assertEquals(null, mapping.toSpatialOrNull(FinalInterval(longArrayOf(0, 10, 20, 30, 1), longArrayOf(0, 41, 51, 61, 1)), 0L))
	}

	@Test
	fun `toSpatial over blocks keeps the blocks at the slice and drops the others`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 2), longArrayOf(0, 0, 0, 1, 3))
		val atSlice = FinalInterval(longArrayOf(0, 0, 0, 1, 3), longArrayOf(31, 31, 31, 1, 3))
		val otherSlice = FinalInterval(longArrayOf(32, 0, 0, 1, 0), longArrayOf(63, 31, 31, 1, 0))
		val alsoAtSlice = FinalInterval(longArrayOf(64, 0, 0, 0, 0), longArrayOf(95, 31, 31, 1, 3))

		val xyz = mapping.toSpatial(listOf(atSlice, otherSlice, alsoAtSlice), 0L)
		assertEquals(2, xyz.size, "the block at another slice must be dropped")
		xyz.forEach { assertEquals(3, it.numDimensions(), "every block must be 3D") }
		assertEquals(listOf(0L, 64L), xyz.map { it.min(0) })
	}

	@Test
	fun `toSpatial over blocks permutes 3D blocks on a permuted 3D source`() {
		/* zyx: a stored block's axis 0 is z */
		val mapping = SpatialMapping(3, intArrayOf(2, 1, 0), longArrayOf(0, 0, 0))
		val stored = FinalInterval(longArrayOf(0, 10, 20), longArrayOf(7, 17, 27))
		val xyz = mapping.toSpatial(listOf(stored), 0L).single()
		assertEquals(listOf(20L, 10L, 0L), (0 until 3).map { xyz.min(it) })
		assertEquals(listOf(27L, 17L, 7L), (0 until 3).map { xyz.max(it) })
	}

	@Test
	fun `toBlockMapping divides each slice position by the block size along its axis`() {
		val mapping = SpatialMapping(5, intArrayOf(0, 1, 2), longArrayOf(5, 6, 7, 1, 3))
		val grid = CellGrid(longArrayOf(64, 64, 64, 2, 4), intArrayOf(32, 32, 32, 1, 2))
		val blockMapping = mapping.toBlockMapping(grid)
		assertEquals(listOf(0, 1, 2), blockMapping.xyzSourceAxes.toList())
		assertEquals(listOf(1L, 1L), blockMapping.slicePositions.drop(3), "c=1 in blocks of 1 is 1; t=3 in blocks of 2 is 1")
		assertThrows(IllegalArgumentException::class.java) { mapping.toBlockMapping(CellGrid(longArrayOf(64, 64, 64), intArrayOf(32, 32, 32))) }
	}
}
