package org.janelia.saalfeldlab.paintera.state

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterObj
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithSingleMesh
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController.Companion.addGridOption
import org.janelia.saalfeldlab.paintera.meshes.ui.exportMeshWithProgressPopup
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportModel
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportModel.Companion.initFromProject
import org.janelia.saalfeldlab.util.Colors

class IntersectingSourceStatePreferencePaneNode(private val state: IntersectingSourceState<*, *>) {

	val node: Node
		get() {
			val manager = state.meshManager
			val settings = manager.globalSettings

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
				}.also {
					it.content = VBox(it.content).apply {
						val exportMeshButton = Button("Export all")
						exportMeshButton.setOnAction { _ ->
							val manager = state.meshManager as MeshManagerWithSingleMesh<IntersectingSourceStateMeshCacheKey<*, *>>
							val key = manager.meshKey!!
							val model = MeshExportModel.fromMeshInfos(MeshInfo(key, manager))
								.initFromProject()
							val exportDialog = MeshExportDialog(model)
							val result = exportDialog.showAndWait()
							if (result.isPresent) {
								result.get().run {
									if (meshExporter.isCancelled) return@run
									(meshExporter as? MeshExporterObj<*>)?.run {
										exportMaterial(filePath, arrayOf(""), arrayOf(Colors.toColor(state.converter().color)))
									}
								}
								manager.exportMeshWithProgressPopup(result.get())
							}
						}
						val buttonBox = HBox(exportMeshButton).also { it.alignment = Pos.BOTTOM_RIGHT }
						children += buttonBox

					}
				}
			}
		}
}