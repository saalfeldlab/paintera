package org.janelia.saalfeldlab.paintera.data.n5

import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import java.nio.file.Path

@TestInstance(PER_CLASS)
class CreateLabelDatasetFormatTest {

	@BeforeAll
	fun setup() = TestData.registerLabelBlockLookupAdapter()

	/**
	 * Create a new label dataset for every writeable label case. The case's data type decides whether we
	 * write a label-multiset or a scalar label group; label multisets only ever appear in N5 cases, so this
	 * keeps multiset creation N5-only without a separate parameter source.
	 */
	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#creatableLabelDatasetCases")
	fun `create label dataset`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val group = "new-labels"
		val labelMultiset = testCase.isLabelMultiset
		val dimensions = TestData.defaultDimensions(testCase)
		val blockSize = testCase.shape.blockSize.map { it.toInt() }.toIntArray()
		val relativeScaleFactors = arrayOf(doubleArrayOf(2.0, 2.0, 1.0))
		val maxNumEntries = if (labelMultiset) intArrayOf(-1) else null

		N5Data.createEmptyLabelDataset(
			writer,
			group,
			dimensions,
			blockSize,
			doubleArrayOf(1.0, 1.0, 1.0),
			doubleArrayOf(0.0, 0.0, 0.0),
			relativeScaleFactors,
			"pixel",
			maxNumEntries,
			labelMultiset,
			false
		)

		assertEquals(0L, writer.getAttribute(group, N5Helpers.MAX_ID_KEY, Long::class.java))
		assertEquals(labelMultiset, writer.getAttribute("$group/data", N5Helpers.IS_LABEL_MULTISET_KEY, Boolean::class.java))
		assertTrue(writer.datasetExists(N5URI.normalizeGroupPath("$group/data/s0"))) { "missing s0 for $testCase" }
		assertTrue(writer.datasetExists(N5URI.normalizeGroupPath("$group/data/s1"))) { "missing s1 for $testCase" }
		assertTrue(writer.exists(N5URI.normalizeGroupPath("$group/unique-labels"))) { "missing unique-labels for $testCase" }
		assertTrue(writer.datasetExists(N5URI.normalizeGroupPath("$group/unique-labels/s1"))) { "missing unique-labels/s1 for $testCase" }

		/* relativeScaleFactors [2, 2, 1] halves x and y at s1 */
		val s1Dimensions = writer.getDatasetAttributes(N5URI.normalizeGroupPath("$group/data/s1")).dimensions
		assertArrayEquals(longArrayOf(dimensions[0] / 2, dimensions[1] / 2, dimensions[2]), s1Dimensions) { "unexpected s1 dimensions for $testCase" }

		/* downsampling factors are accumulated and only declared on downscaled levels, not s0 */
		assertNull(writer.getAttribute("$group/data/s0", N5Helpers.DOWNSAMPLING_FACTORS_KEY, DoubleArray::class.java)) { "s0 should not declare downsampling factors for $testCase" }
		assertArrayEquals(
			doubleArrayOf(2.0, 2.0, 1.0),
			writer.getAttribute("$group/data/s1", N5Helpers.DOWNSAMPLING_FACTORS_KEY, DoubleArray::class.java)
		) { "unexpected s1 downsampling factors for $testCase" }

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), group)
		assertNotNull(metadataState) { "created label group should re-open as metadata for $testCase" }
		assertTrue(metadataState!!.isLabel) { "re-opened group should be a label for $testCase" }
		assertEquals(labelMultiset, metadataState.isLabelMultiset)
	}
}
