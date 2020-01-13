package org.janelia.saalfeldlab.paintera.state.intersecting

import javafx.application.Platform
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import net.imglib2.RealLocalizable
import net.imglib2.RealPoint
import net.imglib2.algorithm.neighborhood.DiamondShape
import net.imglib2.cache.Cache
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.type.BooleanType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.state.label.feature.PointWithinObject
import org.janelia.saalfeldlab.paintera.state.label.feature.count.SegmentVoxelCount
import org.janelia.saalfeldlab.paintera.state.label.feature.count.SegmentVoxelCountStore
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.SingleTaskExecutor
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.BooleanSupplier
import kotlin.math.min

class GoToSegmentWithSynapseNode(
    private val segmentVoxelCountStore: SegmentVoxelCountStore,
    private val source: DataSource<IntegerType<*>, *>,
    private val maskSource: DataSource<BooleanType<*>, *>,
    private val labelBlockLookup: LabelBlockLookup) {

    private class VoxelCountAndLocation(val id: Long, val voxelCount: Long, val location: RealLocalizable)

    private val singleThreadExecutor = SingleTaskExecutor(NamedThreadFactory("label-segment-count-node-%d", true))

    private val pointsWithinObjectCache = SoftRefLoaderCache<Long, PointWithinObject>().withLoader {
        PointWithinObject.findPointInIdAndMask(it, source, maskSource, labelBlockLookup, DiamondShape(1))
    }

    @Synchronized
    private fun refresh(invalidLabelBlockCache: Boolean = false, invalidateCountsPerBlock: Boolean = false) {
        pointsWithinObjectCache.invalidateAll()
        segmentVoxelCountStore.refresh(invalidLabelBlockCache, invalidateCountsPerBlock)
    }

    val node: Node
        get() {
            val segmentCounts = FXCollections.observableArrayList<SegmentVoxelCount>()
            val minMin = SimpleLongProperty(1)
            val maxMax = SimpleLongProperty(Long.MAX_VALUE)
            val minSize = SimpleLongProperty(minMin.value)
            val maxSize = SimpleLongProperty(maxMax.value)

            val filteredSegmentLocations = FXCollections.observableArrayList<VoxelCountAndLocation>()

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

            val currentIndex = SimpleIntegerProperty(0)
            val startIndex = SimpleIntegerProperty(0)
            val currentIndexIsZero = currentIndex.isEqualTo(0)

            val nextButton = Buttons.withTooltip("_Next", "Jump to next smallest segment that intersects with synapses") {
                if (segmentCounts.isNotEmpty()) {
                    val index = currentIndex.get() + 1
                    if (filteredSegmentLocations.size < index) {
                        startIndex.value = filteredSegmentLocations.addNextN(
                            segmentCounts,
                            minSize.value,
                            maxSize.value,
                            startIndex.value,
                            10,
                            pointsWithinObjectCache)
                    }
                    if (index < filteredSegmentLocations.size) {
                        Platform.runLater { LOG.error("LOL KAPOTT!")/* TODO navigate to index */ }
                        currentIndex.set(index)
                    }
                }
            }

            val previousButton = Buttons.withTooltip("_Previous", "Jump to previous smallest segment that intersects with synapses" ) {
                val index = currentIndex.get() - 1
                Platform.runLater { /* TODO navigate to index */ }
                currentIndex.value = index
            }
                .also { it.disableProperty().bind(currentIndexIsZero) }


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
                                        minSize.value = IdService.max(minMin.value, minSize.value)
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

            val table = TableView(filteredSegmentLocations)
                .also { it.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY }
            val segmentIdColumn = TableColumn<SegmentVoxelCount, Long>("Segment Id")
                .also { it.setCellValueFactory(PropertyValueFactory("id")) } as TableColumn<VoxelCountAndLocation, *>
            val voxelCountColumn = TableColumn<SegmentVoxelCount, Long>("Voxel Count")
                .also { it.setCellValueFactory(PropertyValueFactory("voxelCount")) } as TableColumn<VoxelCountAndLocation, *>
            val locationColumn = TableColumn<SegmentVoxelCount, RealLocalizable>("Location")
                .also { it.setCellValueFactory(PropertyValueFactory("location")) } as TableColumn<VoxelCountAndLocation, *>
            table.columns.setAll(segmentIdColumn, voxelCountColumn, locationColumn)

            val controls = GridPane()
            controls.add(Labels.withTooltip("Min", "Minimum segment size (inclusive)"), 0, 1)
            controls.add(Labels.withTooltip("Max", "Maximum segment size (inclusive)"), 0, 2)
            controls.add(minSlider.textField, 1, 1)
            controls.add(maxSlider.textField, 1, 2)
            controls.add(minSlider.slider, 2, 1)
            controls.add(maxSlider.slider, 2, 2)
            GridPane.setHgrow(minSlider.slider, Priority.ALWAYS)
            GridPane.setHgrow(maxSlider.slider, Priority.ALWAYS)

            val buttons = HBox(refreshButton, cancelButton, nextButton, previousButton)
            HBox.setHgrow(refreshButton, Priority.ALWAYS)
            HBox.setHgrow(cancelButton, Priority.ALWAYS)
            refreshButtonGraphic.addListener { _, old, new -> buttons.children.let { it.remove(old); new?.let { n -> it.add(n) } } }

            table.setIsVisibleAndManaged(filteredSegmentLocations.isNotEmpty())
            controls.setIsVisibleAndManaged(segmentCounts.isNotEmpty())

            val helpDialog = PainteraAlerts
                .alert(Alert.AlertType.INFORMATION, true)
                .also { it.initModality(Modality.NONE) }
                .also { it.headerText = "Segments With Synapses" }
                .also { it.contentText = "Navigate to next smallest segment that intersects with a synapse." }
            val tpGraphics = HBox(
                Labels.withTooltip("Segments With Synapses", "Double-click entry in table to navigate to that segment."),
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }
            return with (TitledPaneExtensions) {
                TitledPanes
                    .createCollapsed(null, VBox(buttons, controls, table))
                    .also { it.graphicsOnly(tpGraphics) }
                    .also { it.alignment = Pos.CENTER_RIGHT }
            }
        }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun ObservableList<VoxelCountAndLocation>.addNextN(
            counts: List<SegmentVoxelCount>,
            minInclusive: Long,
            maxInclusive: Long,
            startIndex: Int,
            n: Int,
            cache: Cache<Long, PointWithinObject>): Int {
            var index = startIndex
            var remaining = n
            while (index < counts.size && remaining > 0) {
                val svc = counts[index]
                ++index
                if (svc.count < minInclusive || svc.count > maxInclusive)
                    continue
                cache[svc.id]?.value?.let {
                    this += VoxelCountAndLocation(svc.id, svc.count, RealPoint(it))
                    remaining -= 1
                }
            }

            return index
        }

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
}
