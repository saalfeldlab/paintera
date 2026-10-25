package org.janelia.saalfeldlab.paintera.control.actions.metrics

import gnu.trove.map.hash.TLongLongHashMap
import kotlinx.coroutines.runBlocking
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.img.array.ArrayImgs
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.LongArrayDataBlock
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.RawCompression
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.metrics.labels.LabelMetrics
import org.janelia.saalfeldlab.paintera.metrics.labels.MultisetCountsForRegion
import org.janelia.saalfeldlab.paintera.metrics.labels.UniqueLabelsForRegion
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.metadata.PainteraDataMultiscaleMetadataState
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Path
import java.util.concurrent.atomic.LongAdder
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UniqueLabelsForRegionTest {

	private val size = 16
	private val blockSize = 4
	private val factors = listOf(1.0, 2.0, 4.0)
	private val dataset = "label"
	private val dataGroup = "$dataset/data"
	private val uniqueLabelsGroup = "$dataset/unique-labels"

	@TempDir
	lateinit var tmp: Path

	private lateinit var writer: N5FSWriter

	private fun labelAt(x: Int, y: Int, z: Int) = 1L + ((x * 3 + y * 5 + z * 7) / 5) % 11

	@BeforeEach
	fun createDatasets() {
		writer = N5FSWriter(tmp.resolve("container.n5").toString())
		writer.createGroup(dataset)
		writer.setAttribute(dataset, "painteraData", mapOf("type" to "label"))
		writer.createGroup(dataGroup)
		writer.setAttribute(dataGroup, N5Helpers.MULTI_SCALE_KEY, true)
		writer.createGroup(uniqueLabelsGroup)
		writer.setAttribute(uniqueLabelsGroup, N5Helpers.MULTI_SCALE_KEY, true)
		val s0 = ArrayImgs.unsignedLongs(size.toLong(), size.toLong(), size.toLong())
		val access = s0.randomAccess()
		for (z in 0 until size) for (y in 0 until size) for (x in 0 until size) {
			access.setPosition(longArrayOf(x.toLong(), y.toLong(), z.toLong()))
			access.get().set(labelAt(x, y, z))
		}
		N5Utils.save(s0, writer, "$dataGroup/s0", intArrayOf(blockSize, blockSize, blockSize), RawCompression())

		factors.forEachIndexed { level, factor ->
			val levelSize = (size / factor).toLong()
			val dimensions = longArrayOf(levelSize, levelSize, levelSize)
			val blockSizes = intArrayOf(blockSize, blockSize, blockSize)
			if (level > 0) {
				writer.createDataset("$dataGroup/s$level", DatasetAttributes(dimensions, blockSizes, DataType.UINT64, RawCompression()))
				writer.setAttribute("$dataGroup/s$level", N5Helpers.DOWNSAMPLING_FACTORS_KEY, DoubleArray(3) { factor })
			}
			val uniqueLabels = DatasetAttributes(dimensions, blockSizes, DataType.UINT64, RawCompression())
			writer.createDataset("$uniqueLabelsGroup/s$level", uniqueLabels)
			if (level > 0)
				writer.setAttribute("$uniqueLabelsGroup/s$level", N5Helpers.DOWNSAMPLING_FACTORS_KEY, DoubleArray(3) { factor })
			val blocksPerAxis = (levelSize / blockSize).toInt()
			for (bz in 0 until blocksPerAxis) for (by in 0 until blocksPerAxis) for (bx in 0 until blocksPerAxis) {
				val span = blockSize * factor.toLong()
				val min = longArrayOf(bx * span, by * span, bz * span)
				val max = LongArray(3) { min[it] + span - 1 }
				val ids = idsIn(FinalInterval(min, max))
				writer.writeBlock(
					"$uniqueLabelsGroup/s$level",
					uniqueLabels,
					LongArrayDataBlock(intArrayOf(blockSize, blockSize, blockSize), longArrayOf(bx.toLong(), by.toLong(), bz.toLong()), ids.toLongArray())
				)
			}
		}
	}

	private fun idsIn(region: Interval): Set<Long> {
		val ids = sortedSetOf<Long>()
		for (z in region.min(2)..region.max(2)) for (y in region.min(1)..region.max(1)) for (x in region.min(0)..region.max(0))
			ids += labelAt(x.toInt(), y.toInt(), z.toInt())
		return ids
	}

	private fun scan(region: Interval): Pair<Set<Long>, Long> {
		val metadataState = createMetadataState(N5ContainerState(writer), dataset) as PainteraDataMultiscaleMetadataState
		val scanner = UniqueLabelsForRegion.from(metadataState, countLabelsSize = false)
		val assignment = FragmentSegmentAssignmentOnlyLocal(Supplier { TLongLongHashMap() }, FragmentSegmentAssignmentOnlyLocal.Persister { _, _ -> })
		val metrics = LabelMetrics(assignment, { false })
		val scanned = LongAdder()
		runBlocking { scanner.process(region, metrics, scanned) }

		val csv = StringWriter().also { metrics.writeCsv(it, segmentColumn = false, fragmentColumn = true, lockedColumn = false, countColumn = false) }
		val fragments = csv.toString().lines().drop(1).filter { it.isNotBlank() }.map { it.toLong() }.toSet()
		return fragments to scanned.sum()
	}

	private fun assertScanMatches(region: Interval) {
		val (fragments, scanned) = scan(region)
		assertEquals(idsIn(region), fragments)
		assertEquals(Intervals.numElements(region), scanned)
	}

	@Test
	fun `full volume`() = assertScanMatches(FinalInterval(longArrayOf(0, 0, 0), longArrayOf(15, 15, 15)))

	@Test
	fun `crop off the block grid`() = assertScanMatches(FinalInterval(longArrayOf(3, 2, 5), longArrayOf(12, 13, 10)))

	@Test
	fun `crop on the coarsest block edges`() = assertScanMatches(FinalInterval(longArrayOf(0, 0, 0), longArrayOf(7, 15, 15)))

	@Test
	fun `a single pixel`() = assertScanMatches(FinalInterval(longArrayOf(5, 6, 7), longArrayOf(5, 6, 7)))

	@Test
	fun `region past the volume is clipped`() {
		val (fragments, scanned) = scan(FinalInterval(longArrayOf(10, 10, 10), longArrayOf(40, 40, 40)))
		assertEquals(idsIn(FinalInterval(longArrayOf(10, 10, 10), longArrayOf(15, 15, 15))), fragments)
		assertEquals(216L, scanned)
	}

	@Test
	fun `only the first level unique labels are used`() {
		for (level in 1 until factors.size) {
			val path = "$uniqueLabelsGroup/s$level"
			val attributes = writer.getDatasetAttributes(path)
			val blocksPerAxis = ((size / factors[level]).toInt() / blockSize).toLong()
			for (bz in 0 until blocksPerAxis) for (by in 0 until blocksPerAxis) for (bx in 0 until blocksPerAxis)
				writer.deleteBlock(path, attributes, bx, by, bz)
		}
		assertScanMatches(FinalInterval(longArrayOf(0, 0, 0), longArrayOf(15, 15, 15)))
	}

	@Test
	fun `scalar labels get no counts`() {
		val metadataState = createMetadataState(N5ContainerState(writer), dataset) as PainteraDataMultiscaleMetadataState
		val finder = UniqueLabelsForRegion.from(metadataState, countLabelsSize = true)
		assertFalse(finder is MultisetCountsForRegion)
	}
}
