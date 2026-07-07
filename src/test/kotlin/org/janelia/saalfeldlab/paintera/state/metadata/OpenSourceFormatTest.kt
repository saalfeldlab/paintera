package org.janelia.saalfeldlab.paintera.state.metadata

import bdv.cache.SharedQueue
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.label.VolatileLabelMultisetType
import net.imglib2.type.numeric.integer.ByteType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileByteType
import net.imglib2.type.volatiles.VolatileUnsignedByteType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataType
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.util.concurrent.Executors
import kotlin.io.path.absolutePathString

@TestInstance(PER_CLASS)
class OpenSourceFormatTest {

	private val queue = SharedQueue(1)
	private val executor = Executors.newSingleThreadExecutor()

	/* >3D (sliced) and 2D (embedded) label sources: their data is a view, not a cell image, so the masked-source /
	 * label-block-lookup path must still get a 3D block grid (regressed with a ClassCastException). Sharded zarr3 is
	 * included because the carried grid must use the shard block size the commit expects, not the inner chunk the
	 * volatile cell image reports. */
	val slicedLabelGridCases =
		TestData.n5Scalar.filter { it.dataType == DataType.UINT64 && it.numDimensions > 3 } +
			TestData.n5LabelMultiset.filter { it.numDimensions > 3 } +
			TestData.zarr3Cases.filter { it.dataType == DataType.UINT64 && it.numDimensions > 3 } +
			TestData.twoDimensionalCases.filter { it.dataType == DataType.UINT64 }

	@BeforeAll
	fun setup() = TestData.registerLabelBlockLookupAdapter()

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#rawSources")
	fun `open raw source`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = TestData.createRaw(writer, testCase, "raw")

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), dataset)
		assertNotNull(metadataState) { "metadata should parse for $testCase" }

		val source = openRaw(metadataState!!, "raw", testCase)
		val opened = source.getDataSource(0, 0)
		/* high-dim raw sources are sliced to 3D, so every raw source exposes exactly 3 dimensions */
		assertEquals(3, opened.numDimensions()) { "raw source should expose 3 spatial dims for $testCase" }
		assertArrayEquals(longArrayOf(100, 100, 100), Intervals.dimensionsAsLongArray(opened))
		assertTrue(expectedType(testCase).isInstance(source.dataType)) { "unexpected data type for $testCase: ${source.dataType}" }
	}

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#readOnlyAsLabelSourceCases")
	fun `open read-only label source`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = TestData.createScalarLabel(writer, testCase, "labels")

		val readOnly = N5ContainerState(writer).readOnlyCopy()
		val metadataState = MetadataUtils.createMetadataState(readOnly, dataset)!!.also { it.isLabel = true }
		assertNull(metadataState.writer) { "read-only container must not expose a writer" }

		val backend = N5BackendLabel.createFrom<UnsignedLongType, VolatileUnsignedLongType>(metadataState, executor)
		val source = backend.createSource(queue, 0, "labels")
		assertFalse(source is MaskedSource<*, *>) { "read-only label source must not be masked for $testCase" }
		assertTrue(source is N5DataSource<*, *>) { "label source should be an N5DataSource for $testCase" }
	}

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#writeableLabelSourceCases")
	fun `open writable label source`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = TestData.createScalarLabel(writer, testCase, "labels")

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), dataset)!!.also { it.isLabel = true }
		assertNotNull(metadataState.writer) { "writable container must expose a writer" }

		/* mirror the headless masked-source construction used by ExportSourceStateTest */
		val dataSource = openRaw(metadataState, "labels", testCase)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
		assertTrue(masked is MaskedSource<*, *>) { "writable label source should be masked for $testCase" }
	}

	@ParameterizedTest
	@FieldSource("slicedLabelGridCases")
	fun `masked sliced or embedded label source exposes a 3D block grid`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = TestData.createScalarLabel(writer, testCase, "labels")

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), dataset)!!.also { it.isLabel = true }
		val dataSource = openRaw(metadataState, "labels", testCase)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
		assertTrue(masked is MaskedSource<*, *>) { "label source should be masked for $testCase" }

		/* the exact call that regressed: getLabelBlockLookupFromN5DataSource -> source.grids -> MaskedSource.getCellGrid.
		 * a sliced/embedded view is not a cell image, so the grid must come from the carried 3D spatial projection */
		val grids = masked.grids
		assertEquals(masked.numMipmapLevels, grids.size) { "one grid per scale level for $testCase" }
		val expectedBlockSize = IntArray(3) { d -> if (d < testCase.shape.blockSize.size) testCase.shape.blockSize[d].toInt() else 1 }
		grids.forEach { grid ->
			assertEquals(3, grid.numDimensions()) { "masked label grid should be 3D for $testCase" }
			assertArrayEquals(expectedBlockSize, IntArray(3).also { grid.cellDimensions(it) }) { "masked label block size for $testCase" }
		}
	}

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#channelSource4DCases")
	fun `open channel source for 4D data`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = TestData.createRaw(writer, testCase, "channels")

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), dataset)!!.also { it.isLabel = false }
		val backend = N5BackendChannel<UnsignedByteType, VolatileUnsignedByteType>(metadataState, intArrayOf(0, 1), 3)
		val source = backend.createSource(queue, 0, "channels")
		assertNotNull(source)
		assertEquals(2, source.numChannels()) { "should expose the selected channels for $testCase" }
	}

	@ParameterizedTest
	@FieldSource("org.janelia.saalfeldlab.paintera.testdata.TestData#hyperDimension5DCases")
	fun `slice high-dimensional data to 3D`(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val dataset = TestData.createRaw(writer, testCase, "highdim")

		val metadataState = MetadataUtils.createMetadataState(N5ContainerState(writer), dataset)!!
		val source = openRaw(metadataState, "highdim", testCase)
		val opened = source.getDataSource(0, 0)
		assertEquals(3, opened.numDimensions()) { "n>4D data should be sliced to 3D for $testCase" }
		assertArrayEquals(longArrayOf(100, 100, 100), Intervals.dimensionsAsLongArray(opened))
	}

	/**
	 * Open [name] as a raw [N5DataSource], using the imglib2 type the source will actually expose: label
	 * multisets open as [LabelMultisetType] at any dimensionality (>3D is reduced to a 3D multiset view).
	 */
	private fun openRaw(metadataState: MetadataState, name: String, testCase: TestCase): N5DataSource<*, *> = when {
		testCase.isLabelMultiset -> N5DataSource<LabelMultisetType, VolatileLabelMultisetType>(metadataState, name, queue, 0)
		testCase.dataType == DataType.INT8 -> N5DataSource<ByteType, VolatileByteType>(metadataState, name, queue, 0)
		else -> N5DataSource<UnsignedLongType, VolatileUnsignedLongType>(metadataState, name, queue, 0)
	}

	private fun expectedType(testCase: TestCase): Class<*> = when {
		testCase.isLabelMultiset -> LabelMultisetType::class.java
		testCase.dataType == DataType.INT8 -> ByteType::class.java
		else -> UnsignedLongType::class.java
	}
}
