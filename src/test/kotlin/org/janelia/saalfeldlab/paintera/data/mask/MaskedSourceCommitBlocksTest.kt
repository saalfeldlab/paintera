package org.janelia.saalfeldlab.paintera.data.mask

import bdv.cache.SharedQueue
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.CellLoader
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.cache.img.SingleCellArrayImg
import net.imglib2.converter.Converters
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.GzipCompression
import org.janelia.saalfeldlab.n5.LongArrayDataBlock
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.testdata.TestData
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * A previous bug introduced the following issue that is currently tests:
 * - Painting would commit the entire bounding box of blocks, not just the painted blocks (especially bad when painting non-orthogonally)
 * - A Modification, regardless how small, would commit to s0 correctly, but rewrite the entire downscale volume(s)
 */
class MaskedSourceCommitBlocksTest {

	private val queue = SharedQueue(1)
	private val executor = Executors.newFixedThreadPool(4)

	private val dimensions = longArrayOf(256, 256, 256)
	private val blockSize = intArrayOf(32, 32, 32)

	/* a 4^3 box at a block corner: exactly 8 painted blocks */
	private val paintedRegion = FinalInterval(longArrayOf(30, 30, 30), longArrayOf(33, 33, 33))
	private val paintedLabel = 5L

	/**
	 * The brush applies its mask with the stroke's source-space bounding box; only the blocks that actually
	 * received paint may end up in the commit set, and only those may be written at s0. The lower scale gets the
	 * spatial projection of the painted blocks, not the whole level.
	 */
	@Test
	fun commitOnlyPaintedBlocks(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("repro.n5").toString())
		val group = "label"
		writer.createGroup(group)
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true)
		writer.createDataset("$group/s0", dimensions, blockSize, DataType.UINT64, GzipCompression())
		writer.createDataset("$group/s1", LongArray(3) { dimensions[it] / 2 }, blockSize, DataType.UINT64, GzipCompression())
		writer.setAttribute("$group/s1", N5Helpers.DOWNSAMPLING_FACTORS_KEY, doubleArrayOf(2.0, 2.0, 2.0))

		val metadataState = createMetadataState(N5ContainerState(writer), group)!!.also { it.isLabel = true }
		assertTrue(metadataState is MultiScaleMetadataState)
		val dataSource = N5DataSource<UnsignedLongType, VolatileUnsignedLongType>(metadataState, group, queue, 0)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
			as MaskedSource<UnsignedLongType, VolatileUnsignedLongType>

		/* a non-tracking mask, like the brush's ViewerMask: unpainted voxels are INVALID */
		val store = invalidFilledStore()
		val volatileView = Converters.convert(
			store as RandomAccessibleInterval<UnsignedLongType>,
			{ source, target -> target.get().set(source); target.isValid = true },
			VolatileUnsignedLongType()
		)
		val mask = SourceMask(MaskInfo(0, 0), store, volatileView, null, null, null)
		masked.setMask(mask, MaskedSource.VALID_LABEL_CHECK)
		Views.interval(store, paintedRegion).forEach { it.set(paintedLabel) }

		/* the stroke's source-space bounding box, much larger than the painted voxels */
		val strokeBoundingBox = FinalInterval(longArrayOf(10, 10, 10), longArrayOf(150, 150, 150))
		masked.applyMask(mask, strokeBoundingBox, MaskedSource.VALID_LABEL_CHECK)
		var waited = 0
		while (masked.isMaskInUseBinding.get() && waited < 20_000) {
			Thread.sleep(20); waited += 20
		}

		assertEquals(8, masked.affectedBlocks.size) { "commit set must contain only the painted blocks" }

		val canvasField = MaskedSource::class.java.getDeclaredField("dataCanvases").apply { isAccessible = true }
		@Suppress("UNCHECKED_CAST")
		val canvas = (canvasField.get(masked) as Array<Any?>)[0] as CachedCellImg<UnsignedLongType, *>
		CommitCanvasN5(metadataState).persistCanvas(canvas, masked.affectedBlocks)

		assertEquals(8, countBlockFiles(writer, tmp, "$group/s0")) { "s0 must write only the painted blocks" }
		/* the painted s0 blocks span voxels [0,63]^3, which downsample into s1 voxels [0,31]^3: one s1 block */
		assertEquals(1, countBlockFiles(writer, tmp, "$group/s1")) { "s1 must write only the blocks the commit projects into" }
	}

	/** An over-reported block list must not produce writes where the canvas holds no paint. */
	@Test
	fun persistSkipsUnpaintedBlocks(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("repro.n5").toString())
		val dataset = "label"
		writer.createDataset(dataset, dimensions, blockSize, DataType.UINT64, GzipCompression())
		val metadataState = createMetadataState(N5ContainerState(writer), dataset)!!.also { it.isLabel = true }

		val canvas = invalidFilledStore()
		Views.interval(canvas, paintedRegion).forEach { it.set(paintedLabel) }

		val numBlocks = net.imglib2.util.Intervals.numElements(*canvas.cellGrid.gridDimensions).toInt()
		CommitCanvasN5(metadataState).persistCanvas(canvas, LongArray(numBlocks) { it.toLong() })

		assertEquals(8, countBlockFiles(writer, tmp, dataset)) { "unpainted blocks must not be written" }
	}

	/**
	 * A partial commit must keep unique-labels and the label-to-block mapping consistent: painted blocks get their
	 * unique-labels rewritten and the mapping patched (added and removed ids), untouched blocks keep both.
	 */
	@Test
	fun partialCommitUpdatesLookupAndUniqueLabels(@TempDir tmp: Path) {
		val (writer, metadataState, lookup, gridDims, s1GridDims) = painteraDataFixture(tmp, background = 1L)
		val dims = longArrayOf(128, 128, 128)
		val background = 1L
		val uniqueGroup = "label/unique-labels"
		val dataGroup = "label/data"

		/* paint the small box, fully replace the block at grid (2,2,2), and clear the block at (3,0,0) with background */
		val canvas = invalidFilledStore(dims)
		Views.interval(canvas, paintedRegion).forEach { it.set(paintedLabel) }
		val replacingLabel = 7L
		Views.interval(canvas, blockInterval(longArrayOf(2, 2, 2))).forEach { it.set(replacingLabel) }
		Views.interval(canvas, blockInterval(longArrayOf(3, 0, 0))).forEach { it.set(Label.BACKGROUND) }

		/* the (0|1)^3 corner blocks plus the fully replaced and the cleared block */
		val paintedCornerBlocks = setOf(0L, 1L, 4L, 5L, 16L, 17L, 20L, 21L)
		val replacedBlock = 42L
		val clearedBlock = 3L
		val committedBlocks = (paintedCornerBlocks + replacedBlock + clearedBlock).toLongArray()

		val commitCanvas = CommitCanvasN5(metadataState)
		val blockDiffs = commitCanvas.persistCanvas(canvas, committedBlocks)
		assertTrue(commitCanvas.supportsLabelBlockLookupUpdate())
		commitCanvas.updateLabelBlockLookup(blockDiffs)

		/* unique-labels/s0: painted blocks gain 5, the replaced block is exactly {7}, the cleared block {0}, untouched keep {1} */
		assertEquals(setOf(background, paintedLabel), readUniqueLabels(writer, "$uniqueGroup/s0", longArrayOf(0, 0, 0)))
		assertEquals(setOf(replacingLabel), readUniqueLabels(writer, "$uniqueGroup/s0", longArrayOf(2, 2, 2)))
		assertEquals(setOf(Label.BACKGROUND), readUniqueLabels(writer, "$uniqueGroup/s0", longArrayOf(3, 0, 0)))
		assertEquals(setOf(background), readUniqueLabels(writer, "$uniqueGroup/s0", longArrayOf(3, 3, 3)))

		/* s0 mapping: 5 -> exactly the painted blocks; 7 -> the replaced block; 0 -> the cleared block;
		 * 1 loses only the fully replaced and cleared blocks */
		assertEquals(paintedCornerBlocks, lookupBlockIndices(lookup, 0, paintedLabel, gridDims))
		assertEquals(setOf(replacedBlock), lookupBlockIndices(lookup, 0, replacingLabel, gridDims))
		assertEquals(setOf(clearedBlock), lookupBlockIndices(lookup, 0, Label.BACKGROUND, gridDims))
		assertEquals(allBlockIndices(gridDims) - replacedBlock - clearedBlock, lookupBlockIndices(lookup, 0, background, gridDims))

		/* the cleared block reads back as background */
		val s0Attributes = writer.getDatasetAttributes("$dataGroup/s0")
		val clearedData = writer.readBlock<LongArray>("$dataGroup/s0", s0Attributes, 3, 0, 0)
		assertTrue(clearedData.data.all { it == Label.BACKGROUND }) { "the cleared block must hold background" }

		/* s1: the box downsamples into s1 block (0,0,0), the replaced block into (1,1,1), the cleared into (1,0,0);
		 * 1 survives everywhere at s1 */
		assertEquals(setOf(background, paintedLabel), readUniqueLabels(writer, "$uniqueGroup/s1", longArrayOf(0, 0, 0)))
		assertEquals(setOf(background, replacingLabel), readUniqueLabels(writer, "$uniqueGroup/s1", longArrayOf(1, 1, 1)))
		assertEquals(setOf(background, Label.BACKGROUND), readUniqueLabels(writer, "$uniqueGroup/s1", longArrayOf(1, 0, 0)))
		assertEquals(setOf(0L), lookupBlockIndices(lookup, 1, paintedLabel, s1GridDims))
		assertEquals(setOf(7L), lookupBlockIndices(lookup, 1, replacingLabel, s1GridDims))
		assertEquals(setOf(1L), lookupBlockIndices(lookup, 1, Label.BACKGROUND, s1GridDims))
		assertEquals(allBlockIndices(s1GridDims), lookupBlockIndices(lookup, 1, background, s1GridDims))
	}

	/**
	 * Paint and then fully erase (to transparent) before committing: the canvas holds only non-regular values in
	 * those blocks, which keep the background at merge, so nothing may be written at any level.
	 */
	@Test
	fun erasedPaintIsNotCommitted(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("repro.n5").toString())
		val group = "label"
		writer.createGroup(group)
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true)
		writer.createDataset("$group/s0", dimensions, blockSize, DataType.UINT64, GzipCompression())
		writer.createDataset("$group/s1", LongArray(3) { dimensions[it] / 2 }, blockSize, DataType.UINT64, GzipCompression())
		writer.setAttribute("$group/s1", N5Helpers.DOWNSAMPLING_FACTORS_KEY, doubleArrayOf(2.0, 2.0, 2.0))

		val metadataState = createMetadataState(N5ContainerState(writer), group)!!.also { it.isLabel = true }
		val dataSource = N5DataSource<UnsignedLongType, VolatileUnsignedLongType>(metadataState, group, queue, 0)
		val canvasDir = tmp.resolve("canvas").absolutePathString()
		val masked = Masks.maskedSource(dataSource, queue, canvasDir, { canvasDir }, CommitCanvasN5(metadataState), executor)
			as MaskedSource<UnsignedLongType, VolatileUnsignedLongType>

		applyLabelOverRegion(masked, paintedLabel)
		applyLabelOverRegion(masked, Label.TRANSPARENT)

		/* the blocks stay in the commit set, but the writer skips them: no regular value survives the erase */
		assertEquals(8, masked.affectedBlocks.size)

		val canvasField = MaskedSource::class.java.getDeclaredField("dataCanvases").apply { isAccessible = true }
		@Suppress("UNCHECKED_CAST")
		val canvas = (canvasField.get(masked) as Array<Any?>)[0] as CachedCellImg<UnsignedLongType, *>
		CommitCanvasN5(metadataState).persistCanvas(canvas, masked.affectedBlocks)

		assertEquals(0, countBlockFiles(writer, tmp, "$group/s0")) { "fully erased blocks must not be written" }
		assertEquals(0, countBlockFiles(writer, tmp, "$group/s1")) { "no s0 change means no downsample writes" }
	}

	/**
	 * Editing the same spatial block at two timepoints commits one nD block per timepoint at s0, and one
	 * downsampled block per timepoint slab at each lower level; the other timepoints stay untouched.
	 */
	@Test
	fun twoTimepointEditCommitsPerTimepointBlocks(@TempDir tmp: Path) {
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("repro.n5").toString())
		val dims = longArrayOf(64, 64, 64, 4)
		val blockSize4D = intArrayOf(16, 16, 16, 1)
		val group = "label"
		writer.createGroup(group)
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true)
		writer.createDataset("$group/s0", dims, blockSize4D, DataType.UINT64, GzipCompression())
		writer.createDataset("$group/s1", longArrayOf(32, 32, 32, 4), blockSize4D, DataType.UINT64, GzipCompression())
		writer.setAttribute("$group/s1", N5Helpers.DOWNSAMPLING_FACTORS_KEY, doubleArrayOf(2.0, 2.0, 2.0, 1.0))

		val metadataState = createMetadataState(N5ContainerState(writer), group)!!.also { it.isLabel = true }

		val canvas = DiskCachedCellImgFactory(
			UnsignedLongType(),
			DiskCachedCellImgOptions.options().cellDimensions(*blockSize4D)
		).create(dims, CellLoader { img: SingleCellArrayImg<UnsignedLongType, *> -> img.forEach { it.set(Label.INVALID) } })

		/* paint inside the spatial block (1,1,1) at timepoints 0 and 1 */
		for (timepoint in 0L..1L)
			Views.interval(
				canvas,
				FinalInterval(longArrayOf(16, 16, 16, timepoint), longArrayOf(19, 19, 19, timepoint))
			).forEach { it.set(paintedLabel) }

		/* the nD grid is [4,4,4,4]: the spatial block (1,1,1) at t=0 and at t=1 */
		val blockAtTimepoint0 = 1L + 4L + 16L
		val blockAtTimepoint1 = blockAtTimepoint0 + 64L
		CommitCanvasN5(metadataState).persistCanvas(canvas, longArrayOf(blockAtTimepoint0, blockAtTimepoint1))

		assertEquals(2, countBlockFiles(writer, tmp, "$group/s0")) { "one s0 block per edited timepoint" }
		assertEquals(2, countBlockFiles(writer, tmp, "$group/s1")) { "one downsampled block per edited timepoint slab" }
	}

	private fun applyLabelOverRegion(masked: MaskedSource<UnsignedLongType, VolatileUnsignedLongType>, label: Long) {
		val store = invalidFilledStore()
		val volatileView = Converters.convert(
			store as RandomAccessibleInterval<UnsignedLongType>,
			{ source, target -> target.get().set(source); target.isValid = true },
			VolatileUnsignedLongType()
		)
		val mask = SourceMask(MaskInfo(0, 0), store, volatileView, null, null, null)
		masked.setMask(mask, MaskedSource.VALID_LABEL_CHECK)
		Views.interval(store, paintedRegion).forEach { it.set(label) }
		masked.applyMask(mask, paintedRegion, MaskedSource.VALID_LABEL_CHECK)
		var waited = 0
		while (masked.isMaskInUseBinding.get() && waited < 20_000) {
			Thread.sleep(20); waited += 20
		}
	}

	/**
	 * A commit that touches every block drives many simultaneous unique-labels writes and full mapping turnover:
	 * every unique-labels block holds exactly the new label, its mapping covers everything, and the replaced
	 * background label's mapping is emptied at both levels.
	 */
	@Test
	fun fullCommitRewritesAllUniqueLabelsAndMapping(@TempDir tmp: Path) {
		val (writer, metadataState, lookup, gridDims, s1GridDims) = painteraDataFixture(tmp, background = 1L)
		val replacingLabel = 7L

		val canvas = invalidFilledStore(longArrayOf(128, 128, 128))
		canvas.forEach { it.set(replacingLabel) }

		val numBlocks = (gridDims[0] * gridDims[1] * gridDims[2]).toInt()
		val commitCanvas = CommitCanvasN5(metadataState)
		val blockDiffs = commitCanvas.persistCanvas(canvas, LongArray(numBlocks) { it.toLong() })
		commitCanvas.updateLabelBlockLookup(blockDiffs)

		net.imglib2.algorithm.util.Grids.forEachOffset(LongArray(3), LongArray(3) { gridDims[it] - 1 }, intArrayOf(1, 1, 1)) { pos ->
			assertEquals(setOf(replacingLabel), readUniqueLabels(writer, "label/unique-labels/s0", pos.clone()))
		}
		net.imglib2.algorithm.util.Grids.forEachOffset(LongArray(3), LongArray(3) { s1GridDims[it] - 1 }, intArrayOf(1, 1, 1)) { pos ->
			assertEquals(setOf(replacingLabel), readUniqueLabels(writer, "label/unique-labels/s1", pos.clone()))
		}
		assertEquals(allBlockIndices(gridDims), lookupBlockIndices(lookup, 0, replacingLabel, gridDims))
		assertEquals(allBlockIndices(s1GridDims), lookupBlockIndices(lookup, 1, replacingLabel, s1GridDims))
		assertEquals(emptySet<Long>(), lookupBlockIndices(lookup, 0, 1L, gridDims)) { "the replaced background label must map to nothing" }
		assertEquals(emptySet<Long>(), lookupBlockIndices(lookup, 1, 1L, s1GridDims))
	}

	/**
	 * Label ids share storage buckets in the mapping dataset (`id / blockSize`, block size 3 by default);
	 * updating one id must not clobber the entries of the other ids in the same bucket. Guards against a bulk
	 * write that replaces whole buckets, like `LabelBlockLookupFromN5Relative.set`.
	 */
	@Test
	fun lookupUpdatePreservesBucketNeighbors(@TempDir tmp: Path) {
		/* 99, 100, and 101 all live in mapping bucket 33 */
		val (writer, metadataState, lookup, gridDims, _) = painteraDataFixture(tmp, background = 100L)
		val bucketNeighborId = 101L
		val neighborInterval = blockInterval(longArrayOf(1, 2, 3))
		lookup.write(LabelBlockLookupKey(0, bucketNeighborId), neighborInterval)

		val newLabel = 99L
		val canvas = invalidFilledStore(longArrayOf(128, 128, 128))
		Views.interval(canvas, paintedRegion).forEach { it.set(newLabel) }
		val paintedCornerBlocks = setOf(0L, 1L, 4L, 5L, 16L, 17L, 20L, 21L)

		val commitCanvas = CommitCanvasN5(metadataState)
		val blockDiffs = commitCanvas.persistCanvas(canvas, paintedCornerBlocks.toLongArray())
		commitCanvas.updateLabelBlockLookup(blockDiffs)

		/* the new label's mapping is exact, and its bucket-mates kept their entries */
		assertEquals(paintedCornerBlocks, lookupBlockIndices(lookup, 0, newLabel, gridDims))
		assertEquals(allBlockIndices(gridDims), lookupBlockIndices(lookup, 0, 100L, gridDims)) { "the background id shares the bucket and must be intact" }
		val neighborIntervals = lookup.read(LabelBlockLookupKey(0, bucketNeighborId))
		assertEquals(1, neighborIntervals.size) { "the bucket neighbor's entry must survive" }
		assertTrue(Intervals.equals(neighborInterval, neighborIntervals[0])) { "the bucket neighbor's interval must be unchanged" }
	}

	private data class PainteraDataFixture(
		val writer: N5Writer,
		val metadataState: org.janelia.saalfeldlab.paintera.state.metadata.MetadataState,
		val lookup: org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup,
		val gridDims: LongArray,
		val s1GridDims: LongArray
	)

	/** A paintera-data label group (s0 + s1) filled with [background], unique-labels {background} per block, and a complete mapping. */
	private fun painteraDataFixture(tmp: Path, background: Long): PainteraDataFixture {
		TestData.registerLabelBlockLookupAdapter()
		val writer = Paintera.n5Factory.newWriter(StorageFormat.N5, tmp.resolve("repro.n5").toString())
		val dims = longArrayOf(128, 128, 128)
		val s1Dims = LongArray(3) { dims[it] / 2 }
		val gridDims = LongArray(3) { dims[it] / blockSize[it] }
		val s1GridDims = LongArray(3) { s1Dims[it] / blockSize[it] }

		val group = "label"
		val dataGroup = "$group/data"
		val uniqueGroup = "$group/unique-labels"
		writer.createGroup(group)
		writer.setAttribute(group, "painteraData", mapOf("type" to "label"))
		writer.createGroup(dataGroup)
		writer.setAttribute(dataGroup, N5Helpers.MULTI_SCALE_KEY, true)
		writer.createGroup(uniqueGroup)
		writer.setAttribute(uniqueGroup, N5Helpers.MULTI_SCALE_KEY, true)
		writer.createDataset("$dataGroup/s0", dims, blockSize, DataType.UINT64, GzipCompression())
		writer.createDataset("$dataGroup/s1", s1Dims, blockSize, DataType.UINT64, GzipCompression())
		writer.setAttribute("$dataGroup/s1", N5Helpers.DOWNSAMPLING_FACTORS_KEY, doubleArrayOf(2.0, 2.0, 2.0))
		writer.createDataset("$uniqueGroup/s0", dims, blockSize, DataType.UINT64, GzipCompression())
		writer.createDataset("$uniqueGroup/s1", s1Dims, blockSize, DataType.UINT64, GzipCompression())

		val ones = LongArray(blockSize[0] * blockSize[1] * blockSize[2]) { background }
		fillLevel(writer, "$dataGroup/s0", "$uniqueGroup/s0", gridDims, ones, background)
		fillLevel(writer, "$dataGroup/s1", "$uniqueGroup/s1", s1GridDims, ones, background)

		val metadataState = createMetadataState(N5ContainerState(writer), group)!!.also { it.isLabel = true }
		val lookup = N5Helpers.getLabelBlockLookup(metadataState)
		lookup.write(LabelBlockLookupKey(0, background), *allBlockIntervals(gridDims))
		lookup.write(LabelBlockLookupKey(1, background), *allBlockIntervals(s1GridDims))
		return PainteraDataFixture(writer, metadataState, lookup, gridDims, s1GridDims)
	}

	private fun fillLevel(writer: N5Writer, dataDataset: String, uniqueDataset: String, gridDims: LongArray, blockData: LongArray, background: Long) {
		val dataAttributes = writer.getDatasetAttributes(dataDataset)
		val uniqueAttributes = writer.getDatasetAttributes(uniqueDataset)
		net.imglib2.algorithm.util.Grids.forEachOffset(LongArray(3), LongArray(3) { gridDims[it] - 1 }, intArrayOf(1, 1, 1)) { pos ->
			writer.writeBlock(dataDataset, dataAttributes, LongArrayDataBlock(blockSize, pos.clone(), blockData))
			writer.writeBlock(uniqueDataset, uniqueAttributes, LongArrayDataBlock(intArrayOf(1), pos.clone(), longArrayOf(background)))
		}
	}

	private fun blockInterval(gridPosition: LongArray) = FinalInterval(
		LongArray(3) { gridPosition[it] * blockSize[it] },
		LongArray(3) { (gridPosition[it] + 1) * blockSize[it] - 1 })

	private fun allBlockIntervals(gridDims: LongArray): Array<Interval> {
		val intervals = mutableListOf<Interval>()
		net.imglib2.algorithm.util.Grids.forEachOffset(LongArray(3), LongArray(3) { gridDims[it] - 1 }, intArrayOf(1, 1, 1)) { pos ->
			intervals += blockInterval(pos.clone())
		}
		return intervals.toTypedArray()
	}

	private fun allBlockIndices(gridDims: LongArray) = (0 until gridDims[0] * gridDims[1] * gridDims[2]).toSet()

	private fun readUniqueLabels(writer: N5Writer, dataset: String, gridPosition: LongArray): Set<Long> {
		val attributes = writer.getDatasetAttributes(dataset)
		val block = writer.readBlock<LongArray>(dataset, attributes, *gridPosition)
		return block.data.toSet()
	}

	private fun lookupBlockIndices(lookup: org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup, level: Int, id: Long, gridDims: LongArray): Set<Long> {
		return lookup.read(LabelBlockLookupKey(level, id)).map { interval ->
			val gridPosition = LongArray(3) { interval.min(it) / blockSize[it] }
			net.imglib2.util.IntervalIndexer.positionToIndex(gridPosition, gridDims)
		}.toSet()
	}

	private fun invalidFilledStore(dims: LongArray = dimensions) = DiskCachedCellImgFactory(
		UnsignedLongType(),
		DiskCachedCellImgOptions.options().cellDimensions(*blockSize)
	).create(dims, CellLoader { img: SingleCellArrayImg<UnsignedLongType, *> -> img.forEach { it.set(Label.INVALID) } })

	private fun countBlockFiles(writer: N5Writer, tmp: Path, dataset: String): Int {
		val datasetDir = tmp.resolve("repro.n5").resolve(dataset)
		return Files.walk(datasetDir).asSequence()
			.filter { it.isRegularFile() && it.name != "attributes.json" }
			.count()
	}
}
