package org.janelia.saalfeldlab.paintera.state.label.feature.count

import gnu.trove.map.hash.TLongLongHashMap
import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class SegmentVoxelCountTest {

    @Test
    fun testNBest() {
        val map = TLongLongHashMap()
        map.put(1, 3)
        map.put(9, 2)
        map.put(5, 4)
        map.put(6, 1)
        map.put(0, 1000)

        val smallestToLargest = listOf(
            SegmentVoxelCount(6, map[6]),
            SegmentVoxelCount(9, map[9]),
            SegmentVoxelCount(1, map[1]),
            SegmentVoxelCount(5, map[5]),
            SegmentVoxelCount(0, map[0]))

        for (size in 1 until map.size()) {
            val expected = smallestToLargest.subList(0, size)
            val actual = SegmentVoxelCount.nSmallest(map, size)
            LOG.debug("Comparing for size {}: expected={}, actual={}", size, expected, actual)
            Assert.assertEquals(
                expected,
                actual)
        }

    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }

}
