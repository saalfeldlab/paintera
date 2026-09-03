package org.janelia.saalfeldlab.paintera.control.assignment

import com.google.gson.GsonBuilder
import javafx.util.Pair
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FragmentSegmentAssignmentActionHistoryTest {

	private fun assignment(persister: FragmentSegmentAssignmentOnlyLocal.Persister = FragmentSegmentAssignmentOnlyLocal.Persister { _, _ -> }) =
		FragmentSegmentAssignmentOnlyLocal(FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE, persister)

	private fun merges(count: Int) = (1..count).map { Merge(it * 2L, it * 2L + 1, 100L + it) as AssignmentAction }

	@Test
	fun `undone actions are restored, not collapsed to the last one`() {
		val actions = merges(4)
		val restored = assignment()

		/* what loading a project does with a history that was entirely undone before saving */
		restored.applyWithEnabledFlag(actions.map { Pair(it, false) })

		assertEquals(actions.size, restored.events().size) { "every action should be restored" }
		assertEquals(actions, restored.events().map { it.key }) { "restored actions should keep their order" }
	}

	@Test
	fun `restoring undone actions does not apply them to the lut`() {
		val actions = merges(3)
		val restored = assignment()

		restored.applyWithEnabledFlag(actions.map { Pair(it, false) })

		actions.map { it as Merge }.forEach { merge ->
			assertEquals(merge.fromFragmentId, restored.getSegment(merge.fromFragmentId)) { "a disabled merge must not be applied" }
			assertEquals(merge.intoFragmentId, restored.getSegment(merge.intoFragmentId)) { "a disabled merge must not be applied" }
		}
	}

	@Test
	fun `restoring a mix applies only the enabled actions`() {
		val actions = merges(3).map { it as Merge }
		val restored = assignment()

		restored.applyWithEnabledFlag(listOf(Pair(actions[0], false), Pair(actions[1], true), Pair(actions[2], false)))

		assertEquals(actions[1].segmentId, restored.getSegment(actions[1].fromFragmentId)) { "the enabled merge should be applied" }
		assertEquals(actions[0].fromFragmentId, restored.getSegment(actions[0].fromFragmentId)) { "the disabled merges should not be" }
		assertEquals(actions[2].fromFragmentId, restored.getSegment(actions[2].fromFragmentId)) { "the disabled merges should not be" }
	}

	@Test
	fun `undone actions are not persistable data`() {
		val assignment = assignment()
		assignment.applyWithEnabledFlag(merges(3).map { Pair(it, false) })

		assertFalse(assignment.hasPersistableData()) { "nothing is applied, so there is nothing to commit" }
	}

	@Test
	fun `a single enabled action is persistable data`() {
		val assignment = assignment()
		val actions = merges(3)
		assignment.applyWithEnabledFlag(listOf(Pair(actions[0], false), Pair(actions[1], true), Pair(actions[2], false)))

		assertTrue(assignment.hasPersistableData()) { "one action is applied, so it can be committed" }
	}

	@Test
	fun `assignments that cannot be persisted are never persistable data`() {
		val assignment = assignment(FragmentSegmentAssignmentOnlyLocal.doesNotPersist("no backend for this source"))
		assignment.apply(merges(2))

		assertTrue(assignment.events().size == 2) { "the actions are still tracked for the Paintera project" }
		assertFalse(assignment.hasPersistableData()) { "but they must not be offered for commit" }
	}

	@Test
	fun `deleting an action removes only that one`() {
		val assignment = assignment()
		val actions = merges(3)
		assignment.apply(actions)

		assignment.history.delete(assignment.events()[1].key)

		assertEquals(listOf(actions[0], actions[2]), assignment.events().map { it.key })
	}

	@Test
	fun `deleting an applied action unapplies it`() {
		val assignment = assignment()
		val merge = Merge(2L, 3L, 3L)
		assignment.apply(merge)
		assertEquals(3L, assignment.getSegment(2L)) { "the merge should be applied" }

        assignment.history.delete(assignment.events()[0].key)

		assertEquals(2L, assignment.getSegment(2L)) { "the fragment should be back on its own segment" }
	}

	@Test
	fun `deleting all actions clears the history`() {
		val assignment = assignment()
		assignment.apply(merges(3))

        assignment.history.deleteAll()

		assertTrue(assignment.events().isEmpty())
		assertFalse(assignment.hasPersistableData())
	}

	@Test
	fun `committed actions are not stored in the Paintera project`() {
		val assignment = assignment()
		assignment.apply(merges(3))
		assertEquals(3, FragmentSegmentAssignmentActions(assignment).events.size) { "the actions are stored until committed" }

		assignment.persist()

		assertTrue(FragmentSegmentAssignmentActions(assignment).events.isEmpty()) { "the backend holds them now, the project must not" }
		assertEquals(101L, assignment.getSegment(2L)) { "the committed assignment is still applied" }
	}

	@Test
	fun `disabled actions survive a round trip through the adapter`() {
		val gson = GsonBuilder()
			.registerTypeAdapter(FragmentSegmentAssignmentActions::class.java, FragmentSegmentAssignmentActions.Adapter())
			.create()
		val actions = merges(3)
		val stored = FragmentSegmentAssignmentActions(Pair(actions[0], false), Pair(actions[1], true), Pair(actions[2], false))

		val json = gson.toJsonTree(stored)
		val serializedActions = json.asJsonObject.getAsJsonArray("actions")

		assertEquals(3, serializedActions.size()) { "disabled actions are serialized too" }
		assertTrue(serializedActions[0].asJsonObject["isDisabled"].asBoolean) { "a disabled action is flagged" }
		assertFalse(serializedActions[1].asJsonObject.has("isDisabled")) { "enabled is the default, so the flag is omitted" }

		val restored = gson.fromJson(json, FragmentSegmentAssignmentActions::class.java)

		assertEquals(listOf(false, true, false), restored.events.map { it.value }) { "the enabled flags are restored" }
		/* Merge has no equals, so compare the ids it prints */
		assertEquals(actions.map { it.toString() }, restored.events.map { it.key.toString() }) { "the actions are restored in order" }
	}

	@Test
	fun `a failed commit keeps the actions`() {
		val assignment = assignment { _, _ -> throw UnableToPersist("cannot write") }
		assignment.apply(merges(2))

		assertThrows(UnableToPersist::class.java) { assignment.persist() }

		assertEquals(2, assignment.events().size) { "nothing was written, so the actions must survive for the project" }
		assertTrue(assignment.hasPersistableData()) { "and can be committed again" }
	}
}
