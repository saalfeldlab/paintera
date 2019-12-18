package org.janelia.saalfeldlab.paintera.state.label

import gnu.trove.map.TLongLongMap
import gnu.trove.set.TLongSet
import javafx.application.Platform
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleLongProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.ButtonType
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.IntegerType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService.max
import org.janelia.saalfeldlab.paintera.state.label.feature.blockwise.LabelBlockCache
import org.janelia.saalfeldlab.paintera.state.label.feature.count.ObjectVoxelCount
import org.janelia.saalfeldlab.paintera.state.label.feature.count.SegmentVoxelCount
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import kotlin.math.min

class LabelSegementCountNode(
    private val source: DataSource<IntegerType<*>, *>,
    private val selectedIds: SelectedIds,
    private val assignment: FragmentSegmentAssignment,
    private val labelBlockLookup: LabelBlockLookup,
    private val labelBlockCache: LabelBlockCache.WithInvalidate,
    private val centerViewerAt: Consumer<DoubleArray>,
    private val es: ExecutorService) {

//    private val singleThreadExecutor = Executors.newSingleThreadExecutor(NamedThreadFactory("label-segment-count-node-%d", true))

    private val singleThreadExecutor = SingleTaskExecutor(NamedThreadFactory("label-segment-count-node-%d", true))

    private val fragmentCounts = ObjectVoxelCount.BlockwiseStore(
        source,
        labelBlockLookup,
        SoftRefLoaderCache(),
        SoftRefLoaderCache(),
        0)
    private var segmentCounts: TLongLongMap? = null
    private var allLabels: TLongSet? = null

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun RandomAccessibleInterval<*>.getBlockSize() = when(this) {
            is AbstractCellImg<*, *, *, *> -> getCellGrid().getCellDimensions()
            else -> null
        }

        private fun CellGrid.getCellDimensions() = IntArray(numDimensions()) { cellDimension(it) }

        private fun ObservableList<SegmentVoxelCount>.setAllFiltered(
            counts: List<SegmentVoxelCount>,
            minInclusive: Long,
            maxInclusive: Long) = this.setAllFiltered(counts) { it.count >= minInclusive && it.count <= maxInclusive }

        private inline fun <T> ObservableList<T>.setAllFiltered(list: List<T>, filter: (T) -> Boolean) = this.setAll(list.filter(filter))

        private operator fun Long.compareTo(other: LongProperty) = compareTo(other.value)

        private operator fun LongProperty.compareTo(other: Long) = value.compareTo(other)

        private operator fun Number.compareTo(other: Long) = toLong().compareTo(other)

        private operator fun LongProperty.compareTo(other: LongProperty) = compareTo(other.value)

        private operator fun Number.compareTo(other: LongProperty) = compareTo(other.value)



    }

    val node: Node
        get() {
            val segmentCounts = FXCollections.observableArrayList<SegmentVoxelCount>()
            val minMin = SimpleLongProperty(1)
            val maxMax = SimpleLongProperty(Long.MAX_VALUE)
            val minSize = SimpleLongProperty(minMin.value)
            val maxSize = SimpleLongProperty(maxMax.value)

            val filteredSegmentCounts = FXCollections.observableArrayList<SegmentVoxelCount>()
            segmentCounts.addListener( ListChangeListener { c -> Platform.runLater { filteredSegmentCounts.setAllFiltered(c.list, minSize.value, maxSize.value) } } )
            minSize.addListener { _, _, new ->
                if (new.toLong() < minMin)
                    minSize.value = minMin.value
                else if (new > maxMax)
                    minSize.value = maxMax.value
                else if (new > maxSize.value)
                    maxSize.value = new.toLong()
                else if (!segmentCounts.isEmpty())
                    Platform.runLater { filteredSegmentCounts.setAllFiltered(segmentCounts, minSize.value, maxSize.value) }
            }
            maxSize.addListener { _, _, new ->
                if (new.toLong() < minMin)
                    maxSize.value = minMin.value
                else if (new.toLong() > maxMax)
                    maxSize.value = maxMax.value
                else if (new < minSize.value)
                    minSize.value = new.toLong()
                else if (!segmentCounts.isEmpty())
                    Platform.runLater { filteredSegmentCounts.setAllFiltered(segmentCounts, minSize.value, maxSize.value) }
            }

            val minSlider = NumericSliderWithField(minMin.value, maxMax.value, minMin.value)
            val maxSlider = NumericSliderWithField(minMin.value, maxMax.value, maxMax.value)

            minSlider.slider.minProperty().bind(minMin)
            minSlider.slider.maxProperty().bind(maxMax)
            maxSlider.slider.minProperty().bind(minMin)
            maxSlider.slider.maxProperty().bind(maxMax)
            minSlider.slider.valueProperty().bindBidirectional(minSize)
            maxSlider.slider.valueProperty().bindBidirectional(maxSize)
            minSlider.slider.isShowTickMarks = false
            maxSlider.slider.isShowTickMarks = false
            minSlider.slider.isShowTickLabels = false
            maxSlider.slider.isShowTickLabels = false

            val refreshButton = Buttons.withTooltip(
                "_Refresh",
                "Refresh fragment and segment counts",
                EventHandler {
                    val task = SingleTaskExecutor.asTask { isCanceled: BooleanSupplier ->
                        refresh()
                        this.segmentCounts?.takeIf { !it.isEmpty }?.let {
                            var mm = Long.MAX_VALUE
                            var MM = Long.MIN_VALUE
                            val counts = mutableListOf<SegmentVoxelCount>()
                            it.forEachEntry { id, count ->
                                if (count < mm) mm = count
                                if (count > MM) MM = count
                                counts.add(SegmentVoxelCount(id, count))
                                !isCanceled.asBoolean
                            }
                            minMin.value = mm
                            maxMax.value = MM
                            // TODO this may be expensive when sorting large lists,
                            // TODO maybe only sort the filtered result (on request)
                            counts.sort()
                            synchronized(segmentCounts) {
                                if (!isCanceled.asBoolean) {
                                    segmentCounts.clear()
                                    maxSize.value = min(maxMax.value, maxSize.value)
                                    minSize.value = max(minMin.value, minSize.value)
                                    segmentCounts.setAll(counts)
                                }
                            }
                        } ?: synchronized(segmentCounts) { segmentCounts.takeUnless { isCanceled.asBoolean }?.clear() }
                    }
                    singleThreadExecutor.submit(task)
                })

            val cancelButton = Buttons.withTooltip("_Cancel", "Cancel refreshing counts") { _: ActionEvent ->
                singleThreadExecutor.cancelCurrent()
            }

            val buttons = HBox(refreshButton, cancelButton)
            HBox.setHgrow(refreshButton, Priority.ALWAYS)
            HBox.setHgrow(cancelButton, Priority.ALWAYS)

            val controls = GridPane()
            controls.add(Labels.withTooltip("Min", "Minimum segment size (inclusive)"), 0, 1)
            controls.add(Labels.withTooltip("Max", "Maximum segment size (inclusive)"), 0, 2)
            controls.add(minSlider.textField, 1, 1)
            controls.add(maxSlider.textField, 1, 2)
            controls.add(minSlider.slider, 2, 1)
            controls.add(maxSlider.slider, 2, 2)
            GridPane.setHgrow(minSlider.slider, Priority.ALWAYS)
            GridPane.setHgrow(maxSlider.slider, Priority.ALWAYS)

            // for some reason, TableView does not get sorted when new data is added
            // https://bugs.openjdk.java.net/browse/JDK-8092759
            val countTable = TableView(filteredSegmentCounts)
            countTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
            val segmentIdColumn = TableColumn<SegmentVoxelCount, Long>("Segment Id")
                .also { it.setCellValueFactory(PropertyValueFactory("id")) }
            val voxelCountColumn = TableColumn<SegmentVoxelCount, Long>("Voxel Count")
                .also { it.setCellValueFactory(PropertyValueFactory("count")) }
                .also { it.sortType = TableColumn.SortType.ASCENDING }
            countTable.columns.setAll(segmentIdColumn, voxelCountColumn)
            countTable.sortOrder.setAll(voxelCountColumn)
            countTable.setRowFactory {
                val row = TableRow<SegmentVoxelCount>()
                row.setOnMouseClicked { ev ->
                    if (ev.clickCount == 2 && (! row.isEmpty) ) {
                        ev.consume()
                        askNavigateToSegment(
                            row.item.id,
                            source,
                            assignment,
                            labelBlockLookup)?.let { (id, pos) ->
                            val doublePos = pos.map { it.toDouble() }.toDoubleArray()
                            AffineTransform3D()
                                .also { source.getSourceTransform(0, 0, it) }
                                .let { it.apply(doublePos, doublePos) }
                            LOG.info("Centering viewer at position {} ({}) and selecting id {}", doublePos, pos, id)
                            centerViewerAt.accept(doublePos)
                            selectedIds.activateAlso(id)
                        }
                    }
                }
                row
            }

            return VBox(buttons, controls, countTable)
        }

    private fun askNavigateToSegment(
        segmentId: Long,
        source: DataSource<IntegerType<*>, *>,
        assignment: FragmentSegmentAssignment,
        labelBlockLookup: LabelBlockLookup): Pair<Long, LongArray>? {
        val dialog = PainteraAlerts.confirmation("_Select and navigate", "_Cancel", true)

        if (dialog.showAndWait().filter { ButtonType.OK == it }.isPresent) {
            val fragments = assignment.getFragments(segmentId).toArray()
            val blocks = fragments.flatMap { labelBlockLookup.read(LabelBlockLookupKey(0, it)).toList() }
            for (block in blocks) {
                val cursor = Views.interval(source.getDataSource(0, 0), block).cursor()
                while (cursor.hasNext()) {
                    val fragmentId = cursor.next().getIntegerLong()
                    if (assignment.getSegment(fragmentId) == segmentId)
                        return Pair(fragmentId, LongArray(cursor.numDimensions()) { cursor.getLongPosition(it) })
                }
            }
        }

        return null
    }

    private fun refresh(
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

    private val taskRef = AtomicReference<Task<*>?>(null)

    fun <T> submit(task: Task<T>): Future<T?> {
        taskRef.set(task)
        return es.submit(Callable {
            synchronized(taskRef) {
                if (taskRef.getAndSet(null) == null)
                    return@Callable null
            }
            task.call()
        })
    }

    fun cancelCurrent() = taskRef.getAndSet(null)?.cancel()

    companion object {
        fun <T> asTask(actualTask: (BooleanSupplier) -> T) = TaskDelegate(actualTask)
    }



}
