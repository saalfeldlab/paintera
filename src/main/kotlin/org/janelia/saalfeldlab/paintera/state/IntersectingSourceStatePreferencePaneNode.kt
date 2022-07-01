package org.janelia.saalfeldlab.paintera.state

import javafx.scene.Node
import javafx.scene.control.ColorPicker
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController.Companion.addGridOption
import org.janelia.saalfeldlab.util.Colors

class IntersectingSourceStatePreferencePaneNode(private val state: IntersectingSourceState<*, *>) {

    val node: Node
        get() {
            val manager = state.meshManager
            val settings = manager.settings

            return SourceState.defaultPreferencePaneNode(state.compositeProperty()).apply {
                children += MeshSettingsController(settings, state::refreshMeshes).createTitledPane(
                    false,
                    manager.managedSettings.meshesEnabledProperty,
                    MeshSettingsController.HelpDialogSettings("Meshes"),
                    MeshSettingsController.TitledPaneGraphicsSettings("Meshes")
                ) {
                    val conversionBinding = state.converter().colorProperty().createNullableValueBinding { Colors.toColor(it) }
                    val colorPicker = ColorPicker(conversionBinding.get()).apply {
                        valueProperty().addListener { _, _, new ->
                            state.converter().color = Colors.toARGBType(new)
                        }
                    }

                    addGridOption("Color", colorPicker)
                }
            }
        }
}
