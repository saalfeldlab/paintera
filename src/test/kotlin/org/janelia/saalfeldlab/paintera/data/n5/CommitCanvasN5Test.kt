package org.janelia.saalfeldlab.paintera.data.n5

import bdv.cache.SharedQueue
import com.google.gson.GsonBuilder
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import io.github.oshai.kotlinlogging.KotlinLogging
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.cache.img.*
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.label.Label
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.IntervalIndexer
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.labels.blocks.n5.LabelBlockLookupFromN5Relative
import org.janelia.saalfeldlab.labels.downsample.WinnerTakesAll
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.paintera.Paintera
import net.imglib2.FinalInterval
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.paintera.testdata.TestData.DataType
import org.janelia.saalfeldlab.paintera.testdata.TestData.TestCase
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.Executors
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.io.path.absolutePathString

@TestInstance(PER_CLASS)
class CommitCanvasN5Test {

	/* label multisets and the paintera-data label format are N5-only at the moment */
	val labelMultisetCommitCases = TestData.n5LabelMultiset.filter { it.numDimensions == 3 }

	/* committing needs a writable label dataset; these standard 3D cases exercise the (now unified) native commit over
	 * every storage layout, including sharded zarr3 (verified passing since 8bc2ab47f). >3D is covered by testNDNative* */
	val scalarCommitCases = TestData.allCases.filter { it.dataType == DataType.UINT64 && it.numDimensions == 3 }
	val n5ScalarCommitCases = scalarCommitCases.filter { it.format == StorageFormat.N5 }

	/* commit to zarr3 at every dimensionality, sharded + unsharded (uint64, NONE metadata). ScalePyramid is
	 * irrelevant here because the test helpers build the dataset structure directly, so we pin it to Single to
	 * avoid duplicate cases. */
	val zarr3AllDimCommitCases = TestData.allCases.filter {
		it.format == StorageFormat.ZARR3 && it.dataType == DataType.UINT64 &&
			it.metadata == TestData.Metadata.NONE && it.scalePyramid == TestData.ScalePyramid.Single
	}

	/* a real canvas is always 3D (the source is sliced to 3D); the full-canvas helpers only model that for 3D data */
	val zarr3ThreeDimCommitCases = zarr3AllDimCommitCases.filter { it.numDimensions == 3 }

	/* the >3D subset, for the nD full-canvas commit (and the realistic open-then-commit flow) */
	val zarr3HighDimCommitCases = zarr3AllDimCommitCases.filter { it.numDimensions > 3 }

	/* the realistic flow: open a sharded label, mask it, commit. the masked source sizes its canvas from the carried
	 * grid, which for sharded data must be the shard block size the commit expects (not the inner chunk) */
	val shardedMaskedCommitCases = zarr3AllDimCommitCases.filter { it.sharded && it.numDimensions in 3..4 }

	/* >3D single-scale label-multiset (N5-only) for the native nD commit path */
	val ndMultisetCommitCases = TestData.n5LabelMultiset.filter {
		it.numDimensions > 3 && it.scalePyramid == TestData.ScalePyramid.Single && it.metadata == TestData.Metadata.NONE
	}

	/* >3D multi-scale uint64 for the native nD commit path (per-slab spatial downsample) */
	val ndMultiscaleCommitCases = TestData.n5Scalar.filter {
		it.dataType == DataType.UINT64 && it.numDimensions > 3 && it.scalePyramid == TestData.ScalePyramid.Multi
	}

	private val queue = SharedQueue(1)
	/* multi-threaded: propagateMask submits downsample sub-tasks to this same executor and waits, so a single thread deadlocks */
	private val executor = Executors.newFixedThreadPool(4)

	private data class CanvasAndContainer(val canvas: CachedCellImg<UnsignedLongType, *>, val container: N5ContainerState, val testCase: TestCase)

	@BeforeAll
	fun setupN5Factory() {

		val builder = GsonBuilder()
		builder.registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.getJsonAdapter());
		Paintera.n5Factory.gsonBuilder(builder)
	}

	@ParameterizedTest
	@FieldSource("labelMultisetCommitCases")
	fun testSingleScaleLabelMultisetCommit(testCase: TestCase, @TempDir tmp: Path) = testSingleScale(
		getCanvasAndContainer(tmp, testCase),
		"single-scale-label-multisets",
		{ n5, dataset -> N5LabelMultisets.openLabelMultiset(n5, dataset) },
		{ c: UnsignedLongType, l: LabelMultisetType -> assertMultisetType(c, l) },
		MULTISET_ATTRIBUTE
	)

	@ParameterizedTest
	@FieldSource("labelMultisetCommitCases")
	fun testMultiScaleLabelMultisetCommit(testCase: TestCase, @TempDir tmp: Path) = testMultiScale(
		getCanvasAndContainer(tmp, testCase),
		"multi-scale-label-multisets",
		{ n5, dataset -> N5LabelMultisets.openLabelMultiset(n5, dataset) },
		{ c: UnsignedLongType, l: LabelMultisetType -> assertMultisetType(c, l) },
		MULTISET_ATTRIBUTE
	)

	@ParameterizedTest
	@FieldSource("labelMultisetCommitCases")
	fun testPainteraLabelMultisetCommit(testCase: TestCase, @TempDir tmp: Path) = testPainteraData(
		getCanvasAndContainer(tmp, testCase),
		"paintera-label-multisets",
		{ n5, dataset -> N5LabelMultisets.openLabelMultiset(n5, dataset) },
		{ c: UnsignedLongType, l: LabelMultisetType -> assertMultisetType(c, l) },
		MULTISET_ATTRIBUTE,
		intArrayOf(2, 2, 3)
	)

	@ParameterizedTest
	@FieldSource("scalarCommitCases")
	fun testSingleScaleUint64Commit(testCase: TestCase, @TempDir tmp: Path) = testSingleScale(
		getCanvasAndContainer(tmp, testCase),
		"single-scale-uint64",
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c: UnsignedLongType, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) }
	)

	@ParameterizedTest
	@FieldSource("scalarCommitCases")
	fun testMultiScaleUint64Commit(testCase: TestCase, @TempDir tmp: Path) = testMultiScale(
		getCanvasAndContainer(tmp, testCase),
		"multi-scale-uint64",
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c: UnsignedLongType, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) }
	)

	@ParameterizedTest
	@FieldSource("scalarCommitCases")
	fun testMultiScaleDownsampleUint64Commit(testCase: TestCase, @TempDir tmp: Path) = testMultiScaleDownsample(
		getCanvasAndContainer(tmp, testCase),
		"multi-scale-downsample-uint64",
		intArrayOf(2, 2, 2)
	)

	@ParameterizedTest
	@FieldSource("zarr3ThreeDimCommitCases")
	fun testZarr3SingleScaleCommit3D(testCase: TestCase, @TempDir tmp: Path) = testSingleScale(
		getCanvasAndContainer(tmp, testCase),
		"zarr3-single-uint64",
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c: UnsignedLongType, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) }
	)

	@ParameterizedTest
	@FieldSource("zarr3ThreeDimCommitCases")
	fun testZarr3MultiScaleCommit3D(testCase: TestCase, @TempDir tmp: Path) = testMultiScale(
		getCanvasAndContainer(tmp, testCase),
		"zarr3-multi-uint64",
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c: UnsignedLongType, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) }
	)

	/**
	 * End-to-end of the exact flow a user hits: open a sharded label through the real source path, wrap it as a
	 * masked source, and commit. The masked source sizes its canvas from {@code source.getGrid} (the carried grid);
	 * for sharded data that must be the shard block size the commit derives from the dataset attributes, not the inner
	 * chunk the volatile cell image reports - otherwise the commit's grid-compatibility check rejects the canvas.
	 */
	@ParameterizedTest
	@FieldSource("shardedMaskedCommitCases")
	fun testOpenShardedLabelMaskAndCommit(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val container = N5ContainerState(writer)
		val datasetDimensions = TestData.defaultDimensions(testCase)
		val dataset = TestData.createRaw(writer, testCase, "sharded-label", datasetDimensions)

		val groundTruth = knownData(datasetDimensions)
		N5Utils.saveBlock(groundTruth, writer, dataset, writer.getDatasetAttributes(dataset))

		val metadataState = createMetadataState(container, dataset)!!.also { it.isLabel = true }
		val dataSource = N5DataSource<UnsignedLongType, VolatileUnsignedLongType>(metadataState, "sharded-label", queue, 0)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
		assertTrue(masked is MaskedSource<*, *>) { "sharded label should be masked for $testCase" }

		/* the masked source's carried grid must report the shard block size (not the inner chunk the volatile cell
		 * image reports), so its nD canvas matches the dataset grid the commit checks against */
		val attributes = writer.getDatasetAttributes(dataset)
		val carriedSpatialCells = IntArray(masked.getGrid(0).numDimensions()).also { masked.getGrid(0).cellDimensions(it) }
		assertTrue(attributes.blockSize.copyOf(3).contentEquals(carriedSpatialCells)) { "carried grid must use the shard block size for $testCase" }

		/* the masked source's canvas mirrors the full dataset (nD) grid; commit it all at once */
		val canvas = newWritableTestCanvas(tmp, attributes.dimensions, attributes.blockSize)
		val numBlocks = Intervals.numElements(*canvas.cellGrid.gridDimensions).toInt()
		val blocks = LongArray(numBlocks) { it.toLong() }

		val expected = expectedAfterNDCommit(groundTruth, canvas, datasetDimensions)
		CommitCanvasN5(metadataState).persistCanvas(canvas, blocks)
		assertDatasetMatches(writer, dataset, expected)
	}

	/**
	 * Commit an nD canvas (matching the dataset grid) all at once: the canvas holds painted and INVALID voxels across
	 * every (channel, time) slab, and the whole annotation layer is persisted block-for-block - painted voxels
	 * overwrite, unpainted keep the background, even for blocks that span several non-spatial positions. This is the
	 * commit half of the nD MaskedSource design (the canvas is nD rather than a 3D slice).
	 */
	@ParameterizedTest
	@FieldSource("zarr3HighDimCommitCases")
	fun testNDNativeCommitPersistsWholeCanvas(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val container = N5ContainerState(writer)
		val dimensions = TestData.defaultDimensions(testCase)
		val dataset = TestData.createRaw(writer, testCase, "nd-label", dimensions)

		val groundTruth = knownData(dimensions)
		N5Utils.saveBlock(groundTruth, writer, dataset, writer.getDatasetAttributes(dataset))

		val metadataState = createMetadataState(container, dataset)!!.also { it.isLabel = true }
		val attributes = writer.getDatasetAttributes(dataset)
		/* the canvas mirrors the full dataset grid and paints/INVALIDs across every slab (writable so it stays stable) */
		val canvas = newWritableTestCanvas(tmp, attributes.dimensions, attributes.blockSize)
		val numBlocks = Intervals.numElements(*canvas.cellGrid.gridDimensions).toInt()
		val blocks = LongArray(numBlocks) { it.toLong() }

		val expected = expectedAfterNDCommit(groundTruth, canvas, dimensions)
		CommitCanvasN5(metadataState).persistCanvas(canvas, blocks)
		assertDatasetMatches(writer, dataset, expected)
	}

	/** The label-multiset counterpart of [testNDNativeCommitPersistsWholeCanvas]: an nD canvas commits into a >3D multiset dataset. */
	@ParameterizedTest
	@FieldSource("ndMultisetCommitCases")
	fun testNDNativeMultisetCommitPersistsWholeCanvas(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val container = N5ContainerState(writer)
		val dimensions = TestData.defaultDimensions(testCase)
		val dataset = TestData.createScalarLabel(writer, testCase, "nd-multiset", dimensions)

		val metadataState = createMetadataState(container, dataset)!!.also { it.isLabel = true }
		val attributes = writer.getDatasetAttributes(dataset)
		val canvas = newWritableTestCanvas(tmp, attributes.dimensions, attributes.blockSize)
		val numBlocks = Intervals.numElements(*canvas.cellGrid.gridDimensions).toInt()

		CommitCanvasN5(metadataState).persistCanvas(canvas, LongArray(numBlocks) { it.toLong() })

		/* empty background, so every painted voxel becomes a single-entry multiset of its label, INVALID stays 0 */
		val committed: RandomAccessibleInterval<LabelMultisetType> = N5LabelMultisets.openLabelMultiset(writer, dataset)
		for (pair in Views.interval(Views.pair(canvas, committed), committed))
			assertMultisetType(pair.a, pair.b)
	}

	/**
	 * Native nD commit into a multi-scale source: s0 gets the whole-canvas merge, and every lower scale is the
	 * per-slab spatial winner-takes-all downsample of the committed s0 (non-spatial axes untouched).
	 */
	@ParameterizedTest
	@FieldSource("ndMultiscaleCommitCases")
	fun testNDNativeMultiscaleCommit(testCase: TestCase, @TempDir tmp: Path) {
		val writer = TestData.newWriter(testCase, tmp)
		val container = N5ContainerState(writer)
		val s0Dimensions = TestData.defaultDimensions(testCase)
		val group = TestData.createScalarLabel(writer, testCase, "nd-multi", s0Dimensions)

		val metadataState = createMetadataState(container, group)!!.also { it.isLabel = true } as MultiScaleMetadataState
		val scalePaths = metadataState.metadata.paths
		val s0 = scalePaths[0]
		val s1 = scalePaths[1]

		val groundTruth = knownData(s0Dimensions)
		N5Utils.saveBlock(groundTruth, writer, s0, writer.getDatasetAttributes(s0))

		val s0Attributes = writer.getDatasetAttributes(s0)
		val canvas = newWritableTestCanvas(tmp, s0Attributes.dimensions, s0Attributes.blockSize)
		val numBlocks = Intervals.numElements(*canvas.cellGrid.gridDimensions).toInt()
		CommitCanvasN5(metadataState).persistCanvas(canvas, LongArray(numBlocks) { it.toLong() })

		/* s0: the whole canvas merged over the background */
		val committedS0 = expectedAfterNDCommit(groundTruth, canvas, s0Dimensions)
		assertDatasetMatches(writer, s0, committedS0)

		/* s1: each non-spatial slab spatially downsampled from the committed s0 */
		val s1Dimensions = writer.getDatasetAttributes(s1).dimensions
		val expectedS1 = expectedNDDownsample(committedS0, s0Dimensions, s1Dimensions, intArrayOf(2, 2, 2))
		assertDatasetMatches(writer, s1, expectedS1)
	}

	/**
	 * Phase B retention: paint two timepoints of an nD masked source, scrub the slice position between them, and
	 * confirm the canvas keeps both slabs' edits - the thing the in-place reslice used to discard. Drives applyMask
	 * directly (no UI) and reads back the canvas at each slice.
	 */
	@Test
	fun testNDMaskedCanvasRetainsEditsAcrossTimepoints(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("ndCanvas.n5").toString())
		val dimensions = longArrayOf(16, 16, 16, 2)
		val blockSize = intArrayOf(8, 8, 8, 1)
		val dataset = "label"
		writer.createDataset(dataset, dimensions, blockSize, org.janelia.saalfeldlab.n5.DataType.UINT64, GzipCompression())

		val metadataState = createMetadataState(N5ContainerState(writer), dataset)!!.also { it.isLabel = true }
		val dataSource = N5DataSource<UnsignedLongType, VolatileUnsignedLongType>(metadataState, dataset, queue, 0)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
				as MaskedSource<UnsignedLongType, VolatileUnsignedLongType>

		paintBoxAtTimepoint(masked, metadataState, timepoint = 0L, label = 222L)
		paintBoxAtTimepoint(masked, metadataState, timepoint = 1L, label = 111L)

		/* both timepoints survive in the nD canvas - painting t=1 did not clobber t=0 */
		assertEquals(222L, canvasValueAtTimepoint(masked, metadataState, timepoint = 0L)) { "timepoint 0 edit must be retained" }
		assertEquals(111L, canvasValueAtTimepoint(masked, metadataState, timepoint = 1L)) { "timepoint 1 edit must be retained" }
	}

	private fun paintBoxAtTimepoint(masked: MaskedSource<UnsignedLongType, VolatileUnsignedLongType>, metadataState: MetadataState, timepoint: Long, label: Long) {
		metadataState.slicePositions = longArrayOf(0, 0, 0, timepoint)
		val mask = masked.generateMask(MaskInfo(0, 0), MaskedSource.VALID_LABEL_CHECK)
		val region = FinalInterval(longArrayOf(2, 2, 2), longArrayOf(5, 5, 5))
		Views.interval(mask.rai, region).forEach { it.set(label) }
		masked.applyMask(mask, region, MaskedSource.VALID_LABEL_CHECK)
		/* applyMask paints on a background thread; the mask stays "in use" until it finishes */
		var waited = 0
		while (masked.isMaskInUseBinding.get() && waited < 10_000) {
			Thread.sleep(20); waited += 20
		}
	}

	private fun canvasValueAtTimepoint(masked: MaskedSource<UnsignedLongType, VolatileUnsignedLongType>, metadataState: MetadataState, timepoint: Long): Long {
		metadataState.slicePositions = longArrayOf(0, 0, 0, timepoint)
		return masked.getReadOnlyDataCanvas(0, 0).randomAccess().setPositionAndGet(3L, 3L, 3L).get()
	}

	/**
	 * Phase B multiscale propagation: painting a timepoint of a multi-scale nD source must propagate to the lower
	 * canvas scales for that timepoint (so zoomed-out LODs show the edit before commit), without touching other slabs.
	 */
	@Test
	fun testNDMaskedCanvasPropagatesToLowerScalesPerTimepoint(@TempDir tmp: Path) {
		val testCase = TestData.n5Scalar.first {
			it.dataType == DataType.UINT64 && it.numDimensions == 4 && it.scalePyramid == TestData.ScalePyramid.Multi
		}
		val writer = TestData.newWriter(testCase, tmp)
		val container = N5ContainerState(writer)
		val dimensions = TestData.defaultDimensions(testCase)
		val group = TestData.createScalarLabel(writer, testCase, "label", dimensions)

		val metadataState = createMetadataState(container, group)!!.also { it.isLabel = true }
		val dataSource = N5DataSource<UnsignedLongType, VolatileUnsignedLongType>(metadataState, group, queue, 0)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
				as MaskedSource<UnsignedLongType, VolatileUnsignedLongType>
		assertTrue(masked.numMipmapLevels > 1) { "this test needs a multi-scale source" }

		paintBoxAtTimepoint(masked, metadataState, timepoint = 1L, label = 111L)

		/* the box painted at s0/t=1 propagated to s1/t=1, but no other (level, timepoint) slab was touched */
		assertTrue(paintedCountAtTimepointAndLevel(masked, metadataState, timepoint = 1L, level = 0) > 0) { "s0 t=1 should hold the painted box" }
		assertTrue(paintedCountAtTimepointAndLevel(masked, metadataState, timepoint = 1L, level = 1) > 0) { "s1 t=1 should show the downsampled edit" }
		assertEquals(0, paintedCountAtTimepointAndLevel(masked, metadataState, timepoint = 0L, level = 1)) { "s1 t=0 must stay empty" }
		assertEquals(0, paintedCountAtTimepointAndLevel(masked, metadataState, timepoint = 0L, level = 0)) { "s0 t=0 must stay empty" }
	}

	private fun paintedCountAtTimepointAndLevel(masked: MaskedSource<UnsignedLongType, VolatileUnsignedLongType>, metadataState: MetadataState, timepoint: Long, level: Int): Int {
		metadataState.slicePositions = longArrayOf(0, 0, 0, timepoint)
		var count = 0
		Views.flatIterable(masked.getReadOnlyDataCanvas(0, level)).forEach { if (it.get() != Label.INVALID) count++ }
		return count
	}

	/**
	 * The case that forced commit-all-at-once: the dataset block is `[8,8,8,2]`, so t=0 and t=1 live in the same
	 * block. Painting only t=1 must persist t=1 and leave t=0 (its block-mate) and the other timepoints untouched;
	 * a per-time-slice commit would read-modify-write the shared block and could clobber t=0.
	 */
	@Test
	fun testNDCommitPreservesUnpaintedTimepointsInSharedBlock(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("multiTimepointBlock.n5").toString())
		val dimensions = longArrayOf(8, 8, 8, 4)
		val blockSize = intArrayOf(8, 8, 8, 2)
		val dataset = "label"
		writer.createDataset(dataset, dimensions, blockSize, org.janelia.saalfeldlab.n5.DataType.UINT64, GzipCompression())

		val groundTruth = knownData(dimensions)
		N5Utils.saveBlock(groundTruth, writer, dataset, writer.getDatasetAttributes(dataset))

		val metadataState = createMetadataState(N5ContainerState(writer), dataset)!!.also { it.isLabel = true }

		/* a writable nD canvas with only timepoint 1 painted (the rest INVALID) */
		val options = DiskCachedCellImgOptions.options().volatileAccesses(true).dirtyAccesses(true)
			.cacheDirectory(tmp.resolve("canvas")).cellDimensions(*blockSize)
		val canvas = DiskCachedCellImgFactory(UnsignedLongType(), options)
			.create(dimensions, CellLoader { img: SingleCellArrayImg<UnsignedLongType, *> -> img.forEach { it.set(Label.INVALID) } })
		val painted = 777L
		Views.flatIterable(Views.hyperSlice(canvas, 3, 1L)).forEach { it.set(painted) }

		val numBlocks = Intervals.numElements(*canvas.cellGrid.gridDimensions).toInt()
		CommitCanvasN5(metadataState).persistCanvas(canvas, LongArray(numBlocks) { it.toLong() })

		val reopened: RandomAccessibleInterval<UnsignedLongType> = N5Utils.open(writer, dataset)
		for (timepoint in 0 until dimensions[3]) {
			val committed = Views.hyperSlice(reopened, 3, timepoint)
			if (timepoint == 1L) {
				Views.flatIterable(committed).forEach { assertEquals(painted, it.get()) { "timepoint 1 must be the painted label" } }
			} else {
				val background = Views.hyperSlice(groundTruth, 3, timepoint)
				for (pair in Views.interval(Views.pair(background, committed), committed))
					assertEquals(pair.a.integerLong, pair.b.integerLong) { "timepoint $timepoint must be untouched (shares a block with t=1)" }
			}
		}
	}

	@ParameterizedTest
	@FieldSource("n5ScalarCommitCases")
	fun testPainteraUint64Commit(testCase: TestCase, @TempDir tmp: Path) = testPainteraData(
		getCanvasAndContainer(tmp, testCase),
		"paintera-uint64",
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) },
		HashMap(),
		intArrayOf(2, 2, 3)
	)


	companion object {
		private val LOG = KotlinLogging.logger { }
		private val INVALID = UnsignedLongType(Label.INVALID)
		private val MULTISET_ATTRIBUTE: Map<String, Any> = mapOf(N5Helpers.IS_LABEL_MULTISET_KEY to true)
		private val PAINTERA_DATA_ATTRIBUTE: Map<String, Any> = mapOf("type" to "label")

		private fun isInvalid(pixel: UnsignedLongType): Boolean {
			val isInvalid = INVALID.valueEquals(pixel)
			LOG.trace { "$pixel is invalid? $isInvalid" }
			return isInvalid
		}

		/** Deterministic data that varies across every axis, so cross-slice / cross-inner-chunk corruption is detectable. */
		private fun knownData(dimensions: LongArray): RandomAccessibleInterval<UnsignedLongType> {
			val img = ArrayImgs.unsignedLongs(*dimensions)
			val cursor = img.localizingCursor()
			val position = LongArray(dimensions.size)
			while (cursor.hasNext()) {
				cursor.fwd()
				cursor.localize(position)
				var value = 1L
				for (coordinate in position) value = value * 31L + (coordinate + 1L)
				cursor.get().set(value and 0xFFFFFFL)
			}
			return img
		}

		private fun assertDatasetMatches(writer: N5Writer, dataset: String, expected: RandomAccessibleInterval<UnsignedLongType>) {
			val reopened: RandomAccessibleInterval<UnsignedLongType> = N5Utils.open(writer, dataset)
			assertArrayEquals(Intervals.dimensionsAsLongArray(expected), Intervals.dimensionsAsLongArray(reopened))
			for (pair in Views.interval(Views.pair(expected, reopened), reopened))
				assertEquals(pair.a.integerLong, pair.b.integerLong)
		}

		/**
		 * The volume a correct commit-all-at-once should produce: every painted (non-INVALID) [canvas] voxel
		 * overwrites the background, every other voxel keeps [groundTruth]. The whole nD annotation layer at once -
		 * no axis is pinned, so blocks spanning several timepoints/channels are merged in place.
		 */
		private fun expectedAfterNDCommit(
			groundTruth: RandomAccessibleInterval<UnsignedLongType>,
			canvas: RandomAccessibleInterval<UnsignedLongType>,
			dimensions: LongArray
		): RandomAccessibleInterval<UnsignedLongType> {
			val expected = ArrayImgs.unsignedLongs(*dimensions)
			val groundTruthCursor = Views.flatIterable(groundTruth).cursor()
			val canvasCursor = Views.flatIterable(canvas).cursor()
			val expectedCursor = Views.flatIterable(expected).cursor()
			while (expectedCursor.hasNext()) {
				val background = groundTruthCursor.next()
				val painted = canvasCursor.next()
				expectedCursor.next().set(if (isInvalid(painted)) background else painted)
			}
			return expected
		}

		/**
		 * The s1 volume a correct native nD downsample-on-commit should produce: every (channel, time, ...) slab of
		 * [committedS0] independently winner-takes-all downsampled by [scaleFactor] (spatial axes 0,1,2). Non-spatial
		 * axes are not downsampled, so each slab maps to the same slab one scale down.
		 */
		private fun expectedNDDownsample(
			committedS0: RandomAccessibleInterval<UnsignedLongType>,
			s0Dimensions: LongArray,
			s1Dimensions: LongArray,
			scaleFactor: IntArray
		): RandomAccessibleInterval<UnsignedLongType> {
			val expectedS1 = ArrayImgs.unsignedLongs(*s1Dimensions)
			val nonSpatialAxes = (3 until s0Dimensions.size).toList()

			fun downsampleSlab(axisIndex: Int, position: LongArray) {
				if (axisIndex == nonSpatialAxes.size) {
					var s0Slice: RandomAccessibleInterval<UnsignedLongType> = committedS0
					var s1Slice: RandomAccessibleInterval<UnsignedLongType> = expectedS1
					for (axis in nonSpatialAxes.reversed()) {
						s0Slice = Views.hyperSlice(s0Slice, axis, position[axis])
						s1Slice = Views.hyperSlice(s1Slice, axis, position[axis])
					}
					WinnerTakesAll.downsample(Views.extendMirrorDouble(s0Slice), s1Slice, scaleFactor[0], scaleFactor[1], scaleFactor[2])
					return
				}
				val axis = nonSpatialAxes[axisIndex]
				for (slab in 0 until s0Dimensions[axis]) {
					position[axis] = slab
					downsampleSlab(axisIndex + 1, position)
				}
			}
			downsampleSlab(0, LongArray(s0Dimensions.size))
			return expectedS1
		}

		/**
		 * A writable, disk-backed nD canvas (like the real masked-source canvas) filled with a stable random mix of
		 * INVALID and painted labels across every slab. Unlike [newTestCanvas]'s read-only image, dirty cells persist
		 * rather than reloading from the loader, so a larger nD volume stays stable across cache eviction.
		 */
		private fun newWritableTestCanvas(tmp: Path, dims: LongArray, blockSize: IntArray): DiskCachedCellImg<UnsignedLongType, *> {
			val options = DiskCachedCellImgOptions.options()
				.volatileAccesses(true)
				.dirtyAccesses(true)
				.cacheDirectory(tmp.resolve("nd-canvas"))
				.cellDimensions(*blockSize)
			val canvas = DiskCachedCellImgFactory(UnsignedLongType(), options)
				.create(dims, CellLoader { img: SingleCellArrayImg<UnsignedLongType, *> -> img.forEach { it.set(Label.INVALID) } })
			val rand = Random(100)
			canvas.forEach { it.long = Label.INVALID.takeUnless { rand.nextBoolean() } ?: rand.nextLong(10) }
			return canvas
		}

		private fun newTestCanvas(dims: LongArray, blockSize: IntArray): CachedCellImg<UnsignedLongType, *> {
			val loader = CellLoader { img: SingleCellArrayImg<UnsignedLongType, *> -> img.forEach { it.setOne() } }
			val factory = ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options().cellDimensions(*blockSize))
			val rand = Random(100)
			return factory.create(dims, UnsignedLongType(), loader).onEach {
				it.long = Label.INVALID.takeUnless { rand.nextBoolean() } ?: rand.nextLong(10)
			}
		}

		private fun getCanvasAndContainer(path: Path, testCase: TestCase): CanvasAndContainer {

			val blockSize = testCase.shape.blockSize.map { it.toInt() }.toIntArray()
			val dims = TestData.defaultDimensions(testCase)
			val canvas = newTestCanvas(dims, blockSize)

			val writer = TestData.newWriter(testCase, path)
			val container = N5ContainerState(writer)
			LOG.debug { "Created temporary ${testCase.format} container $writer" }


			return CanvasAndContainer(canvas, container, testCase)
		}

		private fun assertMultisetType(c: UnsignedLongType, l: LabelMultisetType) {
			assertEquals(1, l.entrySet().size)
			val entry = l.entrySet().iterator().next()
			assertEquals(1, entry.count)
			val isInvalid = isInvalid(c)
			if (isInvalid) {
				assertEquals(0, l.integerLong)
			} else {
				assertEquals(c.integerLong, entry.element.id())
			}
		}

		private fun <T> testPainteraData(
			canvasAndContainer: CanvasAndContainer,
			dataset: String,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			asserts: (UnsignedLongType, T) -> Unit,
			additionalAttributes: Map<String, Any> = emptyMap(),
			vararg scaleFactors: IntArray
		) {
			val (canvas, container) = canvasAndContainer
			val case = canvasAndContainer.testCase
			val writer = container.writer!!
			val blockSize = canvas.cellGrid.blockSize
			val dims = canvas.cellGrid.imgDimensions
			val attributes = TestData.datasetAttributes(case, dims)
			val uniqueAttributes = TestData.datasetAttributes(case, dims, DataType.UINT64)
			writer.createGroup(dataset)
			val dataGroup = "$dataset/data"
			val uniqueLabelsGroup = "$dataset/unique-labels"
			writer.createGroup(dataGroup)
			writer.createGroup(uniqueLabelsGroup)
			val s0 = "$dataGroup/s0"
			val u0 = "$uniqueLabelsGroup/s0"

			writer.createDataset(s0, attributes)
			writer.createDataset(u0, uniqueAttributes)
			additionalAttributes.forEach { (k, v) ->
				writer.setAttribute(dataGroup, k, v)
				writer.setAttribute(s0, k, v)
			}
			writer.setAttribute(dataset, "painteraData", PAINTERA_DATA_ATTRIBUTE)
			writer.setAttribute(dataGroup, N5Helpers.MULTI_SCALE_KEY, true)
			writer.setAttribute(uniqueLabelsGroup, N5Helpers.MULTI_SCALE_KEY, true)

			Grids.forEachOffset(LongArray(dims.size), canvas.cellGrid.gridDimensions, IntArray(dims.size) { 1 }) {
				writer.writeBlock(u0, uniqueAttributes, LongArrayDataBlock(intArrayOf(1), it, longArrayOf()))
			}

			for ((idx, factors) in scaleFactors.withIndex()) {
				val scaleNum = idx + 1
				val scaleDims = dims / factors
				val scaleAttributes = TestData.datasetAttributes(case, scaleDims)
				val uniqueScaleAttributes = TestData.datasetAttributes(case, scaleDims, DataType.UINT64)
				val sN = "$dataGroup/s$scaleNum"
				val uN = "$uniqueLabelsGroup/s$scaleNum"
				LOG.debug { "Creating scale data set with scale factor $factors: $sN" }
				writer.createDataset(sN, scaleAttributes)
				writer.createDataset(uN, uniqueScaleAttributes)
				additionalAttributes.forEach { (k, v) -> writer.setAttribute(sN, k, v) }
				writer.setAttribute(sN, N5Helpers.DOWNSAMPLING_FACTORS_KEY, IntStream.of(*factors).asDoubleStream().toArray())
			}

			testCanvasPersistence(canvasAndContainer, dataset, s0, openLabels, asserts)

			// test highest level block lookups
			val uniqueBlock0Group = N5URI.normalizeGroupPath("$dataset/unique-labels/s0")
			val scaleMappingPattern = "label-to-block-mapping/s%d"
			val uniqueBlockAttributes = writer.getDatasetAttributes(uniqueBlock0Group)
			val blocks = Grids.collectAllContainedIntervals(dims, blockSize)
			val labelToBlockMapping: TLongObjectMap<TLongSet> = TLongObjectHashMap()
			for (block in blocks) {
				val labels: TLongSet = TLongHashSet()
				val blockMin = Intervals.minAsLongArray(block)
				val blockPos = LongArray(blockMin.size) {
					blockMin[it] / blockSize[it]
				}
				val blockIndex = IntervalIndexer.positionToIndex(blockPos, canvas.cellGrid.gridDimensions)
				Views.interval(canvas, block).forEach {
					// blocks are loaded with default value 0 if not present, thus 0 will be in the updated data
					val pxVal = if (isInvalid(it)) 0 else it.integerLong
					labels.add(pxVal)
					if (pxVal != 0L) {
						labelToBlockMapping.putIfAbsent(pxVal, TLongHashSet())
						labelToBlockMapping[pxVal].add(blockIndex)
					}
				}

				val uniqueBlock = writer.readBlock<LongArray>(uniqueBlock0Group, uniqueBlockAttributes, *blockPos)
				assertEquals(labels, TLongHashSet(uniqueBlock.data))
			}

			val lookup = LabelBlockLookupFromN5Relative(scaleMappingPattern)
			lookup.setRelativeTo(writer, dataset)

			labelToBlockMapping.forEachKey { id: Long ->
				val key = LabelBlockLookupKey(0, id)
				val lookupFor = lookup.read(key)
				LOG.trace { "Found mapping $lookupFor for id $id" }
				assertEquals(labelToBlockMapping[id].size().toLong(), lookupFor.size.toLong())
				val blockIndices = Stream
					.of(*lookupFor)
					.map { interval: Interval? -> Intervals.minAsLongArray(interval) }
					.mapToLong { m: LongArray -> toBlockIndex(m, canvas.cellGrid) }
					.toArray()
				LOG.trace { "Block indices for id $id: $blockIndices" }
				assertEquals(labelToBlockMapping[id], TLongHashSet(blockIndices))
				true
			}
		}

		private fun <T> testMultiScale(
			canvasAndContainer: CanvasAndContainer,
			dataset: String,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			asserts: (UnsignedLongType, T) -> Unit,
			additionalAttributes: Map<String, Any> = emptyMap()
		) {
			val (canvas, container) = canvasAndContainer
			val s0 = container.writer!!.run {
				val attributes = TestData.datasetAttributes(canvasAndContainer.testCase, canvas.cellGrid.imgDimensions)
				createGroup(dataset)
				additionalAttributes.forEach { (k, v) -> setAttribute(dataset, k, v) }

				val s0 = "$dataset/s0"
				createDataset(s0, attributes)
				additionalAttributes.forEach { (k, v) -> setAttribute(s0, k, v) }

				setAttribute(s0, N5Helpers.MULTI_SCALE_KEY, true)
				s0
			}
			testCanvasPersistence(canvasAndContainer, dataset, s0, openLabels, asserts)
		}

		private fun <T> testSingleScale(
			canvasAndContainer: CanvasAndContainer,
			dataset: String,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			asserts: (UnsignedLongType, T) -> Unit,
			additionalAttributes: Map<String, Any> = emptyMap()
		) {
			val (canvas, container) = canvasAndContainer
			container.writer!!.run {
				val attributes = TestData.datasetAttributes(canvasAndContainer.testCase, canvas.cellGrid.imgDimensions)
				createDataset(dataset, attributes)
				additionalAttributes.forEach { (key, value) -> setAttribute(dataset, key, value) }
			}
			testCanvasPersistence(canvasAndContainer, dataset, dataset, openLabels, asserts)
		}

		private fun testMultiScaleDownsample(
			canvasAndContainer: CanvasAndContainer,
			dataset: String,
			scaleFactor: IntArray
		) {
			val (canvas, container) = canvasAndContainer
			val case = canvasAndContainer.testCase
			val writer = container.writer!!
			val dims = canvas.cellGrid.imgDimensions

			TestData.createMultiscaleScalarLabels(writer, case, dataset, dims, arrayOf(scaleFactor), DataType.UINT64)

			val metadataState = createMetadataState(container, dataset)!!
			assertTrue(metadataState is MultiScaleMetadataState) { "expected multiscale metadata for $case but got ${metadataState::class.simpleName}" }

			writeAll(metadataState, canvas)

			val s0 = N5Utils.open<UnsignedLongType>(writer, "$dataset/s0")
			assertArrayEquals(Intervals.dimensionsAsLongArray(canvas), Intervals.dimensionsAsLongArray(s0))
			for (pair in Views.interval(Views.pair(canvas, s0), s0)) {
				assertEquals(if (isInvalid(pair.a)) 0 else pair.a.integerLong, pair.b.integerLong)
			}

			val scaleDims = LongArray(dims.size) { dims[it] / scaleFactor[it] }
			val expectedS1 = ArrayImgs.unsignedLongs(*scaleDims)
			WinnerTakesAll.downsample(Views.extendMirrorDouble(s0), expectedS1, *scaleFactor)
			val storedS1 = N5Utils.open<UnsignedLongType>(writer, "$dataset/s1")
			assertArrayEquals(scaleDims, Intervals.dimensionsAsLongArray(storedS1))
			for (pair in Views.interval(Views.pair(expectedS1, storedS1), storedS1)) {
				assertEquals(pair.a.integerLong, pair.b.integerLong)
			}
		}

		private fun <T> testCanvasPersistence(
			canvasAndContainer: CanvasAndContainer,
			dataset: String,
			labelsDataset: String,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			assert: (UnsignedLongType, T) -> Unit
		) {

			val (canvas, container) = canvasAndContainer
			val metadataState = createMetadataState(container, dataset)!!

			writeAll(metadataState, canvas)

			val labels = openLabels(metadataState.writer!!, labelsDataset)
			assertArrayEquals(Intervals.dimensionsAsLongArray(canvas), Intervals.dimensionsAsLongArray(labels))

			for (pair in Views.interval(Views.pair(canvas, labels), labels)) {
				LOG.trace { "Comparing canvas ${pair.a} and background ${pair.b}" }
				assert(pair.a, pair.b)
			}
		}

		private fun writeAll(
			metadataState: MetadataState,
			canvas: CachedCellImg<UnsignedLongType, *>
		) {
			val numBlocks = Intervals.numElements(*canvas.cellGrid.gridDimensions)
			val blocks = LongArray(numBlocks.toInt()) { it.toLong() }

			val cc = CommitCanvasN5(metadataState)
			val blockDiffs = cc.persistCanvas(canvas, blocks)
			if (cc.supportsLabelBlockLookupUpdate()) cc.updateLabelBlockLookup(blockDiffs)
		}

		private val CellGrid.blockSize: IntArray
			get() = IntArray(numDimensions()).also { cellDimensions(it) }

		operator fun LongArray.div(divisor: IntArray) = LongArray(size) {
			(this[it] / divisor[it] + (if (this[it] % divisor[it] == 0L) 0 else 1)).coerceAtLeast(1)
		}

		private fun toBlockIndex(intervalMin: LongArray, grid: CellGrid): Long {
			grid.getCellPosition(intervalMin, intervalMin)
			return IntervalIndexer.positionToIndex(intervalMin, grid.gridDimensions)
		}
	}
}
