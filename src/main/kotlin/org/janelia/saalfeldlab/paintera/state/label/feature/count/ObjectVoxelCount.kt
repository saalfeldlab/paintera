package org.janelia.saalfeldlab.paintera.state.label.feature.count

import gnu.trove.map.TLongLongMap
import gnu.trove.map.hash.TLongLongHashMap
import net.imglib2.Interval
import net.imglib2.cache.Cache
import net.imglib2.cache.LoaderCache
import net.imglib2.type.numeric.IntegerType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.state.label.feature.IntegerValuedObjectFeature
import org.janelia.saalfeldlab.util.HashWrapper
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import kotlin.math.max
import kotlin.math.min

typealias Block = HashWrapper<Interval>

data class ObjectVoxelCount(
    override val id: Long,
    val count: Long): IntegerValuedObjectFeature, Comparable<SegmentVoxelCount> {

    override val integerValue: Long
        get() = count

    override fun compareTo(other: SegmentVoxelCount) = integerValue.compareTo(other.integerValue)

    class BlockwiseStore(
        private val source: DataSource<IntegerType<*>, *>,
        private val labelBlockLookup: LabelBlockLookup,
        countsPerBlock: LoaderCache<Block, TLongLongMap>,
        countsPerObject: LoaderCache<Long, Long>,
        private val level: Int = 0) {

        val countsPerBlock = countsPerBlock.withLoader { block ->
            Views
                .interval(source.getDataSource(0, level), block.data)
                .fold(TLongLongHashMap()) { s, iv -> val i = iv.getIntegerLong(); s.put(i, s[i] + 1); s }
        }

        val countsPerObject = countsPerObject.withLoader { objectId ->
            labelBlockLookup
                .read(LabelBlockLookupKey(level, objectId))
                .map { this.countsPerBlock[Block.interval(it)] }
                .filter { it.containsKey(objectId) }
                .map { it[objectId] }
                .toTypedArray()
                .sum()
        }

        companion object {
            fun BlockwiseStore.countsForAllObjects(
                vararg objectIds: Long,
                executors: ExecutorService? = null,
                numTasks: Int? = null): TLongLongMap {
                return executors?.let { es ->
                    val taskSize = max(objectIds.size / (numTasks ?: Runtime.getRuntime().availableProcessors()), 1)
                    (objectIds.indices step taskSize)
                        .map { lower ->
                            es.submit(Callable {
                                countsPerObject.get(
                                    *objectIds.toList().subList(
                                        lower,
                                        min(lower + taskSize, objectIds.size)
                                    ).toLongArray()
                                )
                            })
                        }
                        .map { it.get() }
                        .fold(TLongLongHashMap()) { m1, m2 -> m1.putAll(m2); m1 }
                } ?: countsPerObject.get(*objectIds)
            }

            private operator fun Cache<Long, Long>.get(vararg ids: Long) = ids.fold(TLongLongHashMap()) { m, id ->
                m.also { it.put(id, get(id)) }
            }
        }
    }

}
