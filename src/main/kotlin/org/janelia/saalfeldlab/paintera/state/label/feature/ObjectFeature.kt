package org.janelia.saalfeldlab.paintera.state.label.feature

import com.google.common.collect.MinMaxPriorityQueue
import gnu.trove.map.TLongLongMap
import java.util.Comparator

interface ObjectFeature<F> {
    val id: Long
    val value: F

    companion object {

        @JvmStatic
        fun <F, O> nBest(featureStore: Iterable<O>, n: Int) where O: ObjectFeature<F>, O: Comparable<O> = nBest(
            featureStore,
            n,
            Comparator.naturalOrder<O>())

        @JvmStatic
        fun <F, O> nBest(
            featureStore: Iterable<O>,
            n: Int, comparator: Comparator<O>): List<O>
            where O: ObjectFeature<F>, O: Comparable<O> {
            val queue = MinMaxPriorityQueue
                .orderedBy(comparator)
                .maximumSize(n)
                .expectedSize(n)
                .create<O>()
            featureStore.forEach { queue.add(it) }
            val list = mutableListOf<O>()
            while (queue.isNotEmpty())
                list.add(queue.pollFirst())
            return list
        }
    }

}

interface NumericObjectFeature : ObjectFeature<Number>

interface RealValuedObjectFeature : NumericObjectFeature {
    val realValue: Double

    override val value: Double
        get() = realValue
}

interface IntegerValuedObjectFeature : NumericObjectFeature {
    val integerValue: Long

    override val value: Long
        get() = integerValue

}
