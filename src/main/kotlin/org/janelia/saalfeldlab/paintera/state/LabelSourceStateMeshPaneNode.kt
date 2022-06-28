package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.ReadOnlyListProperty
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import net.imglib2.type.label.LabelMultisetType
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.GlobalMeshProgress
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfo
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfos
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshProgressBar
import org.janelia.saalfeldlab.paintera.ui.source.mesh.SegmentMeshExporterDialog
import org.janelia.saalfeldlab.paintera.ui.source.mesh.SegmentMeshInfoNode
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Objects
import java.util.stream.Collectors

typealias TPE = TitledPaneExtensions

class LabelSourceStateMeshPaneNode(
    private val source: DataSource<*, *>,
    private val manager: MeshManagerWithAssignmentForSegments,
    private val meshInfos: SegmentMeshInfos,
) {

    val node: Node
        get() = makeNode()

    private fun makeNode(): Node {
        val settings = manager.settings
        val tp = MeshSettingsController(settings, manager::refreshMeshes).createTitledPane(
            source.dataType is LabelMultisetType,
            manager.managedSettings.meshesEnabledProperty,
            titledPaneGraphicsSettings = MeshSettingsController.TitledPaneGraphicsSettings("Meshes"),
            helpDialogSettings = MeshSettingsController.HelpDialogSettings(headerText = "Meshes")
        )
        with(tp.content.asVBox()) {
            tp.content = this
            children.add(MeshesList(source, manager, meshInfos).node)
            return tp
        }
    }

    private class MeshesList(
        private val source: DataSource<*, *>,
        private val manager: MeshManagerWithAssignmentForSegments,
        private val meshInfos: SegmentMeshInfos,
    ) {

        private class Listener(
            private val meshInfos: ReadOnlyListProperty<SegmentMeshInfo>,
            private val totalProgressBar: MeshProgressBar,
        ) : ListChangeListener<SegmentMeshInfo> {

            override fun onChanged(change: ListChangeListener.Change<out SegmentMeshInfo>) {
                updateTotalProgressBindings()
            }

            private fun updateTotalProgressBindings() {
                val individualProgresses = meshInfos.stream().map { it.meshProgress() }.filter { Objects.nonNull(it) }.collect(Collectors.toList())
                val globalProgress = GlobalMeshProgress(individualProgresses)
                this.totalProgressBar.bindTo(globalProgress)
            }
        }

        val node: Node
            get() = createNode()

        private val isMeshListEnabledCheckBox = CheckBox()
        private val totalProgressBar = MeshProgressBar()

        private fun createNode(): TitledPane {

            val meshesInfoList = ListView(meshInfos.readOnlyInfos())

            meshesInfoList.setCellFactory {
                object : ListCell<SegmentMeshInfo>() {

                    init {
                        style = "-fx-padding: 0px"
                    }

                    override fun updateItem(item: SegmentMeshInfo?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = null
                        InvokeOnJavaFXApplicationThread {
                            graphic = if (empty || item == null) null else SegmentMeshInfoNode(source, item).node.also { cell ->
                                cell.prefWidthProperty().bind(it.prefWidthProperty())
                            }
                        }
                    }
                }
            }


            val exportMeshButton = Button("Export all")
            exportMeshButton.setOnAction { _ ->
                val exportDialog = SegmentMeshExporterDialog<Long>(meshesInfoList.items)
                val result = exportDialog.showAndWait()
                if (result.isPresent) {
                    val parameters = result.get()
                    parameters.meshExporter.exportMesh(
                        manager.getBlockListForLongKey,
                        manager.getMeshForLongKey,
                        parameters.segmentId.map { it }.toTypedArray(),
                        parameters.scale,
                        parameters.filePaths
                    )
                }
            }

            val buttonBox = HBox(exportMeshButton).also { it.alignment = Pos.BOTTOM_RIGHT }
            val meshesBox = VBox(meshesInfoList, Separator(Orientation.HORIZONTAL), buttonBox)
            listOf(meshesBox, meshesInfoList).forEach { it.padding = Insets.EMPTY }

            isMeshListEnabledCheckBox.also { it.selectedProperty().bindBidirectional(meshInfos.meshSettings().isMeshListEnabledProperty) }

            val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
                initModality(Modality.NONE)
                headerText = "Mesh List."
                contentText = "TODO"
            }

            val tpGraphics = HBox(
                10.0,
                Label("Mesh List"),
                totalProgressBar.also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.text = "" },
                isMeshListEnabledCheckBox,
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } }
            ).apply {
                minWidthProperty().set(0.0)
                alignment = Pos.CENTER_LEFT
                isFillHeight = true
            }

            meshInfos.readOnlyInfos().addListener(Listener(meshInfos.readOnlyInfos(), totalProgressBar))

            return TitledPane("Mesh List", meshesBox).apply {
                with(TPE) {
                    expandIfEnabled(isMeshListEnabledCheckBox.selectedProperty())
                    graphicsOnly(tpGraphics)
                    alignment = Pos.CENTER_RIGHT
                    meshesInfoList.prefWidthProperty().bind(layoutBoundsProperty().createNonNullValueBinding { it.width - 5 })
                }
            }
        }
    }


    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun Node.asVBox() = if (this is VBox) this else VBox(this)

    }

}
