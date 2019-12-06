package org.janelia.saalfeldlab.paintera.state.label.feature.count

import gnu.trove.map.TLongLongMap
import gnu.trove.map.hash.TLongLongHashMap
import net.imglib2.algorithm.util.Grids
import net.imglib2.type.numeric.IntegerType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.data.DataSource
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

class FragmentCount(
    private val source: DataSource<IntegerType<*>, *>,
    private val blockSize: IntArray) {

    var countStore: TLongLongMap? = null
        private set

    fun refreshCounts(es: ExecutorService) {
        val source = this.source.getDataSource(0, 0)
        val nDim = source.numDimensions()
        val blocks = Grids.collectAllContainedIntervals(
            LongArray(nDim) { source.min(it) },
            LongArray(nDim) { source.max(it) },
            blockSize)
        val futures = blocks.map { block ->
            es.submit(Callable {
                val countStore = TLongLongHashMap()
                Views.interval(source, block).forEach { vx ->
                    val value = vx.getIntegerLong()
                    countStore.put(value, countStore[value] + 1)
                }
                countStore
            })
        }

        val countStore = TLongLongHashMap()
        futures.map { it.get() }.forEach {
            it.forEachEntry { k, v ->
                countStore.put(k, countStore[k] + v)
                true
            }
        }

        synchronized(this) {
            this.countStore = countStore
        }
    }


}
