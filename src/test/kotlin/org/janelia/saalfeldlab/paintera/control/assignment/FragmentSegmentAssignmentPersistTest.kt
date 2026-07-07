package org.janelia.saalfeldlab.paintera.control.assignment

import gnu.trove.map.hash.TLongLongHashMap
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.janelia.saalfeldlab.util.n5.N5FragmentSegmentAssignmentInitialLut
import org.janelia.saalfeldlab.util.n5.N5FragmentSegmentAssignmentPersister
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import java.nio.file.Path

class FragmentSegmentAssignmentPersistTest {

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#writeableLabelSourceCases")
	fun `fragment-segment assignment round-trips`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = "fragment-segment-assignment"
		val keys = longArrayOf(2, 3, 4)
		val values = longArrayOf(10, 10, 11)

		N5FragmentSegmentAssignmentPersister(writer, dataset).persist(keys, values)

		/* on-disk layout: fragments live in block (0, 0), segments in block (0, 1) */
		val attributes = writer.getDatasetAttributes(dataset)
		val persistedKeys = writer.readBlock<LongArray>(dataset, attributes, 0, 0)!!.data
		val persistedValues = writer.readBlock<LongArray>(dataset, attributes, 0, 1)!!.data
		assertArrayEquals(keys, persistedKeys) { "fragment ids should match for $testCase" }
		assertArrayEquals(values, persistedValues) { "segment ids should match for $testCase" }

		/* round-trip through the load path Paintera actually uses to restore the assignment */
		val loaded = N5FragmentSegmentAssignmentInitialLut(writer, dataset).get()
		assertEquals(TLongLongHashMap(keys, values), loaded) { "loaded assignment should match for $testCase" }
	}

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#writeableLabelSourceCases")
	fun `empty fragment-segment assignment round-trips`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = "fragment-segment-assignment"

		/* the persister has a dedicated zero-length path (block size is clamped to 1) */
		N5FragmentSegmentAssignmentPersister(writer, dataset).persist(LongArray(0), LongArray(0))

		val loaded = N5FragmentSegmentAssignmentInitialLut(writer, dataset).get()
		assertEquals(0, loaded.size()) { "empty assignment should load as an empty map for $testCase" }
	}
}
