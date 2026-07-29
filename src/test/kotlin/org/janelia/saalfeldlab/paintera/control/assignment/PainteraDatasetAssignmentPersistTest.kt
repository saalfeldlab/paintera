package org.janelia.saalfeldlab.paintera.control.assignment

import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.fragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The commit dialog only offers the fragment-segment assignment when [FragmentSegmentAssignmentState.hasPersistableData]
 * says so, which now also requires that the backend can be written at all. These check that a real Paintera label
 * group still qualifies.
 */
@TestInstance(PER_CLASS)
class PainteraDatasetAssignmentPersistTest {

	@BeforeAll
	fun setup() = TestData.registerLabelBlockLookupAdapter()

	private val testCase: TestCase = TestData.creatableLabelDatasetCases.first { !it.isLabelMultiset }

	private fun painteraLabelGroup(tmp: Path, group: String = "labels"): N5ContainerState {
		val writer = TestData.newWriter(testCase, tmp)
		N5Data.createPainteraLabelDataset(
			writer,
			group,
			TestData.defaultDimensions(testCase),
			testCase.shape.blockSize.map { it.toInt() }.toIntArray(),
			doubleArrayOf(1.0, 1.0, 1.0),
			doubleArrayOf(0.0, 0.0, 0.0),
			arrayOf(doubleArrayOf(2.0, 2.0, 2.0)),
			"pixel",
			null,
			false,
			false
		)
		return N5ContainerState(writer)
	}

	@Test
	fun `a paintera label group can commit its assignment`(@TempDir tmp: Path) {
		val containerState = painteraLabelGroup(tmp)
		val metadataState = MetadataUtils.createMetadataState(containerState, "labels")!!
		val assignment = metadataState.fragmentSegmentAssignmentState

		assertTrue(assignment.persister.canPersist()) { "a writable paintera group must be able to persist: ${assignment.persister.persistError}" }
		assertFalse(assignment.hasPersistableData()) { "nothing has been merged yet" }

		assignment.apply(Merge(2L, 3L, 10L))

		assertTrue(assignment.hasPersistableData()) { "a merge on a paintera group should be offered for commit" }
	}

	@Test
	fun `committing writes the lookup and clears the project actions`(@TempDir tmp: Path) {
		val containerState = painteraLabelGroup(tmp)
		val metadataState = MetadataUtils.createMetadataState(containerState, "labels")!!
		val assignment = metadataState.fragmentSegmentAssignmentState
		assignment.apply(Merge(2L, 3L, 10L))

		assignment.persist()

		val lookup = N5URI.normalizeGroupPath("labels/${N5Helpers.PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASET}")
		assertTrue(containerState.writer!!.datasetExists(lookup)) { "the fragment-segment lookup should have been written" }
		assertTrue(FragmentSegmentAssignmentActions(assignment).events.isEmpty()) { "committed actions must not be stored in the project" }
		assertFalse(assignment.hasPersistableData()) { "and there is nothing left to commit" }
		assertEquals(10L, assignment.getSegment(2L)) { "the merge is still applied" }
	}

	@Test
	fun `a read-only container cannot commit its assignment`(@TempDir tmp: Path) {
		val containerState = painteraLabelGroup(tmp)
		val metadataState = MetadataUtils.createMetadataState(containerState.readOnlyCopy(), "labels")!!
		val assignment = metadataState.fragmentSegmentAssignmentState
		assignment.apply(Merge(2L, 3L, 10L))

		assertFalse(assignment.persister.canPersist()) { "there is no writer, so the commit could only fail" }
		assertFalse(assignment.hasPersistableData()) { "so it must not be offered for commit" }
		assertEquals(1, assignment.events().size) { "the merge is still kept for the Paintera project" }
	}
}
