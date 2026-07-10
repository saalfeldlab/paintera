package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close

import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.view.Views
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClosedCellImageTest {

	private val BACKGROUND_ONLY = longArrayOf(0L)

	private fun valueAt(img: RandomAccessibleInterval<UnsignedLongType>, vararg pos: Long): Long {
		val access = img.randomAccess()
		access.setPosition(pos)
		return access.get().get()
	}

	private fun setValueAt(img: RandomAccessibleInterval<UnsignedLongType>, value: Long, vararg pos: Long) {
		val access = img.randomAccess()
		access.setPosition(pos)
		access.get().set(value)
	}

	@Test
	fun `fills single voxel gaps along any axis`() {
		val img = ArrayImgs.unsignedLongs(5, 5, 5)

		/* 1-0-1 along x at (1..3, 2, 2) */
		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 1L, 3, 2, 2)

		val closed = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY).img

		assertEquals(1L, valueAt(closed, 2, 2, 2), "gap between axis-adjacent labels should fill")
		assertEquals(1L, valueAt(closed, 1, 2, 2))
		assertEquals(1L, valueAt(closed, 3, 2, 2))
		/* voxels adjacent on only one side stay unchanged */
		assertEquals(0L, valueAt(closed, 0, 2, 2))
		assertEquals(0L, valueAt(closed, 4, 2, 2))
		assertEquals(0L, valueAt(closed, 2, 3, 2))
	}

	@Test
	fun `does not fill diagonal-only adjacency`() {
		val img = ArrayImgs.unsignedLongs(5, 5, 5)

		setValueAt(img, 1L, 1, 1, 2)
		setValueAt(img, 1L, 3, 3, 2)

		val closed = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY).img
		assertEquals(0L, valueAt(closed, 2, 2, 2), "diagonal neighbors should not fill the gap")
	}

	@Test
	fun `different labels only bridge when their listed segment matches`() {
		val img = ArrayImgs.unsignedLongs(5, 5, 5)

		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 2L, 3, 2, 2)

		/* both fragments in the same segment, expressed by the lookup */
		val segmentLookup = { fragment: Long -> if (fragment == 1L || fragment == 2L) 9L else fragment }

		val fragmentsOnly = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L, 2L), 1, 1, BACKGROUND_ONLY, fragmentSegmentLookup = segmentLookup).img
		assertEquals(0L, valueAt(fragmentsOnly, 2, 2, 2), "different fragments do not bridge when only the fragments are listed")

		val segmentListed = ClosedCellImage.createClosedCellImage(img, longArrayOf(9L), 1, 1, BACKGROUND_ONLY, fragmentSegmentLookup = segmentLookup).img
		assertEquals(1L, valueAt(segmentListed, 2, 2, 2), "fragments of a listed segment bridge and fill with the negative-side label")
	}

	@Test
	fun `does not fill when only one neighbor is a target label`() {
		val img = ArrayImgs.unsignedLongs(5, 5, 5)

		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 7L, 3, 2, 2)

		val closed = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY).img
		assertEquals(0L, valueAt(closed, 2, 2, 2))
	}

	@Test
	fun `only whitelisted labels fill`() {
		val img = ArrayImgs.unsignedLongs(7, 5, 5)

		/* 1-0-1 and 1-9-1 along x */
		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 1L, 3, 2, 2)
		setValueAt(img, 9L, 4, 2, 2)
		setValueAt(img, 1L, 5, 2, 2)

		val backgroundOnly = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY).img
		assertEquals(1L, valueAt(backgroundOnly, 2, 2, 2), "background gap fills")
		assertEquals(9L, valueAt(backgroundOnly, 4, 2, 2), "non-whitelisted label is preserved")

		val withNine = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, longArrayOf(0L, 9L)).img
		assertEquals(1L, valueAt(withNine, 4, 2, 2), "whitelisted label is overwritten")
	}

	@Test
	fun `null fillable labels fill any regular label`() {
		val img = ArrayImgs.unsignedLongs(5, 5, 5)

		/* 1-9-1 along x; 9 is not whitelisted anywhere */
		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 9L, 2, 2, 2)
		setValueAt(img, 1L, 3, 2, 2)

		val closed = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, fillableLabels = null).img
		assertEquals(1L, valueAt(closed, 2, 2, 2), "any regular label fills when fillable labels are unrestricted")
	}

	@Test
	fun `gap size bounds the fillable run length`() {
		val img = ArrayImgs.unsignedLongs(9, 5, 5)

		/* 1-0-0-1 along x */
		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 1L, 4, 2, 2)

		val gapOne = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY).img
		assertEquals(0L, valueAt(gapOne, 2, 2, 2), "two voxel run does not fill with gap size 1")
		assertEquals(0L, valueAt(gapOne, 3, 2, 2))

		val gapTwo = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 2, 1, BACKGROUND_ONLY).img
		assertEquals(1L, valueAt(gapTwo, 2, 2, 2), "two voxel run fills with gap size 2")
		assertEquals(1L, valueAt(gapTwo, 3, 2, 2))
	}

	@Test
	fun `non-whitelisted voxel blocks the whole run`() {
		val img = ArrayImgs.unsignedLongs(9, 5, 5)

		/* 1-0-9-1 along x; 9 is not fillable so the 0 must not fill either */
		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 9L, 3, 2, 2)
		setValueAt(img, 1L, 4, 2, 2)

		val closed = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 3, 1, BACKGROUND_ONLY).img
		assertEquals(0L, valueAt(closed, 2, 2, 2))
		assertEquals(9L, valueAt(closed, 3, 2, 2))
	}

	@Test
	fun `iterations let earlier fills flank later ones`() {
		val img = ArrayImgs.unsignedLongs(5, 7, 5)

		/* 1-0-1 along x fills (2,2,2) in the first pass; only then is (2,3,2) flanked along y by (2,2,2) and (2,4,2) */
		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 1L, 3, 2, 2)
		setValueAt(img, 1L, 2, 4, 2)

		val onePass = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY).img
		assertEquals(1L, valueAt(onePass, 2, 2, 2))
		assertEquals(0L, valueAt(onePass, 2, 3, 2), "second-order gap stays open with one iteration")

		val twoPasses = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 2, BACKGROUND_ONLY).img
		assertEquals(1L, valueAt(twoPasses, 2, 2, 2))
		assertEquals(1L, valueAt(twoPasses, 2, 3, 2), "second-order gap fills with two iterations")
	}

	@Test
	fun `specified fill label replaces the flanking label and bridges with targets in later passes`() {
		val img = ArrayImgs.unsignedLongs(5, 7, 5)

		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 1L, 3, 2, 2)
		setValueAt(img, 1L, 2, 4, 2)

		val onePass = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY, replacementLabel = 7L).img
		assertEquals(7L, valueAt(onePass, 2, 2, 2), "gap fills with the specified label")
		assertEquals(1L, valueAt(onePass, 1, 2, 2), "target labels are preserved")

		/* (2,3,2) is flanked along y by the pass-1 fill (7) and the target (1); the fill label bridges with any target */
		val twoPasses = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 2, BACKGROUND_ONLY, replacementLabel = 7L).img
		assertEquals(7L, valueAt(twoPasses, 2, 3, 2), "the specified label bridges with the target label")
		assertEquals(7L, valueAt(twoPasses, 2, 2, 2), "the pass-1 fill is preserved")
	}

	@Test
	fun `specified fills bridge with each other in later passes`() {
		val img = ArrayImgs.unsignedLongs(5, 7, 5)

		/* two 1-0-1 x gaps fill with 7 in pass 1; the voxel between them is then flanked 7-0-7 along y */
		setValueAt(img, 1L, 1, 2, 2)
		setValueAt(img, 1L, 3, 2, 2)
		setValueAt(img, 1L, 1, 4, 2)
		setValueAt(img, 1L, 3, 4, 2)

		val twoPasses = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 2, BACKGROUND_ONLY, replacementLabel = 7L).img
		assertEquals(7L, valueAt(twoPasses, 2, 2, 2))
		assertEquals(7L, valueAt(twoPasses, 2, 4, 2))
		assertEquals(7L, valueAt(twoPasses, 2, 3, 2), "same-label specified fills bridge each other")
	}

	@Test
	fun `image border neighbors do not fill`() {
		val img = ArrayImgs.unsignedLongs(3, 3, 3)

		/* 1 at (1,0,0); the voxel at (0,0,0) has an out-of-bounds neighbor on the other side */
		setValueAt(img, 1L, 1, 0, 0)

		val closed = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY).img
		assertEquals(0L, valueAt(closed, 0, 0, 0))
	}

	@Test
	fun `fills across cell boundaries`() {
		val img = ArrayImgs.unsignedLongs(9, 9, 9)

		/* gap straddles the 4|5 cell boundary with 4-cubed cells */
		setValueAt(img, 1L, 3, 4, 4)
		setValueAt(img, 1L, 5, 4, 4)

		val closed = ClosedCellImage.createClosedCellImage(img, longArrayOf(1L), 1, 1, BACKGROUND_ONLY, cellDimensions = intArrayOf(4, 4, 4)).img
		Views.flatIterable(closed).forEach { /* force all cells */ }
		assertEquals(1L, valueAt(closed, 4, 4, 4))
	}
}
