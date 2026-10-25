package org.janelia.saalfeldlab.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.NativeType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.scale

/**
 * One scale level of a multiscale image.
 *
 * @param index the position of this level in the pyramid, relative to the highest resolution level
 * @param scaleFactors the integer downsampling factors, per dimension, relative to the highest resolution level
 */
class MultiscaleLevel<T>(
    val index: Int,
    val image: RandomAccessibleInterval<T>,
    val grid: CellGrid,
    val scaleFactors: LongArray,
) {

    companion object {
        fun <T : NativeType<T>> of(
            index: Int,
            image: AbstractCellImg<T, *, *, *>,
            scaleFactors: LongArray
        ) = MultiscaleLevel(index, image, image.cellGrid, scaleFactors)
    }
}

/**
 * A block of an [AdaptiveScaleBlocks] region.
 *
 * @param interval target interval at level
 * @param firstLevelInterval target interval at highest resolution level
 * @param isPartial if the block is only partly contained in the target interval
 */
class MultiscaleBlock<T>(
    val level: MultiscaleLevel<T>,
    val gridPosition: LongArray,
    val interval: Interval,
    val firstLevelInterval: Interval,
    val isPartial: Boolean,
) {
    val data: RandomAccessibleInterval<T> by lazy { level.image.interval(interval) }

    val size: Long get() = Intervals.numElements(interval)

    val firstLevelSize: Long get() = Intervals.numElements(firstLevelInterval)
}

/**
 * Visit an interval over a multiscale image using the minimal set of blocks that tile it.
 *
 * Every pixel is visited via the lowest resolution block that is fully contained in the target region.
 * If at the highest resolution level, no block is fully covered by the target region, then intersect
 * the image at the highest level, and iterate over the intersection.
 *
 */
class AdaptiveScaleBlocks<T>(
    val levels: List<MultiscaleLevel<T>>,
    val fullInterval: Interval,
) {

    init {
        validate()
    }

    /**
     * Return the smallest set of blocks that cover [interval]. Where a lower resolution block fully
     * covers the interval, that lowest level block will be returned. Partial blocks will be returned
     * on edges at the highest resolution level
     *
     * @param interval the target interval at the highest resolution level
     * @return the multiscale block<t>s that cover [interval]
     */
    fun blocksFor(interval: Interval): Sequence<MultiscaleBlock<T>> {
        val intersection = interval intersect fullInterval
        intersection.takeUnless { it.isEmpty() } ?: return emptySequence()

        return discover(levels.lastIndex, intersection)
    }

    /**
     * Run [action] on the blocks of [interval] in parallel on the caller's dispatcher; returns once all are done
     *
     * @param interval the first level interval
     * @param action
     * @receiver
     */
    suspend fun forEachBlock(interval: Interval, action: suspend (MultiscaleBlock<T>) -> Unit) = coroutineScope {
        for (block in blocksFor(interval)) {
            ensureActive()
            launch { action(block) }
        }
    }

    /** Require that [levels] covers [fullInterval] */
    private fun validate() {
        require(levels.isNotEmpty()) { "No scale levels" }
        require(levels[0].scaleFactors.all { it == 1L }) { "Level 0 is s0; its factors have to be 1" }
        levels.forEachIndexed { levelIndex, level ->
            for (axis in 0 until fullInterval.numDimensions()) {
                require(level.scaleFactors[axis] >= 1L) { "Level $levelIndex has a factor of ${level.scaleFactors[axis]} on axis $axis" }
                require(level.image.dimension(axis) * level.scaleFactors[axis] >= fullInterval.dimension(axis)) { "Level $levelIndex does not cover s0 on axis $axis" }
            }
        }
    }

    /**
     * Recursive call to yield the minimum sequence of blocks, from lowest resolution to highest,
     * that fully covers the [interval] at the lowest resolution level at each position.
     *
     * @param levelIndex to yield blocks from if they are fully contained
     * @param interval the interval we want blocks for
     * @return sequence of adaptive blocks, of varying scale levels.
     */
    private fun discover(levelIndex: Int, interval: Interval): Sequence<MultiscaleBlock<T>> = sequence {
        val level = levels[levelIndex]
        val numDimensions = interval.numDimensions()
        val scaleFactors = level.scaleFactors.map { it.toInt() }.toIntArray()

        val blockGridInterval = let {
            val min = LongArray(numDimensions) { interval.min(it) / scaleFactors[it] }.also {
                level.grid.getCellPosition(it, it)
            }
            val max = LongArray(numDimensions) { interval.max(it) / scaleFactors[it] }.also {
                level.grid.getCellPosition(it, it)
            }
            FinalInterval(min, max)
        }

        val blockCursor = level.grid.cellIntervals()
            .interval(blockGridInterval)
            .localizingCursor()

        for (blockInterval in blockCursor) {
            val gridPosition = blockCursor.positionAsLongArray()
            val blockAtCurrentLevel = FinalInterval(blockInterval)
            val blockAtFirstLevel = blockAtCurrentLevel.scale(*scaleFactors, scaleMin = true) intersect fullInterval
            when {
                Intervals.contains(interval, blockAtFirstLevel) -> {
                    yield(MultiscaleBlock(level, gridPosition, blockAtCurrentLevel, blockAtFirstLevel, false))
                }

                levelIndex == 0 -> {
                    val intersect = Intervals.intersect(blockAtCurrentLevel, interval)
                    yield(MultiscaleBlock(level, gridPosition, intersect, intersect, true))
                }

                else -> yieldAll(discover(levelIndex - 1, Intervals.intersect(blockAtFirstLevel, interval)))
            }
        }
    }
}
