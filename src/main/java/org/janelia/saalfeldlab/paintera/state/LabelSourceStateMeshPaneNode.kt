package org.janelia.saalfeldlab.paintera.state

import gnu.trove.set.hash.TLongHashSet
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.Property
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos
import org.janelia.saalfeldlab.paintera.meshes.MeshManager
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshExporterDialog
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshInfoNode
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Optional

typealias TPE = TitledPaneExtensions

class LabelSourceStateMeshPaneNode(
		private val manager: MeshManager<Long, TLongHashSet>,
		private val meshInfos: MeshInfos<TLongHashSet>) {

	val node: Node
		get() = makeNode()

    private fun makeNode(): Node {
		val contents = meshInfos.meshSettings().globalSettings.let {
			VBox(
					GlobalSettings(
							meshInfos.numScaleLevels,
							it.opacityProperty(),
							it.scaleLevelProperty(),
							it.smoothingLambdaProperty(),
							it.smoothingIterationsProperty(),
							it.inflateProperty(),
							it.drawModeProperty(),
							it.cullFaceProperty()).node,
					MeshesList(manager, meshInfos).node)
		}

		val helpDialog = PainteraAlerts
				.alert(Alert.AlertType.INFORMATION, true)
				.also { it.initModality(Modality.NONE) }
				.also { it.headerText = "Mesh Settings" }
				.also { it.contentText = "TODO" }

		val tpGraphics = HBox(
				Label("Meshes"),
				Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
				CheckBox().also { it.selectedProperty().bindBidirectional(meshInfos.meshSettings().globalSettings.isVisibleProperty) }.also { it.tooltip = Tooltip("Toggle visibility") },
				Buttons.withTooltip(null, "Refresh Meshes") { manager.refreshMeshes() }.also { it.graphic = makeReloadSymbol() },
				Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
				.also { it.alignment = Pos.CENTER }

        return TitledPane("Meshes", contents)
				.also { it.isExpanded = false }
				.also { with(TPE) { it.graphicsOnly(tpGraphics)} }
				.also { it.alignment = Pos.CENTER_RIGHT }
    }

	private class GlobalSettings(
			val numScaleLevels: Int,
			val opacity: DoubleProperty,
			val scale: IntegerProperty,
			val smoothingLambda: DoubleProperty,
			val smoothingIterations: IntegerProperty,
			val inflate: DoubleProperty,
			val drawMode: Property<DrawMode>,
			val cullFace: Property<CullFace>) {


		val node: Node
			get() = createNode()

		private fun createNode(): TitledPane {
			val contents = GridPane()

			populateGridWithMeshSettings(
					contents,
					0,
					NumericSliderWithField(0.0, 1.0, opacity.value).also { it.slider().valueProperty().bindBidirectional(opacity) },
					NumericSliderWithField(0, this.numScaleLevels - 1, scale.value).also { it.slider().valueProperty().bindBidirectional(scale) },
					NumericSliderWithField(0.0, 1.00, .05).also { it.slider().valueProperty().bindBidirectional(smoothingLambda) },
					NumericSliderWithField(0, 10, 5).also { it.slider().valueProperty().bindBidirectional(smoothingIterations) },
					NumericSliderWithField(0.5, 2.0, inflate.value).also { it.slider().valueProperty().bindBidirectional(inflate) },
					ComboBox(FXCollections.observableArrayList(*DrawMode.values())).also { it.valueProperty().bindBidirectional(drawMode) },
					ComboBox(FXCollections.observableArrayList(*CullFace.values())).also { it.valueProperty().bindBidirectional(cullFace) })

			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = "Mesh Settings" }
					.also { it.contentText = "TODO" }

			val tpGraphics = HBox(
					Label("Mesh Settings"),
					Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER }

			return TitledPane("", contents)
					.also { it.isExpanded = false }
					.also { with(TPE) { it.graphicsOnly(tpGraphics)} }
					.also { it.alignment = Pos.CENTER_RIGHT }
		}

	}

	private class MeshesList(
			private val manager: MeshManager<Long, TLongHashSet>,
			private val meshInfos: MeshInfos<TLongHashSet>) {

		private class Listener(
				private val manager: MeshManager<Long, TLongHashSet>,
				private val meshInfos: MeshInfos<TLongHashSet>,
				private val meshesBox: Pane): ListChangeListener<MeshInfo<TLongHashSet>> {

			val infoNodesCache = FXCollections.observableHashMap<MeshInfo<TLongHashSet>, MeshInfoNode<TLongHashSet>>()

			val infoNodes = FXCollections.observableArrayList<MeshInfoNode<TLongHashSet>>()

			override fun onChanged(change: ListChangeListener.Change<out MeshInfo<TLongHashSet>>) {
				while (change.next())
					if (change.wasRemoved())
						change.removed.forEach { info -> Optional.ofNullable(infoNodesCache.remove(info)).ifPresent { it.unbind() } }
				populateInfoNodes(this.meshInfos.readOnlyInfos())
			}

			private fun populateInfoNodes(infos: List<MeshInfo<TLongHashSet>>) {
				val infoNodes = infos.map { MeshInfoNode(it).also { it.bind() } }
				LOG.debug("Setting info nodes: {}: ", infoNodes)
				this.infoNodes.setAll(infoNodes)
				val exportMeshButton = Button("Export all")
				exportMeshButton.setOnAction { event ->
					val exportDialog = MeshExporterDialog(meshInfos)
					val result = exportDialog.showAndWait()
					if (result.isPresent) {
						val parameters = result.get()

						val blockListCaches = Array(meshInfos.readOnlyInfos().size) { manager.blockListCache() }
						val meshCaches = Array(blockListCaches.size) { manager.meshCache() }

						parameters.meshExporter.exportMesh(
								blockListCaches,
								meshCaches,
								parameters.segmentId.map { manager.unmodifiableMeshMap()[it]?.id }.toTypedArray(),
								parameters.scale,
								parameters.filePaths)
					}
				}

				InvokeOnJavaFXApplicationThread.invoke {
					this.meshesBox.children.setAll(infoNodes.map { it.get() })
					this.meshesBox.children.add(exportMeshButton)
				}
			}
		}

		val node: Node
			get() = createNode()

		private fun createNode(): TitledPane {

			val meshesBox = VBox()

			val isMeshListEnabledCheckBox = CheckBox().also { it.selectedProperty().bindBidirectional(meshInfos.meshSettings().isMeshListEnabledProperty) }

			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = "Mesh List." }
					.also { it.contentText = "TODO" }

			val tpGraphics = HBox(
					Label("MeshList"),
					Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
					isMeshListEnabledCheckBox,
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER }
			meshInfos.readOnlyInfos().addListener(Listener(manager, meshInfos, meshesBox))

			return TitledPane("Mesh List", meshesBox)
					.also { with(TPE) { it.expandIfEnabled(isMeshListEnabledCheckBox.selectedProperty()) } }
					.also { with(TPE) { it.graphicsOnly(tpGraphics)} }
					.also { it.alignment = Pos.CENTER_RIGHT }

		}

	}

    companion object {

		// not available in the font we're using, bummer...
		private const val REFRESH_SYMBOL = "🗘"

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun populateGridWithMeshSettings(
                contents: GridPane,
                initialRow: Int,
                opacitySlider: NumericSliderWithField,
                scaleSlider: NumericSliderWithField,
                smoothingLambdaSlider: NumericSliderWithField,
                smoothingIterationsSlider: NumericSliderWithField,
                inflateSlider: NumericSliderWithField,
                drawModeChoice: ComboBox<DrawMode>,
                cullFaceChoice: ComboBox<CullFace>): Int {
            var row = initialRow

            val textFieldWidth = 55.0
            val choiceWidth = 95.0

			val setupSlider = { slider: NumericSliderWithField, ttText: String ->
				slider
						.also { it.slider().isShowTickLabels = true }
						.also { it.slider().tooltip = Tooltip(ttText) }
						.also { it.textField().prefWidth = textFieldWidth }
						.also { GridPane.setHgrow(it.slider(), Priority.ALWAYS) }
			}

            contents.add(Labels.withTooltip("Opacity"), 0, row)
            contents.add(opacitySlider.slider(), 1, row)
            GridPane.setColumnSpan(opacitySlider.slider(), 2)
            contents.add(opacitySlider.textField(), 3, row)
			setupSlider(opacitySlider, "Mesh Opacity")
            ++row

            contents.add(Labels.withTooltip("Scale"), 0, row)
            contents.add(scaleSlider.slider(), 1, row)
            GridPane.setColumnSpan(scaleSlider.slider(), 2)
            contents.add(scaleSlider.textField(), 3, row)
			setupSlider(scaleSlider, "Scale Level")
            ++row

            contents.add(Labels.withTooltip("Lambda"), 0, row)
            contents.add(smoothingLambdaSlider.slider(), 1, row)
            GridPane.setColumnSpan(smoothingLambdaSlider.slider(), 2)
            contents.add(smoothingLambdaSlider.textField(), 3, row)
			setupSlider(smoothingLambdaSlider, "Smoothing Lambda")
            ++row

            contents.add(Labels.withTooltip("Iterations"), 0, row)
            contents.add(smoothingIterationsSlider.slider(), 1, row)
            GridPane.setColumnSpan(smoothingIterationsSlider.slider(), 2)
            contents.add(smoothingIterationsSlider.textField(), 3, row)
			setupSlider(smoothingIterationsSlider, "Smoothing Iterations")
            ++row

            contents.add(Labels.withTooltip("Inflate"), 0, row)
            contents.add(inflateSlider.slider(), 1, row)
            GridPane.setColumnSpan(inflateSlider.slider(), 2)
            contents.add(inflateSlider.textField(), 3, row)
			setupSlider(inflateSlider, "Inflate Meshes by Factor")
            ++row

            val drawModeLabel = Labels.withTooltip("Draw Mode")
            contents.add(drawModeLabel, 0, row)
            GridPane.setColumnSpan(drawModeLabel, 2)
            contents.add(drawModeChoice, 2, row)
            GridPane.setColumnSpan(drawModeChoice, 2)
            GridPane.setHalignment(drawModeChoice, HPos.RIGHT)
            drawModeChoice.prefWidth = choiceWidth
            ++row

            val cullFaceLabel = Labels.withTooltip("Cull Face")
            contents.add(cullFaceLabel, 0, row)
            GridPane.setColumnSpan(cullFaceLabel, 2)
            contents.add(cullFaceChoice, 2, row)
            GridPane.setColumnSpan(cullFaceChoice, 2)
            GridPane.setHalignment(cullFaceChoice, HPos.RIGHT)
            cullFaceChoice.prefWidth = choiceWidth
            ++row

            return row
        }

		private fun makeReloadSymbol() = RefreshButton
				.createFontAwesome(scale = 2.0)
				.also { it.rotate = 45.0 }

    }

}
