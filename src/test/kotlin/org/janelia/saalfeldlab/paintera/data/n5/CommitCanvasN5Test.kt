package org.janelia.saalfeldlab.paintera.data.n5

import bdv.cache.SharedQueue
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import io.github.oshai.kotlinlogging.KotlinLogging
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.Volatile
import net.imglib2.algorithm.util.Grids
import net.imglib2.cache.img.*
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.label.Label
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.util.IntervalIndexer
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.labels.blocks.n5.LabelBlockLookupFromN5Relative
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.N5TestUtil
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.stream.IntStream
import java.util.stream.Stream

class CommitCanvasN5Test {

	@JvmRecord
	private data class CanvasAndContainer(val canvas: CachedCellImg<UnsignedLongType, *>, val container: N5ContainerState)

	@Test
	fun testSingleScaleLabelMultisetCommit() = testSingleScale(
		getTmpCanvasAndContainer(),
		"single-scale-label-multisets",
		DataType.UINT8,
		{ n5, dataset -> N5LabelMultisets.openLabelMultiset(n5, dataset) },
		{ c: UnsignedLongType, l: LabelMultisetType -> assertMultisetType(c, l) },
		MULTISET_ATTRIBUTE
	)

	@Test
	fun testMultiScaleScaleLabelMultisetCommit() = testMultiScale(
		getTmpCanvasAndContainer(),
		"multi-scale-label-multisets",
		DataType.UINT8,
		{ n5, dataset -> N5LabelMultisets.openLabelMultiset(n5, dataset) },
		{ c: UnsignedLongType, l: LabelMultisetType -> assertMultisetType(c, l) },
		MULTISET_ATTRIBUTE
	)

	@Test
	fun testPainteraLabelMultisetCommit() = testPainteraData(
		getTmpCanvasAndContainer(),
		"paintera-label-multisets",
		DataType.UINT8,
		{ n5, dataset -> N5LabelMultisets.openLabelMultiset(n5, dataset) },
		{ c: UnsignedLongType, l: LabelMultisetType -> assertMultisetType(c, l) },
		MULTISET_ATTRIBUTE,
		intArrayOf(2, 2, 3)
	)

	@Test
	fun testSingleScaleUint64Commit() = testSingleScale(getTmpCanvasAndContainer(),
		"single-scale-uint64",
		DataType.UINT64,
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c: UnsignedLongType, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) }, HashMap())

	@Test
	fun testMultiScaleUint64Commit() = testMultiScale(
		getTmpCanvasAndContainer(),
		"multi-scale-uint64",
		DataType.UINT64,
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c: UnsignedLongType, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) }, HashMap()
	)

	@Test
	fun testPainteraUint64Commit() = testPainteraData(
		getTmpCanvasAndContainer(),
		"paintera-uint64",
		DataType.UINT64,
		{ n5, dataset -> N5Utils.open(n5, dataset) },
		{ c, l: UnsignedLongType -> assertEquals(if (isInvalid(c)) 0 else c.integerLong, l.integerLong) },
		HashMap(),
		intArrayOf(2, 2, 3)
	)


	companion object {
		private val LOG = KotlinLogging.logger { }
		private val INVALID = UnsignedLongType(Label.INVALID)
		private val MULTISET_ATTRIBUTE: Map<String, Any> = mapOf(N5Helpers.LABEL_MULTISETTYPE_KEY to true)
		private val PAINTERA_DATA_ATTRIBUTE: Map<String, Any> = mapOf("type" to "label")

		private fun isInvalid(pixel: UnsignedLongType): Boolean {
			val isInvalid = INVALID.valueEquals(pixel)
			LOG.trace { "$pixel is invalid? $isInvalid" }
			return isInvalid
		}

		private fun newTestCanvas(): CachedCellImg<UnsignedLongType, *> {
			val dims = longArrayOf(10, 20, 30)
			val blockSize = intArrayOf(5, 7, 9)
			val loader = CellLoader { img: SingleCellArrayImg<UnsignedLongType, *> -> img.forEach { it.setOne() } }
			val factory = ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options().cellDimensions(*blockSize))
			val rand = Random(100)
			return factory.create(dims, UnsignedLongType(), loader).onEach {
				val value = if (rand.nextBoolean()) rand.nextLong(10) else Label.INVALID
				it.setInteger(value)
			}
		}

		private fun getTmpCanvasAndContainer(): CanvasAndContainer {
			val canvas = newTestCanvas()
			val writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled())
			val container = N5ContainerState(writer)
			LOG.debug { "Created temporary N5 container $writer" }
			return CanvasAndContainer(canvas, container)
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
			dataType: DataType,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			asserts: (UnsignedLongType, T) -> Unit,
			additionalAttributes: Map<String, Any>,
			vararg scaleFactors: IntArray
		) {
			val (canvas, container) = canvasAndContainer
			val writer = container.writer!!
			val blockSize = canvas.cellGrid.blockSize
			val dims = canvas.cellGrid.imgDimensions
			val attributes = DatasetAttributes(dims, blockSize, dataType, GzipCompression())
			val uniqueAttributes = DatasetAttributes(dims, blockSize, DataType.UINT64, GzipCompression())
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
				val scaleNum = idx+1
				val scaleDims = dims / factors
				val scaleAttributes = DatasetAttributes(scaleDims, blockSize, dataType, GzipCompression())
				val uniqueScaleAttributes = DatasetAttributes(scaleDims, blockSize, DataType.UINT64, GzipCompression())
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

				val uniqueBlock = writer.readBlock(uniqueBlock0Group, uniqueBlockAttributes, *blockPos)
				assertEquals(labels, TLongHashSet(uniqueBlock.data as LongArray))
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
			dataType: DataType,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			asserts: (UnsignedLongType, T) -> Unit,
			additionalAttributes: Map<String, Any>
		) {
			val (canvas, container) = canvasAndContainer
			val s0 = container.writer!!.run {
				val attributes = DatasetAttributes(canvas.cellGrid.imgDimensions, canvas.cellGrid.blockSize, dataType, GzipCompression())
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
			dataType: DataType,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			asserts: (UnsignedLongType, T) -> Unit,
			additionalAttributes: Map<String, Any> = emptyMap()
		) {
			val (canvas, container) = canvasAndContainer
			container.writer!!.run {
				val attributes = DatasetAttributes(canvas.cellGrid.imgDimensions, canvas.cellGrid.blockSize, dataType, GzipCompression())
				createDataset(dataset, attributes)
				additionalAttributes.forEach { (key, value) -> setAttribute(dataset, key, value) }
			}
			testCanvasPersistence(canvasAndContainer, dataset, dataset, openLabels, asserts)
		}

		private fun <T> testCanvasPersistence(
			canvasAndContainer: CanvasAndContainer,
			dataset: String,
			labelsDataset: String,
			openLabels: (N5Reader, String) -> RandomAccessibleInterval<T>,
			assert: (UnsignedLongType, T) -> Unit
		) {

			val (canvas, container) = canvasAndContainer
			val metadataState = createMetadataState(container, dataset) ?: DummyMetadataState(dataset, container)

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
			/* persistCanvas now has a call to update its progress, which is on the UI thread. This means we need the UI thread to exist first. */
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

private class DummyMetadataState(override val dataset: String, override val n5ContainerState: N5ContainerState) : MetadataState {

	override var group: String = dataset
	override val writer: N5Writer = n5ContainerState.writer!!
	override var reader: N5Reader = n5ContainerState.reader
	override var unit: String = "pixel"
	override var translation: DoubleArray = DoubleArray(0)
	override var spatialAxes: Map<Axis, Int> = MetadataUtils.SpatialAxes.default
	override var channelAxis: Pair<Axis, Int>? = null
	override var timeAxis: Pair<Axis, Int>? = null
	override var virtualCrop: Interval? = null
	override var resolution: DoubleArray = DoubleArray(0)
	override var maxIntensity: Double = 0.0
	override var minIntensity: Double = 0.0
	override var isLabelMultiset: Boolean = true
	override var isLabel: Boolean = true
	override var transform: AffineTransform3D = error("not necessary for test")
	override var datasetAttributes: DatasetAttributes = error("not necessary for test")
	override val metadata: N5Metadata = N5Metadata { "TEST" }

	override fun copy(): MetadataState = this
	override fun updateTransform(newTransform: AffineTransform3D) = Unit
	override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) = Unit

	override fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> {
		error("not necessary for test")
	}
}
