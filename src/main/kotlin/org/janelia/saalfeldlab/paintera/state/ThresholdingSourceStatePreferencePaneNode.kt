package org.janelia.saalfeldlab.paintera.state

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterObj
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController
import org.janelia.saalfeldlab.paintera.meshes.ui.exportMeshWithProgressPopup
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts.initAppDialog
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshExporterDialog

class ThresholdingSourceStatePreferencePaneNode(private val state: ThresholdingSourceState<*, *>) {

	val node: Node
		get() = SourceState.defaultPreferencePaneNode(state.compositeProperty())
			.also { it.children.addAll(createBasicNote(), createMeshesNode()) }

	private fun createBasicNote(): Node {

		val min = NumberField
			.doubleField(state.minProperty().get(), { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
			.also { it.valueProperty().addListener { _, _, new -> state.minProperty().set(new.toDouble()) } }
		val max = NumberField
			.doubleField(state.maxProperty().get(), { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
			.also { it.valueProperty().addListener { _, _, new -> state.maxProperty().set(new.toDouble()) } }

		val foreground = ColorPicker(state.colorProperty().get()).apply { valueProperty().bindBidirectional(state.colorProperty()) }
		val background = ColorPicker(state.backgroundColorProperty().get()).apply { valueProperty().bindBidirectional(state.backgroundColorProperty()) }

		val minMax = GridPane()
		minMax.add(Label("min"), 0, 0)
		minMax.add(Label("max"), 0, 1)
		minMax.add(Label("foreground"), 0, 2)
		minMax.add(Label("background"), 0, 3)

		minMax.add(min.textField, 1, 0)
		minMax.add(max.textField, 1, 1)
		minMax.add(foreground, 1, 2)
		minMax.add(background, 1, 3)

		GridPane.setHgrow(min.textField, Priority.ALWAYS)
		GridPane.setHgrow(max.textField, Priority.ALWAYS)
		GridPane.setHgrow(foreground, Priority.ALWAYS)
		GridPane.setHgrow(background, Priority.ALWAYS)


		val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
				initModality(Modality.NONE)
				headerText = "Threshold"
				contentText = "TODO" /* TODO */
			}


		val tpGraphics = HBox(
			Label("Threshold"),
			NamedNode.bufferNode(),
			Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
			.also { it.alignment = Pos.CENTER }

		return TitledPanes
			.createCollapsed(null, VBox(minMax)).apply {
				with(TPE) { graphicsOnly(tpGraphics) }
				alignment = Pos.CENTER_RIGHT
				tooltip = null /* TODO */
			}


	}

	private fun createMeshesNode() = MeshSettingsController(state.meshSettings, state::refreshMeshes).createTitledPane(
		false,
		state.meshManager.managedSettings.meshesEnabledProperty,
		titledPaneGraphicsSettings = MeshSettingsController.TitledPaneGraphicsSettings("Meshes"),
		helpDialogSettings = MeshSettingsController.HelpDialogSettings(headerText = "Meshes")
	).also {
		it.content = VBox(it.content).apply {
			val exportMeshButton = Button("Export all")
			exportMeshButton.setOnAction { _ ->
				val exportDialog = MeshExporterDialog(MeshInfo(state.meshManager.meshKey, state.meshManager))
				val result = exportDialog.showAndWait()
				if (result.isPresent) {
					state.meshManager.exportMeshWithProgressPopup(result.get())
					result.get().run {
						if (meshExporter.isCancelled()) return@run
						(meshExporter as? MeshExporterObj<*>)?.run {
							exportMaterial(filePath, arrayOf(""), arrayOf(state.colorProperty().get()))
						}
					}
				}
			}
			val buttonBox = HBox(exportMeshButton).also { it.alignment = Pos.BOTTOM_RIGHT }
			children += buttonBox

		}
	}

}
