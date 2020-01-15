package org.janelia.saalfeldlab.paintera.config

import bdv.fx.viewer.multibox.MultiBoxOverlayConfig
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Modality
import javafx.util.Callback
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

class MultiBoxOverlayConfigNode() {

	constructor(config: MultiBoxOverlayConfig): this() {
		this.bind(config)
	}

    private val visibility = SimpleObjectProperty(MultiBoxOverlayConfig.DefaultValues.VISIBILITY)

    val contents: Node
        get() {

            val visibilityChoiceBox = ComboBox(VISIBILITY_CHOICES)
                .also { it.tooltip = Tooltip("Set the visibility of the multi-box overlay.") }
                .also { it.valueProperty().bindBidirectional(visibility) }
                .also { it.cellFactory = VISIBILITY_CELL_FACTORY }
                .also { it.prefWidth = 80.0 }

            val helpDialog = PainteraAlerts
                .alert(Alert.AlertType.INFORMATION, true)
                .also { it.initModality(Modality.NONE) }
                .also { it.headerText = "Multi-Box Overlay" }
                .also { it.contentText = "Draw overlays of the current screen and all sources in world space to " +
                    "indicate orientation and position of current cross-section in world and relative to data. " +
                    "Uncheck to disable the overlay." }

            val tpGraphics = HBox(
                Label("Multi-Box Overlay"),
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
                visibilityChoiceBox,
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }

            return TitledPane("Meshes", null)
                .also { it.isExpanded = false }
                .also { with(TitledPaneExtensions) { it.graphicsOnly(tpGraphics)} }
                .also { it.alignment = Pos.CENTER_RIGHT }

        }

    fun bind(config: MultiBoxOverlayConfig) {
        visibility.bindBidirectional(config.visibilityProperty())
    }

    private class VisibilityCell : ListCell<MultiBoxOverlayConfig.Visibility>() {
        override fun updateItem(visibility: MultiBoxOverlayConfig.Visibility?, empty: Boolean) {
            super.updateItem(visibility, empty)
            text = item?.name
            tooltip = item?.description?.let { Tooltip(it) }
        }
    }

    companion object {
        private val VISIBILITY_CHOICES = FXCollections.observableArrayList(*MultiBoxOverlayConfig.Visibility.values())
        private val VISIBILITY_CELL_FACTORY = Callback<ListView<MultiBoxOverlayConfig.Visibility>, ListCell<MultiBoxOverlayConfig.Visibility>> { VisibilityCell() }
    }

}
