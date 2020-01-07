package org.janelia.saalfeldlab.paintera.config

import bdv.fx.viewer.multibox.MultiBoxOverlayConfig
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

class MultiOverlayBoxConfigNode() {

	constructor(config: MultiBoxOverlayConfig): this() {
		this.bind(config)
	}

    private val isVisible = SimpleBooleanProperty(true)

    private val isVisibleOnlyInFocusedViewer = SimpleBooleanProperty(true)

    val contents: Node
        get() {


            val isVisibleOnlyInFocusedViewerCheckBox = CheckBox("Only in focused viewer")
                .also { it.tooltip = Tooltip("If checked, the multi-box overlay will be visible only in the currently " +
                    "focused viewer (if any). Uncheck to make it visible in all viewers.") }
                .also { it.selectedProperty().bindBidirectional(isVisibleOnlyInFocusedViewer) }

            val contents = VBox(isVisibleOnlyInFocusedViewerCheckBox)
                .also { it.alignment = Pos.CENTER_LEFT }

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
                CheckBox().also { it.selectedProperty().bindBidirectional(isVisible) },
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }

            return TitledPane("Meshes", contents)
                .also { it.isExpanded = false }
                .also { with(TitledPaneExtensions) { it.graphicsOnly(tpGraphics)} }
                .also { it.alignment = Pos.CENTER_RIGHT }

        }

    fun bind(config: MultiBoxOverlayConfig) {
        isVisible.bindBidirectional(config.isVisibleProperty())
        isVisibleOnlyInFocusedViewer.bindBidirectional(config.isVisibleOnlyInFocusedViewerProperty())
    }

}
