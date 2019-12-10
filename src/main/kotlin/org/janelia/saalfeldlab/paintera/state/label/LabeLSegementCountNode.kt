package org.janelia.saalfeldlab.paintera.state.label

import gnu.trove.map.TLongLongMap
import gnu.trove.set.TLongSet
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ButtonType
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
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
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.state.label.feature.blockwise.LabelBlockCache
import org.janelia.saalfeldlab.paintera.state.label.feature.count.ObjectVoxelCount
import org.janelia.saalfeldlab.paintera.state.label.feature.count.SegmentVoxelCount
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.ExecutorService
import java.util.function.Consumer

class LabelSegementCountNode(
    private val source: DataSource<IntegerType<*>, *>,
    private val selectedIds: SelectedIds,
    private val assignment: FragmentSegmentAssignment,
    private val labelBlockLookup: LabelBlockLookup,
    private val labelBlockCache: LabelBlockCache.WithInvalidate,
    private val centerViewerAt: Consumer<DoubleArray>,
    private val es: ExecutorService) {

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
    }

    val node: Node
        get() {
            val segmentCounts = FXCollections.observableArrayList<SegmentVoxelCount>()
            val bestNButton = NumericSliderWithField(1, 1000, 10)
            val refreshButton = Buttons.withTooltip(
                "_Refresh",
                "Refresh fragment and segment counts",
                EventHandler {
                    segmentCounts.clear()
                    Thread {
                        refresh()
                        this.segmentCounts?.let {
                            val smallestSegments = SegmentVoxelCount.nSmallest(it, bestNButton.slider.value.toInt())
                            Platform.runLater { segmentCounts.setAll(smallestSegments) }
                        }
                    }
                        .also { it.isDaemon = true }
                        .start()
                })

            val controls = HBox(
                refreshButton,
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                bestNButton.slider.also { HBox.setHgrow(it, Priority.ALWAYS) },
                bestNButton.textField.also { it.alignment = Pos.CENTER_RIGHT })

            val countTable = TableView(segmentCounts)
            val segmentIdColumn = TableColumn<SegmentVoxelCount, Long>("Segment Id")
                .also { it.setCellValueFactory(PropertyValueFactory("id")) }
            val voxelCountColumn = TableColumn<SegmentVoxelCount, Long>("Voxel Count")
                .also { it.setCellValueFactory(PropertyValueFactory("count")) }
            countTable.columns.setAll(segmentIdColumn, voxelCountColumn)
            countTable.setRowFactory { tv ->
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

            return VBox(controls, countTable)
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
