package org.janelia.saalfeldlab.paintera.state.label.feature.blockwise

import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import net.imglib2.Interval
import net.imglib2.cache.Invalidate
import org.janelia.saalfeldlab.util.HashWrapper
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import kotlin.math.max
import kotlin.math.min

interface LabelBlockCache {

    data class Key(
        val level: Int,
        val t: Int,
        val block: HashWrapper<Interval>) {

        constructor(
            level: Int,
            t: Int,
            block: Interval): this(level, t, HashWrapper.interval(block))
    }

    operator fun get(key: Key): TLongSet

    operator fun get(level: Int, t: Int, block: Interval): TLongSet = get(Key(level, t, block))

    operator fun get(level: Int, t: Int, vararg blocks: Interval): TLongSet {
        return blocks
            .map { get(level, t, it) }
            .fold(TLongHashSet()) { s1, s2 -> s1.addAll(s2); s1 }
    }

    fun getBlockSize(level: Int, t: Int): IntArray? = null


    companion object {
        fun LabelBlockCache.getAllLabelsIn(
            level: Int,
            t: Int,
            vararg blocks: Interval,
            executors: ExecutorService? = null,
            numTasks: Int? = null): TLongSet {
            return executors?.let { es ->
                val taskSize = max(blocks.size / ( numTasks ?: Runtime.getRuntime().availableProcessors()), 1)
                (blocks.indices step taskSize)
                    .map { lower ->
                        es.submit(Callable { get(level, t, *blocks.toList().subList(lower, min(lower + taskSize, blocks.size)).toTypedArray()) })
                    }
                    .map { it.get() }
                    .fold(TLongHashSet()) { s1, s2 -> s1.addAll(s2); s1 }
            } ?: get(level, t, *blocks)
        }
    }

    interface WithInvalidate : LabelBlockCache, Invalidate<Key>

}
