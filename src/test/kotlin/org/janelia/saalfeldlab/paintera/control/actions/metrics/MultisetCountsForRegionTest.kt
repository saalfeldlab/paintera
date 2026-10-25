package org.janelia.saalfeldlab.paintera.control.actions.metrics

import gnu.trove.map.hash.TLongLongHashMap
import kotlinx.coroutines.runBlocking
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.type.label.LabelMultisetEntry
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.label.LabelMultisetTypeDownscaler
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.n5.ByteArrayDataBlock
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.data.n5.openLabelMultiset
import org.janelia.saalfeldlab.paintera.metrics.labels.LabelMetrics
import org.janelia.saalfeldlab.paintera.metrics.labels.MultisetCountsForRegion
import org.janelia.saalfeldlab.paintera.metrics.labels.PainteraDataLevels
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
import java.nio.file.Paths
import java.util.concurrent.atomic.LongAdder
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultisetCountsForRegionTest {

	private val dataset = "paintera_labels"
	private val regions = listOf(
		FinalInterval(longArrayOf(0, 0, 0), longArrayOf(99, 99, 99)),
		FinalInterval(longArrayOf(3, 17, 5), longArrayOf(81, 90, 66)),
		FinalInterval(longArrayOf(0, 0, 0), longArrayOf(63, 63, 63)),
		FinalInterval(longArrayOf(40, 40, 40), longArrayOf(40, 40, 40)),
	)

	@TempDir
	lateinit var tmp: Path

	private lateinit var writer: N5FSWriter

	@BeforeEach
	fun copyContainer() {
		val source = Paths.get(MultisetCountsForRegionTest::class.java.getResource("/export_tests/export_test.n5")!!.toURI())
		source.toFile().copyRecursively(tmp.resolve("container.n5").toFile())
		writer = N5FSWriter(tmp.resolve("container.n5").toString())
	}

	private fun openState() = createMetadataState(N5ContainerState(writer), dataset) as PainteraDataMultiscaleMetadataState

	private fun truth(region: Interval): Map<Long, Long> {
		val image = openLabelMultiset(writer, "$dataset/data/s0")
		val counts = HashMap<Long, Long>()
		val entryRef = LabelMultisetEntry()
		Views.flatIterable(Views.interval(image, region)).forEach { multiset ->
			for (entry in multiset.entrySetWithRef(entryRef))
				counts.merge(entry.element.id(), entry.count.toLong(), Long::plus)
		}
		counts.remove(0L)
		return counts
	}

	private fun scan(
        state: PainteraDataMultiscaleMetadataState,
        region: Interval,
        countLabels: Boolean,
        finder: UniqueLabelsForRegion = UniqueLabelsForRegion.from(state, countLabels),
	): Map<Long, Long> {
		val assignment = FragmentSegmentAssignmentOnlyLocal(Supplier { TLongLongHashMap() }, FragmentSegmentAssignmentOnlyLocal.Persister { _, _ -> })
		val metrics = LabelMetrics(assignment, { false })
		val scanned = LongAdder()
		runBlocking { finder.process(region, metrics, scanned) }
		assertEquals(Intervals.numElements(Intervals.intersect(region, finder.firstLevelBounds())), scanned.sum())

		val csv = StringWriter().also { metrics.writeCsv(it, segmentColumn = false, fragmentColumn = true, lockedColumn = false, countColumn = countLabels) }
		return csv.toString().lines().drop(1).filter { it.isNotBlank() }.associate { line ->
			val cells = line.split(",")
			cells[0].toLong() to (cells.getOrNull(1)?.toLong() ?: 0L)
		}
	}

	private fun assertCountsMatch(state: PainteraDataMultiscaleMetadataState) = regions.forEach { region ->
		assertEquals(truth(region), scan(state, region, countLabels = true), "counts in $region")
	}

	private fun assertIdsMatch(state: PainteraDataMultiscaleMetadataState, finder: UniqueLabelsForRegion = UniqueLabelsForRegion.from(state, countLabelsSize = false)) = regions.forEach { region ->
		assertEquals(truth(region).keys, scan(state, region, countLabels = false, finder = finder).keys, "ids in $region")
	}

	/**
	 * Replace the downscaled levels with ones limited to [maxNumEntries] entries per pixel, each from the level above
	 * it, and declare the limit. Returns the number of first level pixels the limited levels lost track of.
	 */
	private fun limitEntries(maxNumEntries: Int): Long {
		val factors = intArrayOf(2, 2, 2)
		for (level in 1..2) {
			val sourcePath = "$dataset/data/s${level - 1}"
			val targetPath = "$dataset/data/s$level"
			val source = openLabelMultiset(writer, sourcePath)
			val targetAttributes = writer.getDatasetAttributes(targetPath)
			val blockSize = targetAttributes.blockSize
			val grid = net.imglib2.img.cell.CellGrid(targetAttributes.dimensions, blockSize)
			net.imglib2.algorithm.util.Grids.forEachOffset(LongArray(3), LongArray(3) { grid.gridDimension(it) - 1 }, intArrayOf(1, 1, 1)) { gridPosition ->
				val cellMin = LongArray(3)
				val cellDimensions = IntArray(3)
				grid.getCellDimensions(gridPosition, cellMin, cellDimensions)
				val sourceMin = LongArray(3) { cellMin[it] * factors[it] }
				val sourceMax = LongArray(3) { minOf(source.max(it), (cellMin[it] + cellDimensions[it]) * factors[it] - 1) }
				val sourceCell = Views.zeroMin(Views.interval(source, FinalInterval(sourceMin, sourceMax)))
				val cell = LabelMultisetTypeDownscaler.createDownscaledCell(sourceCell, factors, maxNumEntries)
				val bytes = ByteArray(LabelMultisetTypeDownscaler.getSerializedVolatileLabelMultisetArraySize(cell))
				LabelMultisetTypeDownscaler.serializeVolatileLabelMultisetArray(cell, bytes)
				writer.writeBlock(targetPath, targetAttributes, ByteArrayDataBlock(cellDimensions, gridPosition.clone(), bytes))
			}
			writer.setAttribute(targetPath, N5Helpers.MAX_NUM_ENTRIES_KEY, maxNumEntries)
		}
		val level1 = openLabelMultiset(writer, "$dataset/data/s1")
		var counted = 0L
		val entryRef = LabelMultisetEntry()
		Views.flatIterable(level1).forEach { multiset: LabelMultisetType ->
			for (entry in multiset.entrySetWithRef(entryRef))
				counted += entry.count
		}
		return Intervals.numElements(openLabelMultiset(writer, "$dataset/data/s0")) - counted
	}

	@Test
	fun `unbounded multiset counts are exact`() = assertCountsMatch(openState())

	@Test
	fun `unbounded multiset ids are exact`() = assertIdsMatch(openState())

	@Test
	fun `unique labels for a region are exact on a multiset`() {
		val state = openState()
		assertIdsMatch(state, UniqueLabelsForRegion(state))
	}

	@Test
	fun `a limited multiset without counts uses unique labels for the region`() {
		limitEntries(1)
		val state = openState()
		assertFalse(UniqueLabelsForRegion.from(state, countLabelsSize = false) is MultisetCountsForRegion)
		assertTrue(UniqueLabelsForRegion.from(state, countLabelsSize = true) is MultisetCountsForRegion)
		assertIdsMatch(state, UniqueLabelsForRegion(state))
	}

	@Test
	fun `an unbounded multiset is reported as such`() {
		val state = openState()
		assertTrue(PainteraDataLevels(state).maxNumEntriesUnbounded)
		val finder = UniqueLabelsForRegion.from(state, countLabelsSize = true)
		assertTrue(finder is MultisetCountsForRegion)
	}

	@Test
	fun `limited multiset counts stay exact by splitting lossy blocks`() {
		val lost = limitEntries(1)
		assertTrue(lost > 0, "limiting the entries did not lose any counts, so this does not test the split")
		val state = openState()
		assertFalse(PainteraDataLevels(state).maxNumEntriesUnbounded)
		assertCountsMatch(state)
	}

	@Test
	fun `limited multiset ids stay exact by splitting lossy blocks`() {
		limitEntries(1)
		assertIdsMatch(openState())
	}
}
