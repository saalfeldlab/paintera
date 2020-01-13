package org.janelia.saalfeldlab.paintera.state.label

import javafx.application.Platform
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.IntegerType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService.max
import org.janelia.saalfeldlab.paintera.state.label.feature.count.SegmentVoxelCount
import org.janelia.saalfeldlab.paintera.state.label.feature.count.SegmentVoxelCountStore
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.SingleTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import kotlin.math.min

class LabelSegementCountNode(
    private val segmentVoxelCountStore: SegmentVoxelCountStore,
    private val source: DataSource<IntegerType<*>, *>,
    private val selectedIds: SelectedIds,
    private val assignment: FragmentSegmentAssignment,
    private val labelBlockLookup: LabelBlockLookup,
    private val centerViewerAt: Consumer<DoubleArray>) {

    private val singleThreadExecutor = SingleTaskExecutor(NamedThreadFactory("label-segment-count-node-%d", true))

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

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

        private fun Node.setIsVisibleAndManaged(isVisibleAndManaged: Boolean) {
            isVisible = isVisibleAndManaged
            isManaged = isVisibleAndManaged
        }

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


            val cancelButton = Buttons.withTooltip("_Cancel", "Cancel refreshing counts") { _: ActionEvent ->
                singleThreadExecutor.cancelCurrentTask()
            }

            val refreshButtonGraphic = SimpleObjectProperty<ProgressIndicator?>(null)
            val isRefreshButtonDisable = SimpleBooleanProperty(false)

            val refreshButton = Buttons.withTooltip(
                "_Refresh",
                "Refresh fragment and segment counts",
                EventHandler {
                    val task = SingleTaskExecutor.asTask { isCanceled: BooleanSupplier ->
                        try {
                            Platform.runLater {
                                cancelButton.isDisable = false
                                isRefreshButtonDisable.value = true
                                refreshButtonGraphic.value = ProgressIndicator(-1.0)
                            }
                            this.segmentVoxelCountStore.refresh()
                            this.segmentVoxelCountStore.segmentCounts?.takeIf { !it.isEmpty }?.let {
                                var mm = Long.MAX_VALUE
                                var MM = Long.MIN_VALUE
                                val counts = mutableListOf<SegmentVoxelCount>()
                                it.forEachEntry { id, count ->
                                    if (count < mm) mm = count
                                    if (count > MM) MM = count
                                    counts.add(SegmentVoxelCount(id, count))
                                    !isCanceled.asBoolean
                                }
                                // TODO this may be expensive when sorting large lists,
                                // TODO maybe only sort the filtered result (on request)
                                counts.sort()
                                synchronized(segmentCounts) {
                                    if (!isCanceled.asBoolean) {
                                        minMin.value = mm
                                        maxMax.value = MM
                                        segmentCounts.clear()
                                        maxSize.value = min(maxMax.value, maxSize.value)
                                        minSize.value = max(minMin.value, minSize.value)
                                        segmentCounts.setAll(counts)
                                    }
                                }
                            }
                                ?: synchronized(segmentCounts) { segmentCounts.takeUnless { isCanceled.asBoolean }?.clear() }
                        } finally {
                            Platform.runLater {
                                cancelButton.isDisable = true
                                isRefreshButtonDisable.value = false
                                refreshButtonGraphic.value = null
                            }
                        }
                    }
                    singleThreadExecutor.submit(task)

                })

            refreshButtonGraphic.addListener { _, _, new -> new?.prefHeightProperty()?.bind(refreshButton.heightProperty()) }
            isRefreshButtonDisable.addListener { _, _, new -> refreshButton.isDisable = new }

            val buttons = HBox(refreshButton, cancelButton)
            HBox.setHgrow(refreshButton, Priority.ALWAYS)
            HBox.setHgrow(cancelButton, Priority.ALWAYS)
            refreshButtonGraphic.addListener { _, old, new -> buttons.children.let { it.remove(old); new?.let { n -> it.add(n) } } }

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
                .also { it.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY }
            val segmentIdColumn = TableColumn<SegmentVoxelCount, Long>("Segment Id")
                .also { it.setCellValueFactory(PropertyValueFactory("id")) }
            val voxelCountColumn = TableColumn<SegmentVoxelCount, Long>("Voxel Count")
                .also { it.setCellValueFactory(PropertyValueFactory("count")) }
            countTable.columns.setAll(segmentIdColumn, voxelCountColumn)
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

            filteredSegmentCounts.addListener(ListChangeListener { countTable.setIsVisibleAndManaged(it.list.isNotEmpty()) })

            segmentCounts.addListener(ListChangeListener { controls.setIsVisibleAndManaged(it.list.isNotEmpty()) })

            countTable.setIsVisibleAndManaged(filteredSegmentCounts.isNotEmpty())
            controls.setIsVisibleAndManaged(segmentCounts.isNotEmpty())

            val helpDialog = PainteraAlerts
                .alert(Alert.AlertType.INFORMATION, true)
                .also { it.initModality(Modality.NONE) }
                .also { it.headerText = "Segment Counts" }
                .also { it.contentText = "List all segments and their respective voxel counts. " +
                    "Initialize the counts by clicking the Refresh button. " +
                    "Use the min and max sliders and fields to exclude segments that are too small or too large. " +
                    "At the moment, the counts may be inaccurate if changes to the canvas were not committed yet. " +
                    "After committing, hit the refresh button again to update the counts." }
            val tpGraphics = HBox(
                Labels.withTooltip("Segment Counts", "Double-click entry in table to navigate to that segment."),
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }
            return with (TitledPaneExtensions) {
                TitledPanes
                    .createCollapsed(null, VBox(buttons, controls, countTable))
                    .also { it.graphicsOnly(tpGraphics) }
                    .also { it.alignment = Pos.CENTER_RIGHT }
            }

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

}

