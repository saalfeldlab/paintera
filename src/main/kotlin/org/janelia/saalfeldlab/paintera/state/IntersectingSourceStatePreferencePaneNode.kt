package org.janelia.saalfeldlab.paintera.state

import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton.createFontAwesome

class IntersectingSourceStatePreferencePaneNode(private val state: IntersectingSourceState) {

    val node: Node
        get() {
            val vbox = SourceState.defaultPreferencePaneNode(state.compositeProperty()).let { if (it is VBox) it else VBox(it) }

            val spacer = Region()
            HBox.setHgrow(spacer, Priority.ALWAYS)
            spacer.minWidth = 0.0

            val enabledCheckBox = CheckBox()
            enabledCheckBox.selectedProperty().bindBidirectional(state.meshesEnabledProperty())
            enabledCheckBox.tooltip = Tooltip("Toggle meshes on/off. " +
                "If meshes are disabled in the underlying label source, " +
                "meshes for this source are disabled, too.")

            val refreshButton = Buttons.withTooltip(null, "Refresh Meshes", EventHandler { state.refreshMeshes() })
            val reloadSymbol = createFontAwesome(2.0)
            reloadSymbol.rotate = 45.0
            refreshButton.graphic = reloadSymbol


            val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true)
            helpDialog.initModality(Modality.NONE)
            helpDialog.headerText = "Mesh Settings"
            helpDialog.contentText = "" +
                "Intersecting sources inherit their mesh settings from the global settings for the " +
                "underlying label source and cannot be configured explicitly. The meshes can be toggled on/off " +
                "with the check box."

            val helpButton = Button("?")
            helpButton.onAction = EventHandler { e: ActionEvent? -> helpDialog.show() }
            helpButton.alignment = Pos.CENTER

            val tpGraphics = HBox(
                Label("Meshes"),
                spacer,
                enabledCheckBox,
                refreshButton,
                helpButton)
            tpGraphics.alignment = Pos.CENTER

            val tp = TitledPane(null, null)
            tp.isExpanded = false
            tp.alignment = Pos.CENTER_RIGHT
            // Make titled pane title graphics only.
            // TODO make methods in TitledPaneExtensions.kt @JvmStatic so they can be
            // TODO called from here instead of re-writing in Java.
            // Make titled pane title graphics only.
            // TODO make methods in TitledPaneExtensions.kt @JvmStatic so they can be
            // TODO called from here instead of re-writing in Java.
            val regionWidth = tp.widthProperty().subtract(50.0)
            tpGraphics.prefWidthProperty().bind(regionWidth)
            tp.text = null
            tp.graphic = tpGraphics
            tp.contentDisplay = ContentDisplay.GRAPHIC_ONLY

            vbox.children.add(tp)
            return vbox
        }
}
