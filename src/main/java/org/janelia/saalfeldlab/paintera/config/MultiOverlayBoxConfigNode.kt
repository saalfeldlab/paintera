package org.janelia.saalfeldlab.paintera.config

import bdv.fx.viewer.multibox.MultiBoxOverlayConfig
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.TitledPaneExtensions.Companion.graphicsOnly
import org.janelia.saalfeldlab.paintera.state.LabelSourceStateMeshPaneNode
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.Colors

class MultiOverlayBoxConfigNode() {

	constructor(config: MultiBoxOverlayConfig): this() {
		this.bind(config)
	}

    private val isVisible = SimpleBooleanProperty(true)

    val contents: Node
        get() {


            val isVisibleCheckBox = CheckBox().also { it.selectedProperty().bindBidirectional(isVisible) }

            val helpDialog = PainteraAlerts
                .alert(Alert.AlertType.INFORMATION, true)
                .also { it.initModality(Modality.NONE) }
                .also { it.headerText = "Multi-Box Overlay" }
                .also { it.contentText = "Draw overlays of the current screen and all sources in world space to " +
                    "indicate orientation and position of current cross-section in world and relative to data." }

            val tpGraphics = HBox(
                Label("Multi-Box Overlay"),
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
                CheckBox().also { it.selectedProperty().bindBidirectional(isVisible) },
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }

            return TitledPane("Meshes", null)
                .also { it.isExpanded = false }
                .also { with(TitledPaneExtensions) { it.graphicsOnly(tpGraphics)} }
                .also { it.alignment = Pos.CENTER_RIGHT }

        }

    fun bind(config: MultiBoxOverlayConfig) {
        isVisible.bindBidirectional(config.isVisibleProperty())
    }

}
