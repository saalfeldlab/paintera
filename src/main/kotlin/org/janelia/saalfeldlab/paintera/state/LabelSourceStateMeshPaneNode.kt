package org.janelia.saalfeldlab.paintera.state

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import net.imglib2.type.label.LabelMultisetType
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions.Companion.expandIfEnabled
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions.Companion.graphicsOnly
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
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

typealias TPE = TitledPaneExtensions

class LabelSourceStateMeshPaneNode(
	private val source: DataSource<*, *>,
	private val manager: MeshManagerWithAssignmentForSegments,
	private val meshInfos: SegmentMeshInfoList,
) {

	val node: Node
		get() = makeNode()

	private fun makeNode(): Node {
		val settings = manager.globalSettings
		val tp = MeshSettingsController(settings, manager::refreshMeshes).createTitledPane(
			source.dataType is LabelMultisetType,
			manager.managedSettings.meshesEnabledProperty,
			titledPaneGraphicsSettings = MeshSettingsController.TitledPaneGraphicsSettings("Meshes"),
		)
		with(tp.content.asVBox()) {
			tp.content = this
			children.add(MeshSettingsPane(manager, meshInfos))
			return tp
		}
	}

	private class MeshSettingsPane(
		private val manager: MeshManagerWithAssignmentForSegments,
		private val meshInfoList: SegmentMeshInfoList,
	) : TitledPane("Mesh List", null) {

		private val isMeshListEnabledCheckBox = CheckBox()
		private val disabledMeshesBinding = isMeshListEnabledCheckBox.selectedProperty().not()
		private val observableMeshProgresses = meshInfoList.meshInfos.readOnlyProperty
		private val globalMeshProgress = GlobalMeshProgressState(observableMeshProgresses, disabledMeshesBinding)
		private val totalProgressBar = MeshProgressBar().also {
			it.bindTo(globalMeshProgress)
		}

		init {

			val exportMeshButton = Button("Export all")
			exportMeshButton.setOnAction { _ ->
				val model = MeshExportModel
					.fromMeshInfos(*meshInfoList.meshInfos.toTypedArray())
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

			isMeshListEnabledCheckBox.also { it.selectedProperty().bindBidirectional(manager.managedSettings.isMeshListEnabledProperty) }

			val tpGraphics = HBox(
				10.0,
				Label("Mesh List"),
				totalProgressBar.hGrow(),
				isMeshListEnabledCheckBox
			).apply {
				minWidthProperty().set(0.0)
				alignment = Pos.CENTER_LEFT
				isFillHeight = true
			}

			expandIfEnabled(isMeshListEnabledCheckBox.selectedProperty())
			graphicsOnly(tpGraphics)
			alignment = Pos.CENTER_RIGHT
			meshInfoList.prefWidthProperty().bind(layoutBoundsProperty().createNonNullValueBinding { it.width - 5 })
			content = meshesBox
		}
	}


	companion object {

		private val LOG = KotlinLogging.logger { }

		private fun Node.asVBox() = this as? VBox ?: VBox(this)

	}

}
