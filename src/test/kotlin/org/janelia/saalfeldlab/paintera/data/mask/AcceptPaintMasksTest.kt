package org.janelia.saalfeldlab.paintera.data.mask

import gnu.trove.set.hash.TLongHashSet
import net.imglib2.FinalInterval
import net.imglib2.RandomAccessible
import net.imglib2.img.array.ArrayImg
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.label.Label
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.integer.UnsignedLongType
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.mask.AcceptPaintMasks.rejectLockedLabels
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.function.LongPredicate
import kotlin.test.assertEquals

/** Mask over a paint operation that accepts or rejects modifications to the underlying data */
class AcceptPaintMasksTest {

	private val dimensions = longArrayOf(32, 8, 1)
	private val grid = CellGrid(dimensions, intArrayOf(8, 8, 1))
	private val numBlocks = grid.gridDimensions.fold(1L) { acc, d -> acc * d }.toInt()
	private val interval = FinalInterval(LongArray(3) { 0L }, LongArray(3) { dimensions[it] - 1 })

	private val painted = 9L

	/* existing label per stripe of 8 columns: 5 | 6 | 7 | background */
	private val stripeLabels = longArrayOf(5, 6, 7, Label.BACKGROUND)

	private fun stripeOf(x: Long) = (x / 8).toInt()

	private fun background(): ArrayImg<UnsignedLongType, *> = ArrayImgs.unsignedLongs(*dimensions).also { img ->
		val cursor = img.localizingCursor()
		while (cursor.hasNext())
			cursor.next().set(stripeLabels[stripeOf(cursor.getLongPosition(0))])
	}

	private fun unpaintedCanvas(): ArrayImg<UnsignedLongType, *> = ArrayImgs.unsignedLongs(*dimensions).also { img -> img.forEach { it.set(Label.INVALID) } }

	private fun paintedStripes(
		acceptPaintMask: RandomAccessible<BoolType>?,
		canvas: ArrayImg<UnsignedLongType, *> = unpaintedCanvas(),
	): Set<Int> {
		val maskRai = ArrayImgs.unsignedLongs(*dimensions).apply { forEach { it.set(painted) } }
		val mask = SourceMask(MaskInfo(0, 0), maskRai, null, null, null, null)
		val blocks = TLongHashSet().apply { for (block in 0 until numBlocks) add(block.toLong()) }
		val pool = Executors.newFixedThreadPool(4)
		try {
			MaskedSource.paintAffectedPixels(blocks, mask, canvas, grid, interval, { it == painted }, acceptPaintMask, pool)
		} finally {
			pool.shutdown()
		}

		val stripes = mutableSetOf<Int>()
		val cursor = canvas.localizingCursor()
		while (cursor.hasNext()) {
			val value = cursor.next().get()
			if (value == painted)
				stripes += stripeOf(cursor.getLongPosition(0))
		}
		return stripes
	}

    private fun accept(maskLockedIds: LongPredicate? = null, segmentMask: AcceptPaintMasks.SegmentMask? = null) =
        AcceptPaintMasks.compose(unpaintedCanvas(), background(), listOfNotNull(maskLockedIds?.let(::rejectLockedLabels), segmentMask))

	/* SelectedSegments only computes its segments on a change, so the ids are activated after it exists */
	private fun selectedSegments(vararg labels: Long, assignment: FragmentSegmentAssignmentOnlyLocal = newAssignment()): SelectedSegments {
		val selectedIds = SelectedIds()
		return SelectedSegments(selectedIds, assignment).also { selectedIds.activate(*labels) }
	}

	private fun newAssignment() =
		FragmentSegmentAssignmentOnlyLocal(FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE) { _, _ -> }

    @Test
	fun `no accept mask paints everywhere`() {
		assertEquals(setOf(0, 1, 2, 3), paintedStripes(null))
	}

	@Test
	fun `locked labels are not painted over`() {
		assertEquals(setOf(1, 3), paintedStripes(accept(maskLockedIds = { it == 5L || it == 7L })))
	}

	@Test
	fun `canvas values take priorite over background values`() {
		/* stripe 0: canvas 6 over a locked background 5; stripe 1: locked canvas 5 over background 6 */
		val canvas = unpaintedCanvas()
		val cursor = canvas.localizingCursor()
		while (cursor.hasNext()) {
			val type = cursor.next()
			when (stripeOf(cursor.getLongPosition(0))) {
				0 -> type.set(6)
				1 -> type.set(5)
			}
		}
		val mask = AcceptPaintMasks.compose(canvas, background(), listOf(rejectLockedLabels { it == 5L }))
		assertEquals(setOf(0, 2, 3), paintedStripes(mask, canvas))
	}

	@Test
	fun `whitelist paints only the listed labels`() {
		assertEquals(setOf(1), paintedStripes(accept(segmentMask = AcceptPaintMasks.SegmentMask.Whitelist(selectedSegments(6)))))
	}

	@Test
	fun `whitelisting background paints only unlabeled voxels`() {
		assertEquals(setOf(3), paintedStripes(accept(segmentMask = AcceptPaintMasks.SegmentMask.Whitelist(selectedSegments(Label.BACKGROUND)))))
	}

	@Test
	fun `blacklist paints everything but the listed labels`() {
		assertEquals(setOf(0, 2, 3), paintedStripes(accept(segmentMask = AcceptPaintMasks.SegmentMask.Blacklist(selectedSegments(6)))))
	}

	@Test
	fun `lock with a blacklist rejects what either rejects`() {
		val mask = accept(maskLockedIds = { it == 5L }, segmentMask = AcceptPaintMasks.SegmentMask.Blacklist(selectedSegments(6)))
		assertEquals(setOf(2, 3), paintedStripes(mask))
	}

	@Test
	fun `lock with a whitelist rejects a whitelisted label that is locked`() {
		val mask = accept(maskLockedIds = { it == 6L }, segmentMask = AcceptPaintMasks.SegmentMask.Whitelist(selectedSegments(5, 6)))
		assertEquals(setOf(0), paintedStripes(mask))
	}

	@Test
	fun `a list matches every fragment of a listed segment`() {
		val assignment = newAssignment()
		/* fragments 6 and 7 are one segment, 100 */
		assignment.apply(Merge(6, 7, 100))

		val whitelisted = accept(segmentMask = AcceptPaintMasks.SegmentMask.Whitelist(selectedSegments(100, assignment = assignment)))
		assertEquals(setOf(1, 2), paintedStripes(whitelisted))

		val listedByFragment = accept(segmentMask = AcceptPaintMasks.SegmentMask.Blacklist(selectedSegments(6, assignment = assignment)))
		assertEquals(setOf(0, 3), paintedStripes(listedByFragment))
	}

	@Test
	fun `no rule gives no mask`() {
		assertEquals(null, accept())
	}
}
