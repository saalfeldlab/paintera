package org.janelia.saalfeldlab.paintera.state.label.feature.count

import gnu.trove.map.TLongLongMap
import gnu.trove.map.hash.TLongLongHashMap
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.state.label.feature.IntegerValuedObjectFeature
import org.janelia.saalfeldlab.paintera.state.label.feature.ObjectFeature

data class SegmentVoxelCount(
    override val id: Long,
    val count: Long): IntegerValuedObjectFeature, Comparable<SegmentVoxelCount> {

    override val integerValue: Long
        get() = count

    override fun compareTo(other: SegmentVoxelCount) = integerValue.compareTo(other.integerValue)

    companion object {

        private fun TLongLongMap.asIterableFeatures(): Iterable<SegmentVoxelCount> {
            val m = this
            return Iterable {
                object: Iterator<SegmentVoxelCount> {

                    private val iterator = m.iterator()

                    override fun hasNext() = iterator.hasNext()

                    override fun next(): SegmentVoxelCount {
                        iterator.advance()
                        return SegmentVoxelCount(iterator.key(), iterator.value())
                    }

                }
            }
        }
        @JvmStatic
        fun getSegmentCounts(
            fragmentCounts: TLongLongMap,
            assignment: FragmentSegmentAssignment): TLongLongMap = TLongLongHashMap().also { m ->
            fragmentCounts.forEachEntry { k, v ->
                val segment = assignment.getSegment(k)
                m.put(segment, m[segment] + v)
                true
            }
        }

        @JvmStatic
        fun nSmallest(counts: TLongLongMap, n: Int) = ObjectFeature.nBest(counts.asIterableFeatures(), n)

    }


}

fun main(vararg args: String) {
    val map = TLongLongHashMap()
    map.put(1, 3)
    map.put(10, 2)
    map.put(5, 4)
    map.put(6, 1)
    map.put(0, 1000)
    val threeSmallest = SegmentVoxelCount.nSmallest(map, 4)
    println(threeSmallest)
}
