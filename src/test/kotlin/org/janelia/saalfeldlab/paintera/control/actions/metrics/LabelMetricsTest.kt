package org.janelia.saalfeldlab.paintera.control.actions.metrics

import gnu.trove.map.hash.TLongLongHashMap
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.metrics.labels.LabelMetrics
import org.janelia.saalfeldlab.paintera.metrics.labels.SortBy
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.util.function.Supplier
import kotlin.test.assertEquals

class LabelMetricsTest {

	private fun metrics() = LabelMetrics(
        FragmentSegmentAssignmentOnlyLocal({ TLongLongHashMap() }, { _, _ -> }),
        { false },
    )

	private fun TLongLongHashMap.with(vararg entries: Pair<Long, Long>) = apply { entries.forEach { (id, count) -> put(id, count) } }

	@Test
	fun `background is left out of the summary and the rows`() {
		val metrics = metrics()
		metrics.addCounts(TLongLongHashMap().with(0L to 90L, 5L to 6L, 7L to 4L))

		assertEquals(LabelMetrics.Snapshot(segments = 2, fragments = 2, lockedSegments = 0, totalCount = 10L), metrics.snapshot)
		val csv = StringWriter().also { metrics.writeCsv(it, segmentColumn = false, fragmentColumn = true, lockedColumn = false, countColumn = true) }
		assertEquals("Fragment ID,Voxel Count\n5,6\n7,4\n", csv.toString())
	}

	@Test
	fun `rows are sorted by the chosen column`() {
		val metrics = metrics()
		metrics.addCounts(TLongLongHashMap().with(5L to 6L, 7L to 40L, 9L to 6L))

		fun csv(sortBy: SortBy) = StringWriter().also {
			metrics.writeCsv(it, segmentColumn = false, fragmentColumn = true, lockedColumn = false, countColumn = true, sortBy = sortBy)
		}.toString()

		assertEquals("Fragment ID,Voxel Count\n5,6\n7,40\n9,6\n", csv(SortBy.FRAGMENT))
		assertEquals("Fragment ID,Voxel Count\n7,40\n5,6\n9,6\n", csv(SortBy.COUNT))
	}
}
