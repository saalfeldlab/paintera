package org.janelia.saalfeldlab.paintera.state

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
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
            helpButton.onAction = EventHandler { helpDialog.show() }
            helpButton.alignment = Pos.CENTER

            val tpGraphics = HBox(
                Label("Meshes"),
                spacer,
                enabledCheckBox,
                refreshButton,
                helpButton)
            tpGraphics.alignment = Pos.CENTER

            val exportMeshButton = Button("Export")
            exportMeshButton.setOnAction {
                // TODO
//                val exportDialog = MeshExporterDialog<Long>(meshInfo)
//                val result = exportDialog.showAndWait()
//                if (result.isPresent) {
//                    val parameters = result.get()
//                    parameters.meshExporter.exportMesh(
//                        manager.getBlockListForLongKey,
//                        manager.getMeshForLongKey,
//                        parameters.segmentId.map { it }.toTypedArray(),
//                        parameters.scale,
//                        parameters.filePaths)
//                }
            }

            val contents = GridPane()
            contents.children += exportMeshButton

            val tp = TitledPane(null, contents)
                .also { it.isExpanded = false }
                .also { with (TitledPaneExtensions) { it.graphicsOnly(tpGraphics) } }
                .also { it.alignment = Pos.CENTER_RIGHT }

            vbox.children.add(tp)
            return vbox
        }
}
