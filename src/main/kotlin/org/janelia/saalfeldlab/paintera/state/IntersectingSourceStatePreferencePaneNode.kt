package org.janelia.saalfeldlab.paintera.state

import javafx.beans.binding.Bindings
import javafx.scene.Node
import javafx.scene.control.ColorPicker
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController.Companion.addGridOption
import org.janelia.saalfeldlab.util.Colors

class IntersectingSourceStatePreferencePaneNode(private val state: IntersectingSourceState<*, *>) {

    val node: Node
        get() {
            val manager = state.meshManager
            val settings = manager.settings
            return MeshSettingsController(settings, state::refreshMeshes).createTitledPane(
                false,
                manager.managedSettings.meshesEnabledProperty,
                MeshSettingsController.HelpDialogSettings("Meshes"),
                MeshSettingsController.TitledPaneGraphicsSettings("Meshes")
            ) {
                val conversionBinding = Bindings.createObjectBinding(
                    { Colors.toColor(state.converter().color) },
                    state.converter().colorProperty()
                )
                val colorPicker = ColorPicker(conversionBinding.get()).apply {
                    valueProperty().addListener { _, _, new ->
                        state.converter().color = Colors.toARGBType(new)
                    }
                }

                addGridOption("Color", colorPicker)
            }
        }
}
