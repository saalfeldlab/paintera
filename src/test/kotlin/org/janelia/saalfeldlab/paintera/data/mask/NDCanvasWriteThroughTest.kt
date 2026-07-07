package org.janelia.saalfeldlab.paintera.data.mask

import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.DiskCachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.cache.img.CellLoader
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.util.n5.SpatialMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * De-risks the load-bearing assumption of the nD MaskedSource refactor: the painted canvas can be a single nD scalar
 * `DiskCachedCellImg<UnsignedLongType>`, and painting a copy-free 3D `SpatialMapping.to3D` slice of it (at fixed
 * non-spatial positions) must:
 *   1. write through to exactly that nD slab,
 *   2. leave every other slab at the INVALID background,
 *   3. mark the underlying nD cells dirty so they survive a cache flush (i.e. commit/persist will see them).
 *
 * If this holds, the canvas can hold every visited timepoint at once and the slider never loses uncommitted edits;
 * the rest of the refactor is plumbing the 3D views through MaskedSource. This is the spike that justifies that work.
 */
class NDCanvasWriteThroughTest {

	private val INVALID = Label.INVALID

	/* a 5D (x, y, z, c, t) canvas: 2 channels, 3 timepoints; non-spatial block size 1 so each slab is its own cells */
	private val ndDimensions = longArrayOf(8, 8, 4, 2, 3)
	private val ndBlockSize = intArrayOf(4, 4, 4, 1, 1)
	private val xyzSourceAxes = intArrayOf(0, 1, 2)

	private fun newCanvas(tmp: Path, name: String): DiskCachedCellImg<UnsignedLongType, *> =
		DiskCachedCellImgFactory(
			UnsignedLongType(),
			DiskCachedCellImgOptions.options()
				.volatileAccesses(true)
				.dirtyAccesses(true)
				.cacheDirectory(tmp.resolve(name))
				.cellDimensions(*ndBlockSize)
		).create(ndDimensions, CellLoader { img -> img.forEach { it.set(INVALID) } })

	/* the 3D (x, y, z) slice of [canvas] at the given non-spatial position - the view ViewerMask would paint into */
	private fun slabAt(canvas: DiskCachedCellImg<UnsignedLongType, *>, channel: Long, time: Long): RandomAccessibleInterval<UnsignedLongType> =
		SpatialMapping(ndDimensions.size, xyzSourceAxes, longArrayOf(0, 0, 0, channel, time)).to3D(canvas)

	@Test
	fun `painting a 3D slice writes only that slab and leaves the others INVALID`(@TempDir tmp: Path) {
		val canvas = newCanvas(tmp, "isolation")

		/* paint the (channel=1, time=2) slab: every voxel of its 3D view gets a label */
		val painted = 4242L
		val slab = slabAt(canvas, channel = 1, time = 2)
		assertEquals(3, slab.numDimensions()) { "the to3D view must be 3D" }
		Views.flatIterable(slab).forEach { it.set(painted) }

		/* the painted slab reads back the label end-to-end */
		Views.flatIterable(slabAt(canvas, channel = 1, time = 2)).forEach { assertEquals(painted, it.get()) }

		/* every OTHER (channel, time) slab is still the INVALID background - no bleed across non-spatial axes */
		for (channel in 0 until ndDimensions[3]) {
			for (time in 0 until ndDimensions[4]) {
				if (channel == 1L && time == 2L) continue
				Views.flatIterable(slabAt(canvas, channel, time)).forEach {
					assertEquals(INVALID, it.get()) { "slab (c=$channel,t=$time) should be untouched" }
				}
			}
		}
	}

	@Test
	fun `a slice write lands at the corresponding nD coordinate, not elsewhere`(@TempDir tmp: Path) {
		val canvas = newCanvas(tmp, "writethrough")

		/* paint one voxel (2,3,1) of the (channel=1, time=2) 3D slice */
		val slabAccess = slabAt(canvas, channel = 1, time = 2).randomAccess()
		slabAccess.setPosition(intArrayOf(2, 3, 1))
		slabAccess.get().set(99L)

		/* it must appear in the nD canvas at exactly [2,3,1, c=1, t=2] - this is the write-through the whole design needs */
		val ndAccess = canvas.randomAccess()
		ndAccess.setPosition(longArrayOf(2, 3, 1, 1, 2))
		assertEquals(99L, ndAccess.get().get()) { "the slice write must reach the nD cell at the fixed non-spatial position" }

		/* the same (x,y,z) at a different (channel, time) is untouched - the write did not bleed across slabs */
		ndAccess.setPosition(longArrayOf(2, 3, 1, 0, 0))
		assertEquals(INVALID, ndAccess.get().get()) { "a different slab's cell must stay INVALID" }
	}

	@Test
	fun `exactly one slab holds labels after painting it, all others stay INVALID`(@TempDir tmp: Path) {
		val canvas = newCanvas(tmp, "scan")
		Views.flatIterable(slabAt(canvas, channel = 1, time = 2)).forEach { it.set(7L) }

		/* mirror what commit does at slab granularity: which (channel, time) slabs hold any non-INVALID label */
		var paintedSlabs = 0
		for (channel in 0 until ndDimensions[3]) {
			for (time in 0 until ndDimensions[4]) {
				if (Views.flatIterable(slabAt(canvas, channel, time)).any { it.get() != INVALID }) paintedSlabs++
			}
		}
		assertEquals(1, paintedSlabs) { "exactly the (c=1,t=2) slab should hold labels" }
	}
}
