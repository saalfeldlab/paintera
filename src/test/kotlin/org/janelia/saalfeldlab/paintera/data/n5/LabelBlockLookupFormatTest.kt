package org.janelia.saalfeldlab.paintera.data.n5

import net.imglib2.FinalInterval
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.labels.blocks.n5.LabelBlockLookupFromN5Relative
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.janelia.saalfeldlab.paintera.util.n5.metadata.LabelBlockLookupGroup
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import java.nio.file.Path

/**
 * The label-block-lookup writes a byte block far shorter than the block size it declares. N5 stores the real
 * length in its block header, so the short block reads back; other formats have no per-block header and cannot
 * represent it. Committing a canvas twice reads the lookup back, so any format that cannot do this round trip
 * breaks the second commit.
 */
@TestInstance(PER_CLASS)
class LabelBlockLookupFormatTest {

	@BeforeAll
	fun setup() = TestData.registerLabelBlockLookupAdapter()

	private val group = "labels"
	private val lookupGroup = "label-to-block-mapping"

	private fun lookupFor(testCase: TestCase, tmp: Path): LabelBlockLookupFromN5Relative {
		val writer = TestData.newWriter(testCase, tmp)
		writer.createGroup(group)
		val lookup = LabelBlockLookupFromN5Relative(N5URI.normalizeGroupPath("$lookupGroup/s%d"))
		LabelBlockLookupGroup(group, lookupGroup, 1, lookup).also { it.writeMetadata(it, writer, it.path) }
		lookup.setRelativeTo(writer, group)
		return lookup
	}

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#creatableLabelDatasetCases")
	fun `a written lookup entry reads back`(testCase: TestCase, @TempDir tmp: Path) {
		val lookup = lookupFor(testCase, tmp)
		val key = LabelBlockLookupKey(0, 5L)
		val interval = FinalInterval(longArrayOf(0, 0, 0), longArrayOf(31, 31, 31))

		lookup.write(key, interval)

		val read = lookup.read(key)
		assertEquals(1, read.size) { "one interval was written for label 5 in $testCase" }
		assertArrayEquals(interval.minAsLongArray(), read[0].minAsLongArray())
		assertArrayEquals(interval.maxAsLongArray(), read[0].maxAsLongArray())
	}

	/** what a second canvas commit does: read the entries the first commit wrote, then add to them */
	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#creatableLabelDatasetCases")
	fun `a second write keeps the first entry`(testCase: TestCase, @TempDir tmp: Path) {
		val lookup = lookupFor(testCase, tmp)
		val first = LabelBlockLookupKey(0, 5L)
		val second = LabelBlockLookupKey(0, 6L)

		lookup.write(first, FinalInterval(longArrayOf(0, 0, 0), longArrayOf(31, 31, 31)))
		lookup.write(second, FinalInterval(longArrayOf(32, 0, 0), longArrayOf(63, 31, 31)))

		assertEquals(1, lookup.read(first).size) { "the first label should survive the second write in $testCase" }
		assertEquals(1, lookup.read(second).size) { "the second label should be there too in $testCase" }
	}
}
