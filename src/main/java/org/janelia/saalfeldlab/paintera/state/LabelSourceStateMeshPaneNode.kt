package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.Property
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.stage.Modality
import net.imglib2.type.label.LabelMultisetType
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.*
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsNode
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshExporterDialog
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshInfoNode
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshProgressBar
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min

typealias TPE = TitledPaneExtensions

class LabelSourceStateMeshPaneNode(
        private val source: DataSource<*, *>,
        private val manager: MeshManagerWithAssignmentForSegments,
        private val meshInfos: MeshInfos) {

	val node: Node
		get() = makeNode()

	private fun makeNode(): Node {
        val settings = manager.settings
        val tp = MeshSettingsNode(
            settings,
            Runnable { manager.refreshMeshes() }).createTitledPane(
            source.dataType is LabelMultisetType,
            manager.rendererSettings.meshesEnabledProperty(),
            titledPaneGraphicsSettings = MeshSettingsNode.TitledPaneGraphicsSettings("Meshes"),
            helpDialogSettings = MeshSettingsNode.HelpDialogSettings(headerText = "Meshes"))
        val contents = tp.content.asVBox()
            .also { tp.content = it }
            .also { it.children.add(MeshesList(source, manager, meshInfos).node) }
        return tp
    }

	private class MeshesList(
        private val source: DataSource<*, *>,
        private val manager: PainteraMeshManager<Long>,
        private val meshInfos: MeshInfos) {

		private class Listener(
            private val source: DataSource<*, *>,
            private val manager: PainteraMeshManager<Long>,
            private val meshInfos: MeshInfos,
            private val meshesBox: Pane,
            private val isMeshListEnabledCheckBox: CheckBox,
            private val totalProgressBar: MeshProgressBar): ListChangeListener<MeshInfo> {

			val infoNodesCache = FXCollections.observableHashMap<MeshInfo, MeshInfoNode>()
			val infoNodes = FXCollections.observableArrayList<MeshInfoNode>()

			override fun onChanged(change: ListChangeListener.Change<out MeshInfo>) {
				while (change.next())
					if (change.wasRemoved())
						change.removed.forEach { infoNodesCache.remove(it) }

				if (isMeshListEnabledCheckBox.isSelected)
					populateInfoNodes()

				updateTotalProgressBindings()
			}

			private fun populateInfoNodes() {
				val infoNodes = this.meshInfos.readOnlyInfos().map { MeshInfoNode(source, it) }
				LOG.debug("Setting info nodes: {}: ", infoNodes)
				this.infoNodes.setAll(infoNodes)
				val exportMeshButton = Button("Export all")
				exportMeshButton.setOnAction { _ ->
					val exportDialog = MeshExporterDialog<Long>(meshInfos)
					val result = exportDialog.showAndWait()
					if (result.isPresent) {
						val parameters = result.get()

                        // TODO
//						val blockListCaches = Array(meshInfos.readOnlyInfos().size) { manager.blockListCache() }
//						val meshCaches = Array(blockListCaches.size) { manager.meshCache() }

//						parameters.meshExporter.exportMesh(
//								blockListCaches,
//								meshCaches,
//								parameters.segmentId.map { manager.unmodifiableMeshMap()[it]?.id }.toTypedArray(),
//								parameters.scale,
//								parameters.filePaths)
					}
				}

				InvokeOnJavaFXApplicationThread.invoke {
					this.meshesBox.children.setAll(infoNodes.map { it.get() })
					this.meshesBox.children.add(exportMeshButton)
				}
			}

			private fun updateTotalProgressBindings() {
				val infos = this.meshInfos.readOnlyInfos()
				val individualProgresses = infos.stream().map { it.meshProgress() }.filter { Objects.nonNull(it) }.collect(Collectors.toList())
				val globalProgress = GlobalMeshProgress(individualProgresses)
				this.totalProgressBar.bindTo(globalProgress)
			}
		}

		val node: Node
			get() = createNode()

		private val isMeshListEnabledCheckBox = CheckBox()
		private val totalProgressBar = MeshProgressBar()

		private fun createNode(): TitledPane {

			val meshesBox = VBox()

			isMeshListEnabledCheckBox.also { it.selectedProperty().bindBidirectional(meshInfos.meshSettings().isMeshListEnabledProperty) }

			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = "Mesh List." }
					.also { it.contentText = "TODO" }

			val tpGraphics = HBox(10.0,
					Label("Mesh List"),
					totalProgressBar.also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.text = "" },
					isMeshListEnabledCheckBox,
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER_LEFT }
					.also { it.isFillHeight = true }

			meshInfos.readOnlyInfos().addListener(Listener(
					source,
					manager,
					meshInfos,
					meshesBox,
					isMeshListEnabledCheckBox,
					totalProgressBar))

			return TitledPane("Mesh List", meshesBox)
					.also { with(TPE) { it.expandIfEnabled(isMeshListEnabledCheckBox.selectedProperty()) } }
					.also { with(TPE) { it.graphicsOnly(tpGraphics)} }
					.also { it.alignment = Pos.CENTER_RIGHT }
		}

	}

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		fun populateGridWithMeshSettings(
				addMinLabelratioSlider: Boolean,
				contents: GridPane,
				initialRow: Int,
                visibleCheckBox: CheckBox,
				opacitySlider: NumericSliderWithField,
				levelOfDetailSlider: NumericSliderWithField,
				coarsestScaleLevelSlider: NumericSliderWithField,
				finestScaleLevelSlider: NumericSliderWithField,
				smoothingLambdaSlider: NumericSliderWithField,
				smoothingIterationsSlider: NumericSliderWithField,
				minLabelRatioSlider: NumericSliderWithField,
				inflateSlider: NumericSliderWithField,
				drawModeChoice: ComboBox<DrawMode>,
				cullFaceChoice: ComboBox<CullFace>): Int {

			setCoarsestAndFinestScaleLevelSliderListeners(
					coarsestScaleLevelSlider.slider,
					finestScaleLevelSlider.slider)

			var row = initialRow

			val textFieldWidth = 48.0
			val choiceWidth = 95.0

			// arrange the grid as 4 columns to fine-tune size and layout of the elements
			for (i in 0..2)
				contents.columnConstraints.add(ColumnConstraints())
			contents.columnConstraints.add(ColumnConstraints(textFieldWidth))

			val setupSlider = { slider: NumericSliderWithField, ttText: String ->
				slider
						.also { it.slider.isShowTickLabels = true }
						.also { it.slider.tooltip = Tooltip(ttText) }
						.also { it.textField.prefWidth = textFieldWidth }
						.also { it.textField.maxWidth = Control.USE_PREF_SIZE }
						.also { GridPane.setHgrow(it.slider, Priority.ALWAYS) }
			}

            contents.add(Labels.withTooltip("Visible"), 0, row)
            contents.add(visibleCheckBox, 3, row)
            GridPane.setHalignment(visibleCheckBox, HPos.CENTER)
            ++row

			contents.add(Labels.withTooltip("Opacity"), 0, row)
			contents.add(opacitySlider.slider, 1, row)
			GridPane.setColumnSpan(opacitySlider.slider, 2)
			contents.add(opacitySlider.textField, 3, row)
			setupSlider(opacitySlider, "Mesh Opacity")
			++row

			contents.add(Labels.withTooltip("Level of detail"), 0, row)
			contents.add(levelOfDetailSlider.slider, 1, row)
			GridPane.setColumnSpan(levelOfDetailSlider.slider, 2)
			contents.add(levelOfDetailSlider.textField, 3, row)
			setupSlider(levelOfDetailSlider, "Level Of Detail")
			++row

			contents.add(Labels.withTooltip("Coarsest scale"), 0, row)
			contents.add(coarsestScaleLevelSlider.slider, 1, row)
			GridPane.setColumnSpan(coarsestScaleLevelSlider.slider, 2)
			contents.add(coarsestScaleLevelSlider.textField, 3, row)
			setupSlider(coarsestScaleLevelSlider, "Coarsest Scale Level")
			++row

			contents.add(Labels.withTooltip("Finest scale"), 0, row)
			contents.add(finestScaleLevelSlider.slider, 1, row)
			GridPane.setColumnSpan(finestScaleLevelSlider.slider, 2)
			contents.add(finestScaleLevelSlider.textField, 3, row)
			setupSlider(finestScaleLevelSlider, "Finest Scale Level")
			++row

			contents.add(Labels.withTooltip("Lambda"), 0, row)
			contents.add(smoothingLambdaSlider.slider, 1, row)
			GridPane.setColumnSpan(smoothingLambdaSlider.slider, 2)
			contents.add(smoothingLambdaSlider.textField, 3, row)
			setupSlider(smoothingLambdaSlider, "Smoothing Lambda")
			++row

			contents.add(Labels.withTooltip("Iterations"), 0, row)
			contents.add(smoothingIterationsSlider.slider, 1, row)
			GridPane.setColumnSpan(smoothingIterationsSlider.slider, 2)
			contents.add(smoothingIterationsSlider.textField, 3, row)
			setupSlider(smoothingIterationsSlider, "Smoothing Iterations")
			++row

			// min label ratio slider only makes sense for sources of label multiset type
			if (addMinLabelratioSlider)
			{
				contents.add(Labels.withTooltip("Min label ratio"), 0, row)
				contents.add(minLabelRatioSlider.slider, 1, row)
				GridPane.setColumnSpan(minLabelRatioSlider.slider, 2)
				contents.add(minLabelRatioSlider.textField, 3, row)
				setupSlider(minLabelRatioSlider, "Min label percentage for a pixel to be filled." + System.lineSeparator() +
						"0.0 means that a pixel will always be filled if it contains the given label.")
				++row
			}

			contents.add(Labels.withTooltip("Inflate"), 0, row)
			contents.add(inflateSlider.slider, 1, row)
			GridPane.setColumnSpan(inflateSlider.slider, 2)
			contents.add(inflateSlider.textField, 3, row)
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

		private fun setCoarsestAndFinestScaleLevelSliderListeners(
				coarsestScaleLevelSlider: Slider,
				finestScaleLevelSlider: Slider) {

			coarsestScaleLevelSlider.valueProperty().addListener { _ ->
				finestScaleLevelSlider.value = min(
						coarsestScaleLevelSlider.value,
						finestScaleLevelSlider.value)
			}

			finestScaleLevelSlider.valueProperty().addListener { _ ->
				coarsestScaleLevelSlider.value = max(
						coarsestScaleLevelSlider.value,
						finestScaleLevelSlider.value)
			}
		}

		private fun makeReloadSymbol() = RefreshButton
				.createFontAwesome(scale = 2.0)
				.also { it.rotate = 45.0 }

        private fun Node.asVBox() = if (this is VBox) this else VBox(this)

    }

}
