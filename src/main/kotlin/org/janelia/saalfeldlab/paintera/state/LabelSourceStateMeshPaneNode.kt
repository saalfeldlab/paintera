package org.janelia.saalfeldlab.paintera.state

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.ContentDisplay
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import net.imglib2.type.label.LabelMultisetType

import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar.Companion.ReverseBehavior.SET
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.GlobalMeshProgressState
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterObj
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfoList
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController
import org.janelia.saalfeldlab.paintera.meshes.ui.exportMeshWithProgressPopup
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportModel
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportModel.Companion.initFromProject
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshProgressBar
import kotlin.jvm.optionals.getOrNull

class LabelSourceStateMeshPaneNode(
	private val source: DataSource<*, *>,
	private val manager: MeshManagerWithAssignmentForSegments,
	private val meshInfos: SegmentMeshInfoList,
) {

	val node: Node
		get() = makeNode()

	private fun makeNode(): Node {
		val globalMeshSettings = manager.globalSettings
		val meshSettingsController = MeshSettingsController(globalMeshSettings, manager::refreshMeshes)
		val meshSettingsTitlePane = meshSettingsController.createTitledPane(
			source.dataType is LabelMultisetType,
			manager.managedSettings.meshesEnabledProperty,
			titledPaneGraphicsSettings = MeshSettingsController.TitledPaneGraphicsSettings("Meshes"),
		).apply {
			content = content.asVBox().apply {
				children += GlobalMeshSettingsPane(manager, meshInfos)
			}
		}

		return meshSettingsTitlePane
	}

	private class GlobalMeshSettingsPane(
		private val manager: MeshManagerWithAssignmentForSegments,
		private val meshInfoList: SegmentMeshInfoList
	) : TitledPane("Mesh List", null) {

		private val disabledMeshesBinding = manager.managedSettings.meshesEnabledProperty.not()
		private val globalMeshProgress = GlobalMeshProgressState(
			ReadOnlyObjectWrapper.objectExpression(meshInfoList.itemsProperty()),
			disabledMeshesBinding
		)
		private val totalProgressBar = MeshProgressBar(SET).also {
			it.bindTo(globalMeshProgress.progressBinding)
		}

		init {

			val exportMeshButton = Button("Export all")
			exportMeshButton.setOnAction { _ ->
				val model = MeshExportModel
					.fromMeshInfos(*meshInfoList.items.toTypedArray())
					.initFromProject()
				MeshExportDialog(model)
					.showAndWait()
					.getOrNull()?.apply {

						if (meshExporter.isCancelled) return@apply

						val ids = meshKeys.toTypedArray()
						val meshSettings = ids.map { manager.getSettings(it) }.toTypedArray()
						(meshExporter as? MeshExporterObj<*>)?.run {
							val colors: Array<Color> = ids.mapIndexed { idx, it ->
								val color = manager.getStateFor(it)?.color ?: Color.WHITE
								color.deriveColor(0.0, 1.0, 1.0, meshSettings[idx].opacity)
							}.toTypedArray()
							exportMaterial(filePath, ids.map { it.toString() }.toTypedArray(), colors)
						}
						manager.exportMeshWithProgressPopup(this)
					}
			}

			val buttonBox = HBox(exportMeshButton).also { it.alignment = Pos.BOTTOM_RIGHT }
			val meshesBox = VBox(meshInfoList, Separator(Orientation.HORIZONTAL), buttonBox)
			listOf(meshesBox, meshInfoList).forEach { it.padding = Insets.EMPTY }

			val tpGraphics = HBox(
				10.0,
				Label("Mesh List"),
				totalProgressBar.hGrow(),
			).apply {
				minWidthProperty().set(0.0)
				alignment = Pos.CENTER_LEFT
				isFillHeight = true
			}
			graphic = tpGraphics
			contentDisplay = ContentDisplay.GRAPHIC_ONLY
			alignment = Pos.CENTER_RIGHT
			content = meshesBox
		}
	}


	companion object {

		private val LOG = KotlinLogging.logger { }

		private fun Node.asVBox() = this as? VBox ?: VBox(this)

	}

}
