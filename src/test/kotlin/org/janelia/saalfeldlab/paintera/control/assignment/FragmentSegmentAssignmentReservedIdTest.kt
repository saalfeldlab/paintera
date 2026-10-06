package org.janelia.saalfeldlab.paintera.control.assignment

import com.google.gson.GsonBuilder
import gnu.trove.map.TLongLongMap
import gnu.trove.map.hash.TLongLongHashMap
import javafx.util.Pair
import net.imglib2.type.label.Label
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.id.LocalIdService
import org.janelia.saalfeldlab.paintera.serialization.SelectedIdsSerializer
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Supplier

class FragmentSegmentAssignmentReservedIdTest {

	private val reservedIds = longArrayOf(Label.INVALID, Label.TRANSPARENT, Label.OUTSIDE)

	private fun assignment(initialLut: TLongLongMap = TLongLongHashMap()) =
		FragmentSegmentAssignmentOnlyLocal(Supplier { initialLut }, FragmentSegmentAssignmentOnlyLocal.Persister { _, _ -> })

	private fun saveAndRestore(selectedIds: SelectedIds): SelectedIds {
		val gson = GsonBuilder().registerTypeAdapter(SelectedIds::class.java, SelectedIdsSerializer()).create()
		return gson.fromJson(gson.toJson(selectedIds), SelectedIds::class.java)
	}

	@Test
	fun `INVALID is never an active id`() {
		val selectedIds = SelectedIds()
		selectedIds.activate(3)
		selectedIds.activateAlso(Label.INVALID)
		assertArrayEquals(longArrayOf(3), selectedIds.activeIdsCopyAsArray)

		selectedIds.activate(Label.INVALID)
		assertTrue(selectedIds.isEmpty)
	}

	@Test
	fun `a selection saved without a last selection is restored without INVALID`() {
		val selectedIds = SelectedIds().apply {
			activate(3, 4)
			deactivate(3)
		}
		assertFalse(selectedIds.isLastSelectionValid)

		val restored = saveAndRestore(selectedIds)
		assertArrayEquals(longArrayOf(4), restored.activeIdsCopyAsArray)
		/* the saved INVALID last selection is ignored; the restored active id becomes the last selection */
		assertEquals(4, restored.lastSelection)
	}

	@Test
	fun `merging all selected after restoring an empty selection does not merge INVALID`() {
		val assignment = assignment()
		val selectedIds = saveAndRestore(SelectedIds())
		selectedIds.activateAlso(1)
		selectedIds.activateAlso(2)

		FragmentSegmentAssignment.mergeAllSelected(assignment, selectedIds, LocalIdService(10))

		val segment = assignment.getSegment(1)
		assertEquals(segment, assignment.getSegment(2))
		assertEquals(Label.INVALID, assignment.getSegment(Label.INVALID))
		assertFalse(assignment.getFragments(segment).contains(Label.INVALID))
	}

	@Test
	fun `a reserved id cannot be merged`() {
		val assignment = assignment()
		val idService = LocalIdService(10)
		for (reserved in reservedIds) {
			assertTrue(assignment.getMergeAction(1, reserved) { idService.next() }.isEmpty)
			assertTrue(assignment.getMergeAction(reserved, 1) { idService.next() }.isEmpty)
			assertEquals(reserved, assignment.getSegment(reserved))
		}
		assertEquals(1, assignment.getSegment(1))
	}

	@Test
	fun `a reserved id cannot become a segment`() {
		for (reserved in reservedIds) {
			val assignment = assignment()
			assertTrue(assignment.getMergeAction(1, 2) { reserved }.isEmpty)
			assertEquals(1, assignment.getSegment(1))
			assertEquals(2, assignment.getSegment(2))
		}
	}

	@Test
	fun `a saved merge with a reserved id is dropped from the actions`() {
		val assignment = assignment()
		val kept = Merge(5, 6, 22)
		val merges = listOf<AssignmentAction>(
			Merge(1, Label.INVALID, 20),
			Merge(Label.INVALID, 2, 21),
			Merge(3, 4, Label.INVALID),
			kept,
		)
		assignment.applyWithEnabledFlag(merges.map { Pair(it, true) })

		for (id in longArrayOf(1, 2, 3, 4, Label.INVALID))
			assertEquals(id, assignment.getSegment(id))
		assertEquals(22, assignment.getSegment(5))
		/* only the kept merge is left to persist or serialize */
		assertEquals(listOf<AssignmentAction>(kept), assignment.events().map { it.key })
		assertEquals(listOf<AssignmentAction>(kept), FragmentSegmentAssignmentActions(assignment).events.map { it.key })
	}

	@Test
	fun `a saved lut entry with a reserved id is dropped`() {
		val lut = TLongLongHashMap().apply {
			put(Label.INVALID, 20)
			put(1, 20)
			put(2, Label.INVALID)
		}
		val assignment = assignment(lut)

		assertEquals(20, assignment.getSegment(1))
		assertEquals(2, assignment.getSegment(2))
		assertEquals(Label.INVALID, assignment.getSegment(Label.INVALID))
		assertFalse(assignment.getFragments(20).contains(Label.INVALID))
	}
}
