package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.JsonElement
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataType
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiscaleGroup
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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

		N5Data.createPainteraLabelDataset(
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

	/**
	 * An N-D (x, y, z, c, t) label dataset: the source axes (in x, y, z, c, t order) are stored and round-trip with the
	 * right types/units, the rank comes from the dataset, and downsampling halves only the spatial axes. The full-rank
	 * per-level geometry, including the non-spatial resolution/offset, round-trips through the OME-NGFF style
	 * multiscales block on the data group; the paintera resolution/offset keys stay spatial-only.
	 */
	@Test
	fun `create N-D label dataset stores axes and downsamples spatially`(@TempDir tmp: Path) {
		val testCase = TestData.creatableLabelDatasetCases.first { it.format == StorageFormat.N5 && it.dataType == DataType.UINT64 }
		val writer = TestData.newWriter(testCase, tmp)
		val group = "nd-labels"

		val dimensions = longArrayOf(16, 16, 16, 3, 2)
		val blockSize = intArrayOf(8, 8, 8, 1, 1)
		val axes = arrayOf(
			Axis(Axis.SPACE, "x", "pixel", false),
			Axis(Axis.SPACE, "y", "pixel", false),
			Axis(Axis.SPACE, "z", "pixel", false),
			Axis(Axis.CHANNEL, "c", null, false),
			Axis(Axis.TIME, "t", "seconds", false)
		)

		N5Data.createPainteraLabelDataset(
			writer,
			group,
			dimensions,
			blockSize,
			doubleArrayOf(2.0, 3.0, 4.0, 1.0, 0.5),
			doubleArrayOf(0.0, 0.0, 0.0, 0.0, 10.0),
			arrayOf(doubleArrayOf(2.0, 2.0, 2.0)),
			"pixel",
			null,
			labelMultisetType = false,
			overwrite = false,
			axes = axes
		)

		/* s0 keeps the full N-D shape; s1 halves only x, y, z (channel/time untouched) */
		assertArrayEquals(dimensions, writer.getDatasetAttributes(N5URI.normalizeGroupPath("$group/data/s0")).dimensions)
		assertArrayEquals(longArrayOf(8, 8, 8, 3, 2), writer.getDatasetAttributes(N5URI.normalizeGroupPath("$group/data/s1")).dimensions) {
			"downsampling must be spatial-only for N-D"
		}
		assertArrayEquals(doubleArrayOf(2.0, 2.0, 2.0), writer.getAttribute("$group/data/s1", N5Helpers.DOWNSAMPLING_FACTORS_KEY, DoubleArray::class.java))

		/* the paintera keys stay spatial-only; the parser requires length 3 */
		assertArrayEquals(doubleArrayOf(2.0, 3.0, 4.0), writer.getAttribute("$group/data", N5Helpers.RESOLUTION_KEY, DoubleArray::class.java))
		assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0), writer.getAttribute("$group/data", N5Helpers.OFFSET_KEY, DoubleArray::class.java))

		/* the non-spatial resolution/offset live in the OME-NGFF style multiscales block, full rank per level */
		assertEquals("0.5", writer.getAttribute("$group/data", "ome/version", String::class.java))
		val datasets = writer.getAttribute("$group/data", "ome/multiscales", JsonElement::class.java)!!
			.asJsonArray[0].asJsonObject["datasets"].asJsonArray
		fun transformOf(level: Int, type: String) = datasets[level].asJsonObject["coordinateTransformations"].asJsonArray
			.first { it.asJsonObject["type"].asString == type }.asJsonObject[type].asJsonArray
			.map { it.asDouble }.toDoubleArray()
		assertArrayEquals(doubleArrayOf(2.0, 3.0, 4.0, 1.0, 0.5), transformOf(0, "scale")) { "s0 scale must carry the c/t resolution" }
		assertArrayEquals(doubleArrayOf(4.0, 6.0, 8.0, 1.0, 0.5), transformOf(1, "scale")) { "s1 scale must downsample spatially only" }
		assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0, 0.0, 10.0), transformOf(0, "translation")) { "translation must carry the c/t offset" }

		/* re-open: rank from the dataset, axes round-trip with the chosen types and units (no size heuristic) */
		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), group)!!
		assertEquals(5, metadataState.datasetAttributes.numDimensions)
		val readAxes = metadataState.axes
		assertArrayEquals(arrayOf("x", "y", "z", "c", "t"), readAxes.map { it.name }.toTypedArray())
		assertArrayEquals(arrayOf(Axis.SPACE, Axis.SPACE, Axis.SPACE, Axis.CHANNEL, Axis.TIME), readAxes.map { it.type }.toTypedArray())
		assertEquals("seconds", readAxes[4].unit) { "time unit should round-trip" }

		/* the data group metadata is the parsed block itself, so the full-rank geometry is in memory (e.g. for dialog populate) */
		val ngffHighestRes = (metadataState.metadata as N5PainteraDataMultiscaleGroup).dataGroupMetadata.childrenMetadata.first()
		assertArrayEquals(doubleArrayOf(2.0, 3.0, 4.0, 1.0, 0.5), ngffHighestRes.scale)
		assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0, 0.0, 10.0), ngffHighestRes.translation)
	}

	/**
	 * A legacy group (no ome/multiscales block) has the equivalent block synthesized at parse time: with the block
	 * stripped, the group still reopens with the correct transforms and axes from the legacy attributes, and the
	 * in-memory metadata is NGFF all the same.
	 */
	@Test
	fun `legacy group parses without the ome multiscales block`(@TempDir tmp: Path) {
		val testCase = TestData.creatableLabelDatasetCases.first { it.format == StorageFormat.N5 && it.dataType == DataType.UINT64 }
		val writer = TestData.newWriter(testCase, tmp)
		val group = "legacy-labels"

		N5Data.createPainteraLabelDataset(
			writer, group,
			longArrayOf(16, 16, 16, 3, 2), intArrayOf(8, 8, 8, 1, 1),
			doubleArrayOf(2.0, 3.0, 4.0), doubleArrayOf(1.0, 2.0, 3.0),
			arrayOf(doubleArrayOf(2.0, 2.0, 2.0)),
			"pixel", null, labelMultisetType = false, overwrite = false,
			axes = arrayOf(
				Axis(Axis.SPACE, "x", "pixel", false),
				Axis(Axis.SPACE, "y", "pixel", false),
				Axis(Axis.SPACE, "z", "pixel", false),
				Axis(Axis.CHANNEL, "c", null, false),
				Axis(Axis.TIME, "t", "seconds", false)
			)
		)

		/* strip the block; only the legacy attributes remain, as on a dataset from an older Paintera */
		writer.removeAttribute("$group/data", "ome")

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), group)!!
		assertTrue(metadataState.isLabel) { "legacy group should still reopen as a label" }
		assertArrayEquals(doubleArrayOf(2.0, 3.0, 4.0), metadataState.resolution)
		assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0), metadataState.translation)
		/* legacy carries no axis metadata; the canonical axes (and no units) are the only possibility */
		assertArrayEquals(arrayOf("x", "y", "z", "c", "t"), metadataState.axes.map { it.name }.toTypedArray())
		assertNull(metadataState.axes[4].unit) { "legacy has no axis metadata, so no units survive" }

		/* the synthesized in-memory block carries the full-rank geometry, spatial from legacy keys, non-spatial neutral */
		val synthesizedHighestRes = (metadataState.metadata as N5PainteraDataMultiscaleGroup).dataGroupMetadata.childrenMetadata.first()
		assertArrayEquals(doubleArrayOf(2.0, 3.0, 4.0, 1.0, 1.0), synthesizedHighestRes.scale)
		assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0, 0.0, 0.0), synthesizedHighestRes.translation)
	}

	/**
	 * The ome/multiscales block is the authoritative geometry: with every legacy geometry attribute stripped
	 * (resolution, offset, downsamplingFactors, axes, unit), the group still reopens with the correct transforms
	 * and axes. This is the block-only format a future writer may emit once legacy dual-writing is dropped.
	 */
	@Test
	fun `block-only group parses without legacy geometry attributes`(@TempDir tmp: Path) {
		val testCase = TestData.creatableLabelDatasetCases.first { it.format == StorageFormat.N5 && it.dataType == DataType.UINT64 }
		val writer = TestData.newWriter(testCase, tmp)
		val group = "block-only-labels"

		N5Data.createPainteraLabelDataset(
			writer, group,
			longArrayOf(16, 16, 16, 3, 2), intArrayOf(8, 8, 8, 1, 1),
			doubleArrayOf(2.0, 3.0, 4.0, 1.0, 0.5), doubleArrayOf(1.0, 2.0, 3.0, 0.0, 10.0),
			arrayOf(doubleArrayOf(2.0, 2.0, 2.0)),
			"pixel", null, labelMultisetType = false, overwrite = false,
			axes = arrayOf(
				Axis(Axis.SPACE, "x", "pixel", false),
				Axis(Axis.SPACE, "y", "pixel", false),
				Axis(Axis.SPACE, "z", "pixel", false),
				Axis(Axis.CHANNEL, "c", null, false),
				Axis(Axis.TIME, "t", "seconds", false)
			)
		)

		/* strip every legacy geometry attribute; only the block remains */
		writer.removeAttribute("$group/data", N5Helpers.RESOLUTION_KEY)
		writer.removeAttribute("$group/data", N5Helpers.OFFSET_KEY)
		listOf("s0", "s1").forEach { scaleLevel ->
			writer.removeAttribute("$group/data/$scaleLevel", N5Helpers.UNIT_KEY)
			runCatching { writer.removeAttribute("$group/data/$scaleLevel", N5Helpers.DOWNSAMPLING_FACTORS_KEY) }
		}

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), group)!!
		assertTrue(metadataState.isLabel) { "block-only group should still reopen as a label" }
		assertArrayEquals(doubleArrayOf(2.0, 3.0, 4.0), metadataState.resolution) { "resolution must come from the block" }
		assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0), metadataState.translation) { "translation must come from the block" }
		assertArrayEquals(arrayOf("x", "y", "z", "c", "t"), metadataState.axes.map { it.name }.toTypedArray())
		assertEquals("seconds", metadataState.axes[4].unit) { "axis units must come from the block" }

		/* s1's transform derives from the block: spatially doubled scale, unchanged translation */
		val s1Transform = (metadataState as MultiScaleMetadataState).scaleTransforms[1]
		assertArrayEquals(doubleArrayOf(4.0, 6.0, 8.0), doubleArrayOf(s1Transform[0, 0], s1Transform[1, 1], s1Transform[2, 2]))
		assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0), s1Transform.translation)
	}

	/**
	 * An N-D (x, y, z, c, t) label MULTISET dataset: stored as UINT8 with per-level max entries and a matching UINT64
	 * unique-labels group of the same N-D shape, downsampled spatially only, re-opening as a label multiset of the full rank.
	 */
	@Test
	fun `create N-D label multiset dataset`(@TempDir tmp: Path) {
		val testCase = TestData.creatableLabelDatasetCases.first { it.format == StorageFormat.N5 }
		val writer = TestData.newWriter(testCase, tmp)
		val group = "nd-multiset-labels"

		val dimensions = longArrayOf(16, 16, 16, 3, 2)
		val blockSize = intArrayOf(8, 8, 8, 1, 1)
		val axes = arrayOf(
			Axis(Axis.SPACE, "x", "pixel", false),
			Axis(Axis.SPACE, "y", "pixel", false),
			Axis(Axis.SPACE, "z", "pixel", false),
			Axis(Axis.CHANNEL, "c", null, false),
			Axis(Axis.TIME, "t", "seconds", false)
		)

		N5Data.createPainteraLabelDataset(
			writer, group, dimensions, blockSize,
			doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(0.0, 0.0, 0.0),
			arrayOf(doubleArrayOf(2.0, 2.0, 2.0)),
			"pixel",
			maxNumEntries = intArrayOf(-1),
			labelMultisetType = true,
			overwrite = false,
			axes = axes
		)

		/* label multiset scales are UINT8 with a matching UINT64 unique-labels group of the same N-D shape */
		val s0 = writer.getDatasetAttributes(N5URI.normalizeGroupPath("$group/data/s0"))
		assertEquals(org.janelia.saalfeldlab.n5.DataType.UINT8, s0.dataType)
		assertArrayEquals(dimensions, s0.dimensions)
		assertEquals(true, writer.getAttribute("$group/data", N5Helpers.IS_LABEL_MULTISET_KEY, Boolean::class.java))
		assertEquals(true, writer.getAttribute("$group/data/s0", N5Helpers.IS_LABEL_MULTISET_KEY, Boolean::class.java))
		assertNotNull(writer.getAttribute("$group/data/s0", N5Helpers.MAX_NUM_ENTRIES_KEY, Int::class.java)) { "max entries should be declared" }

		val uniqueLabels = writer.getDatasetAttributes(N5URI.normalizeGroupPath("$group/unique-labels/s0"))
		assertEquals(org.janelia.saalfeldlab.n5.DataType.UINT64, uniqueLabels.dataType)
		assertArrayEquals(dimensions, uniqueLabels.dimensions) { "unique-labels must match the N-D data shape" }

		/* s1 halves only x, y, z */
		assertArrayEquals(longArrayOf(8, 8, 8, 3, 2), writer.getDatasetAttributes(N5URI.normalizeGroupPath("$group/data/s1")).dimensions) {
			"downsampling must be spatial-only for an N-D multiset"
		}

		/* re-open as an N-D label multiset with axes intact */
		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), group)!!
		assertTrue(metadataState.isLabel) { "should re-open as a label" }
		assertTrue(metadataState.isLabelMultiset) { "should re-open as a label multiset" }
		assertEquals(5, metadataState.datasetAttributes.numDimensions)
		assertArrayEquals(arrayOf("x", "y", "z", "c", "t"), metadataState.axes.map { it.name }.toTypedArray())
	}
}
