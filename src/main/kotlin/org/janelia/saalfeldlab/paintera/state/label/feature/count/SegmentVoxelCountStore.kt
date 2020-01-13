package org.janelia.saalfeldlab.paintera.state.label.feature.count

import gnu.trove.map.TLongLongMap
import gnu.trove.set.TLongSet
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.numeric.IntegerType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.state.label.feature.blockwise.LabelBlockCache
import org.janelia.saalfeldlab.util.NamedThreadFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier

class SegmentVoxelCountStore(
    private val source: DataSource<IntegerType<*>, *>,
    private val assignment: FragmentSegmentAssignment,
    private val labelBlockLookup: LabelBlockLookup,
    private val labelBlockCache: LabelBlockCache.WithInvalidate,
    private val es: ExecutorService) {

    private val singleThreadExecutor = SingleTaskExecutor(NamedThreadFactory("segment-voxel-count-store-%d", true))

    private val fragmentCounts = ObjectVoxelCount.BlockwiseStore(
        source,
        labelBlockLookup,
        SoftRefLoaderCache(),
        SoftRefLoaderCache(),
        0)

    var segmentCounts: TLongLongMap? = null
        private set
    private var allLabels: TLongSet? = null

    @JvmOverloads
    fun refresh(
        invalidateLabelBlockCache: Boolean = false,
        invalidateCountsPerBlock: Boolean = false) {

        if (invalidateCountsPerBlock)
            fragmentCounts.countsPerBlock.invalidateAll()

        if (invalidateLabelBlockCache || allLabels == null) {
            labelBlockCache.invalidateAll()
            val blocks = source
                .getDataSource(0, 0)
                .let { Grids.collectAllContainedIntervals(Intervals.dimensionsAsLongArray(it), labelBlockCache.getBlockSize(0, 0) ?: it.getBlockSize()) }
            allLabels = with (LabelBlockCache) { labelBlockCache.getAllLabelsIn(0, 0, *blocks.toTypedArray(), executors = es) }
        }

        fragmentCounts.countsPerObject.invalidateAll()
        with (ObjectVoxelCount.BlockwiseStore) {
            val counts = fragmentCounts.countsForAllObjects(*allLabels!!.toArray(), executors = es)
            segmentCounts = SegmentVoxelCount.getSegmentCounts(counts, assignment)
        }
    }

    companion object {

        private fun CellGrid.getCellDimensions() = IntArray(numDimensions()) { cellDimension(it) }

        private fun RandomAccessibleInterval<*>.getBlockSize() = when(this) {
            is AbstractCellImg<*, *, *, *> -> getCellGrid().getCellDimensions()
            else -> null
        }
    }

}

private class SingleTaskExecutor(factory: ThreadFactory = NamedThreadFactory("single-task-executor", true)) {

    interface Task<T>: Callable<T> {
        fun cancel()

        val isCanceled: Boolean
    }

    private class TaskDelegate<T>(private val actualTask: (BooleanSupplier) -> T): Task<T> {

        constructor(actualTask: Callable<T>): this({ actualTask.call() })

        override var isCanceled: Boolean = false
            private set

        override fun cancel() {
            isCanceled = true
        }

        private val isCanceledSupplier: BooleanSupplier = BooleanSupplier { isCanceled }

        override fun call(): T = actualTask(isCanceledSupplier)
    }

    private val es = Executors.newSingleThreadExecutor(factory)

    private var taskRef = AtomicReference<Task<*>?>(null)

    var currentTask: Task<*>? = null

    fun submit(task: Task<*>): Future<Any?> {
        taskRef.set(task)
        return es.submit(Callable {
            synchronized(taskRef) {
                taskRef.getAndSet(null)?.also {
                    currentTask = it
                }
            }?.call()
        })
    }

    fun cancelCurrentTask() = currentTask?.cancel()

    companion object {
        fun <T> asTask(actualTask: (BooleanSupplier) -> T) = TaskDelegate(actualTask)
    }



}
