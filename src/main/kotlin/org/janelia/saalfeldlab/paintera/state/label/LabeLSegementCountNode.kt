package org.janelia.saalfeldlab.paintera.state.label

import gnu.trove.map.TLongLongMap
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
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.numeric.IntegerType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.state.label.feature.count.FragmentCount
import org.janelia.saalfeldlab.paintera.state.label.feature.count.SegmentVoxelCount
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import java.util.concurrent.ExecutorService

class LabelSegementCountNode(
    private val source: DataSource<IntegerType<*>, *>,
    private val assignment: FragmentSegmentAssignment,
    private val labelBlockLookup: LabelBlockLookup,
    private val es: ExecutorService) {

    private val fragmentCount = FragmentCount(source)
    private var segmentCounts: TLongLongMap? = null

    companion object {
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
                        println("Refreshing segment counts!")
                        refresh()
                        println("Finding n smallest counts: ${this.segmentCounts}")
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
                            labelBlockLookup
                        )?.let { (id, pos) ->
                            val doublePos = pos.map { it.toDouble() }.toDoubleArray()
                            println("LOL WAS $id ${pos.toList()} ${doublePos.toList()}")
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

    private fun refresh() {
        fragmentCount.refreshCounts(es, source.getDataSource(0, 0).getBlockSize() ?: IntArray(3) { 64 })
        segmentCounts = fragmentCount.countStore?.let { SegmentVoxelCount.getSegmentCounts(it, assignment) }
    }

}
