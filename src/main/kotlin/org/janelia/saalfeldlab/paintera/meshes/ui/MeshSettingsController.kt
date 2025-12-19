package org.janelia.saalfeldlab.paintera.meshes.ui

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.ContentDisplay
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.util.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import net.imglib2.type.label.LabelMultisetType
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterObj
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManager
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.ui.dialogs.AnimatedProgressBarAlert
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportModel
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportModel.Companion.initFromProject
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshExportResult
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshProgressBar
import java.util.concurrent.CancellationException
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.min

private val LOG = KotlinLogging.logger { }

class MeshSettingsController(
	val numScaleLevels: Int,
	private val opacity: DoubleProperty,
	private val levelOfDetail: IntegerProperty,
	private val coarsestScaleLevel: IntegerProperty,
	private val finestScaleLevel: IntegerProperty,
	private val smoothingLambda: DoubleProperty,
	private val smoothingIterations: IntegerProperty,
	private val minLabelRatio: DoubleProperty,
	private val overlap: BooleanProperty,
	private val drawMode: Property<DrawMode>,
	private val cullFace: Property<CullFace>,
	private val isVisible: BooleanProperty,
	private val refreshMeshes: Runnable? = null,
) {

	constructor(meshSettings: MeshSettings, refreshMeshes: Runnable? = null) : this(
		meshSettings.numScaleLevels,
		meshSettings.opacityProperty,
		meshSettings.levelOfDetailProperty,
		meshSettings.coarsestScaleLevelProperty,
		meshSettings.finestScaleLevelProperty,
		meshSettings.smoothingLambdaProperty,
		meshSettings.smoothingIterationsProperty,
		meshSettings.minLabelRatioProperty,
		meshSettings.overlapProperty,
		meshSettings.drawModeProperty,
		meshSettings.cullFaceProperty,
		meshSettings.isVisibleProperty,
		refreshMeshes
	)


	fun createContents(addMinLabelRatioSlider: Boolean): GridPane {
		val minLabelRatioSlider: NumericSliderWithField? = if (addMinLabelRatioSlider) NumericSliderWithField(0.0, 1.0, 0.0) else null
		return GridPane().populateGridWithMeshSettings(
			CheckBox().also { it.selectedProperty().bindBidirectional(isVisible) },
			NumericSliderWithField(0.0, 1.0, opacity.value).also { it.valueProperty.bindBidirectional(opacity) },
			NumericSliderWithField(
				MeshSettings.Defaults.Values.minLevelOfDetail,
				MeshSettings.Defaults.Values.maxLevelOfDetail,
				levelOfDetail.value
			).also { it.valueProperty.bindBidirectional(levelOfDetail) },
			NumericSliderWithField(0, this.numScaleLevels - 1, coarsestScaleLevel.value).also {
				it.valueProperty.bindBidirectional(coarsestScaleLevel)
			},
			NumericSliderWithField(0, this.numScaleLevels - 1, finestScaleLevel.value).apply { valueProperty.bindBidirectional(finestScaleLevel) },
			NumericSliderWithField(0.0, 1.00, 1.0).apply { valueProperty.bindBidirectional(smoothingLambda) },
			NumericSliderWithField(0, 10, 1).apply { valueProperty.bindBidirectional(smoothingIterations) },
			minLabelRatioSlider?.apply { valueProperty.bindBidirectional(minLabelRatio) },
			CheckBox().also { it.selectedProperty().bindBidirectional(overlap) },
			ComboBox(FXCollections.observableArrayList(*DrawMode.entries.toTypedArray())).apply { valueProperty().bindBidirectional(drawMode) },
			ComboBox(FXCollections.observableArrayList(*CullFace.entries.toTypedArray())).apply { valueProperty().bindBidirectional(cullFace) })
	}

	fun createTitledPane(
		addMinLabelRatioSlider: Boolean,
		isEnabled: BooleanProperty,
		titledPaneGraphicsSettings: TitledPaneGraphicsSettings = TitledPaneGraphicsSettings(),
		withGridPane: GridPane.() -> Unit = {},
	): TitledPane {

		val contents = createContents(addMinLabelRatioSlider)
		withGridPane.invoke(contents) /* Used to add costume components to the GridPane */

		val tpGraphics = HBox(
			Label(titledPaneGraphicsSettings.labelText),
			NamedNode.bufferNode(),
			CheckBox().apply {
				selectedProperty().bindBidirectional(isEnabled)
				tooltip = Tooltip("Toggle meshes on/off")
			},
			Buttons.withTooltip(null, "Refresh Meshes") {
				refreshMeshes?.run()

			}.apply {
				addStyleClass(Style.REFRESH_ICON)
				isVisible = refreshMeshes != null
				isManaged = refreshMeshes != null
			}
		).apply {
			alignment = Pos.CENTER
		}


		return TitledPane("", contents).apply {
			minWidthProperty().set(0.0)
			isExpanded = false
			graphic = tpGraphics
			contentDisplay = ContentDisplay.GRAPHIC_ONLY
			alignment = Pos.CENTER_RIGHT
		}
	}

	data class TitledPaneGraphicsSettings(val labelText: String = "Mesh Settings")


	companion object {
		const val TEXT_FIELD_WIDTH = 48.0


		const val CHOICE_WIDTH = 95.0

		private fun GridPane.populateGridWithMeshSettings(
			visibleCheckBox: CheckBox,
			opacitySlider: NumericSliderWithField,
			levelOfDetailSlider: NumericSliderWithField,
			coarsestScaleLevelSlider: NumericSliderWithField,
			finestScaleLevelSlider: NumericSliderWithField,
			smoothingLambdaSlider: NumericSliderWithField,
			smoothingIterationsSlider: NumericSliderWithField,
			minLabelRatioSlider: NumericSliderWithField?,
			overlapToggle: CheckBox,
			drawModeChoice: ComboBox<DrawMode>,
			cullFaceChoice: ComboBox<CullFace>,
		): GridPane {

			setCoarsestAndFinestScaleLevelSliderListeners(
				coarsestScaleLevelSlider.valueProperty,
				finestScaleLevelSlider.valueProperty
			)

			val row = rowCount

			// arrange the grid as 4 columns to fine-tune size and layout of the elements
			columnConstraints.add(ColumnConstraints())
			columnConstraints.add(ColumnConstraints().also {
				it.maxWidth = Double.MAX_VALUE
				it.hgrow = Priority.ALWAYS
			})
			columnConstraints.add(ColumnConstraints())
			columnConstraints.add(ColumnConstraints(TEXT_FIELD_WIDTH))

			add(Labels.withTooltip("Visible"), 0, row)
			add(visibleCheckBox, 3, row)
			GridPane.setHalignment(visibleCheckBox, HPos.CENTER)

			addGridOption("Opacity", opacitySlider, "Mesh Opacity")
			addGridOption("Level of detail", levelOfDetailSlider, "Level Of Detail")
			addGridOption("Coarsest scale", coarsestScaleLevelSlider, "Coarsest Scale Level")
			addGridOption("Finest scale", finestScaleLevelSlider, "Finest Scale Level")
			addGridOption("Lambda", smoothingLambdaSlider, "Smoothing Lambda")
			addGridOption("Iterations", smoothingIterationsSlider, "Smoothing Iterations")

			// min label ratio slider only makes sense for sources of label multiset type
			if (minLabelRatioSlider != null) {
				val tooltipText = "Min label percentage for a pixel to be filled." + System.lineSeparator() +
						"0.0 means that a pixel will always be filled if it contains the given label."
				addGridOption("Min label ratio", minLabelRatioSlider, tooltipText)
			}

			addGridOption("Overlap", overlapToggle)
			addGridOption("Draw Mode", drawModeChoice)
			addGridOption("Cull Face", cullFaceChoice)
			return this
		}

		private fun GridPane.addGridOption(text: String, sliderWithField: NumericSliderWithField, tooltip: String?) {
			addGridOption(text, sliderWithField.slider, sliderWithField.textField)
			with(sliderWithField) {
				slider.isShowTickLabels = true
				tooltip?.let { slider.tooltip = Tooltip(it) }
				textField.prefWidth = TEXT_FIELD_WIDTH
				textField.maxWidth = Control.USE_PREF_SIZE
				GridPane.setHgrow(slider, Priority.ALWAYS)
			}
		}

		fun GridPane.addGridOption(text: String, node: Node, secondNode: Node? = null, tooltip: String? = null, width: Double? = null) {
			val label = Labels.withTooltip(text, tooltip ?: text)
			val row = rowCount

			add(label, 0, row)

			secondNode?.let {
				add(node, 1, row)
				add(secondNode, 3, row)
			} ?: let {
				add(node, 2, row)
				GridPane.setColumnSpan(label, 2)
				GridPane.setHalignment(node, HPos.RIGHT)
			}
			GridPane.setColumnSpan(node, 2)
			when (node) {
				is ChoiceBox<*> -> node.prefWidth = width ?: CHOICE_WIDTH
				is Region -> width?.let { node.prefWidth = it }
                else -> Unit
            }
		}

		private fun setCoarsestAndFinestScaleLevelSliderListeners(
			coarsestScaleLevelProperty: DoubleProperty,
			finestScaleLevelProperty: DoubleProperty,
		) {


			fun setCoarseAndFineRange() {
				val coarse = coarsestScaleLevelProperty.value
				val fine = finestScaleLevelProperty.value
				when {
					coarse.isNaN() && fine.isNaN() -> {
						coarsestScaleLevelProperty.value = 0.0
						finestScaleLevelProperty.value = 0.0
					}

					coarse.isNaN() -> {
						coarsestScaleLevelProperty.value = fine
						finestScaleLevelProperty.value = fine
					}

					fine.isNaN() -> {
						coarsestScaleLevelProperty.value = coarse
						finestScaleLevelProperty.value = coarse
					}

					else -> {
						coarsestScaleLevelProperty.value = max(coarse, fine)
						finestScaleLevelProperty.value = min(coarse, fine)
					}
				}
			}

			coarsestScaleLevelProperty.addListener { _ -> setCoarseAndFineRange() }
			finestScaleLevelProperty.addListener { _ -> setCoarseAndFineRange() }
		}
	}
}


open class MeshInfoPane<T>(internal val meshInfo: MeshInfo<T>) : TitledPane(null, null) {
	private val hasIndividualSettings = CheckBox("Individual Settings")
	private var isManagedProperty = SimpleBooleanProperty()
	private var controller: MeshSettingsController = MeshSettingsController(meshInfo.meshSettings)

	private var progressBar = MeshProgressBar()

	init {
		hasIndividualSettings.selectedProperty().bindBidirectional(isManagedProperty)
		isExpanded = false
		val meshKeyLabel = TextField("${meshInfo.key}").also {
			it.isEditable = false
			it.background = Background.EMPTY
			it.prefWidth = 10 * it.font.size
		}
		contentDisplay = ContentDisplay.GRAPHIC_ONLY
		graphic = HBox(
			meshKeyLabel, progressBar.hGrow()
		).also {
			it.alignment = Pos.CENTER
			it.padding = Insets(0.0, 30.0, 0.0, 10.0)
		}
	}

	internal fun initContent() {
		content = createMeshInfoGrid()
	}

	private var subscription: Subscription? = null

	internal fun unbindProgressBar() {
		subscription?.let {
			it.unsubscribe()
			subscription = null
		}
	}

	@Synchronized
	internal fun bindProgressBar() {
		unbindProgressBar()
		val meshProgress = SimpleDoubleProperty(ProgressIndicator.INDETERMINATE_PROGRESS)
		val meshStateSubscription = meshInfo.meshStateProperty.subscribe { state ->
			meshProgress.unbind()
			when (state) {
				null -> meshProgress.set(ProgressIndicator.INDETERMINATE_PROGRESS)
				else -> {
					meshProgress.value = state.progress.progressBinding.value
					meshProgress.bind(state.progress.progressBinding)
				}
			}
		}
		val progressBarSubscription = progressBar.bindTo(meshProgress).let {
			Subscription { progressBar.unbind() }
		}
		subscription += progressBarSubscription + meshStateSubscription
	}

	private val settingsNode by lazy {
		val isLabelMultiset = meshInfo.manager.source.getDataType() is LabelMultisetType
		controller.createContents(isLabelMultiset)
	}

	protected open fun createMeshInfoGrid(): Node {

		isManagedProperty.bindBidirectional(meshInfo.isManagedProperty)
		val individualSettingsBox = TitledPane()
		individualSettingsBox.addStyleClass("individual-mesh-settings")
		individualSettingsBox.isCollapsible = false
		individualSettingsBox.graphic = HBox(
			hasIndividualSettings,
			Region().apply {
				maxWidth = Double.MAX_VALUE
				HBox.setHgrow(this, Priority.ALWAYS)
			},
			createExportMeshButton()
		).also { titleRegion ->
			titleRegion.alignment = Pos.CENTER
			titleRegion.padding = Insets(0.0, 10.0, 0.0, 00.0)
		}
		hasIndividualSettings.selectedProperty().subscribe { individualSettings ->
			if (individualSettings && individualSettingsBox.content == null)
				individualSettingsBox.content = settingsNode
			individualSettingsBox.isCollapsible = true
			individualSettingsBox.isExpanded = individualSettings
			individualSettingsBox.isCollapsible = false
		}
		return individualSettingsBox
	}

	private fun createExportMeshButton(): Button {
		val exportMeshButton = Button("Export")
		exportMeshButton.setOnAction { event ->
			val model = MeshExportModel
				.fromMeshInfos(meshInfo)
				.initFromProject()
			val exportDialog = MeshExportDialog(model)
			val result = exportDialog.showAndWait()
			if (!result.isPresent) return@setOnAction

			val manager: MeshManager<T> = meshInfo.manager
			val parameters = result.get()
			val meshExporter = parameters.meshExporter
			val ids = parameters.meshKeys
			val filePath = parameters.filePath
			if (meshExporter is MeshExporterObj) {
				meshExporter.exportMaterial(
					filePath,
					arrayOf(ids[0].toString()),
					arrayOf(meshInfo.manager.getStateFor(ids[0])?.color ?: Color.WHITE)
				)
			}
			manager.exportMeshWithProgressPopup(parameters)

		}
		return exportMeshButton
	}

}
fun <T> MeshManager<T>.exportMeshWithProgressPopup(result: MeshExportResult<T>) {
	val meshExporter = result.meshExporter
	val ids = result.meshKeys
	if (ids.isEmpty()) return
	val (getBlocks, getMesh) = when (this) {
		is MeshManagerWithAssignmentForSegments -> getBlockListForSegment as GetBlockListFor<T> to getMeshForLongKey as GetMeshFor<T>
		else -> getBlockListFor to getMeshFor
	}
	val totalBlocks = ids.sumOf { getBlocks.getBlocksFor(result.scale, it).count() }

	val labelProp = SimpleStringProperty()
	val progressProp = SimpleDoubleProperty(0.0)

	val progressUpdater = AnimatedProgressBarAlert(
		"Export Mesh",
		"Exporting Mesh",
		labelProp,
		progressProp,
		cancellable = true
	)

	val uiSubscription = meshExporter.blocksProcessed.subscribe { count ->
		labelProp.set("Blocks processed: $count/$totalBlocks")
		progressProp.set(count.toDouble() / totalBlocks)
	}

	val exportJob = CoroutineScope(Dispatchers.IO).async {
		meshExporter.exportMesh(
			getBlocks,
			getMesh,
			ids.map { getSettings(it) }.toTypedArray(),
			ids,
			result.scale,
			result.filePath
		)
	}
	exportJob.invokeOnCompletion { cause ->
		uiSubscription.unsubscribe()
		cause?.let {
			if (it is CancellationException) {
				LOG.info { "Export Mesh Cancelled by User" }
				progressUpdater.stopAndClose()
				return@invokeOnCompletion
			}
			LOG.error(it) { "Error exporting meshes" }
			progressUpdater.stopAndClose()
			InvokeOnJavaFXApplicationThread {
				PainteraAlerts.alert(Alert.AlertType.ERROR, true).apply {
					contentText = "Error exporting meshes\n${it.message}"
				}.showAndWait()
			}
		} ?: progressUpdater.finish()
	}

	InvokeOnJavaFXApplicationThread {
		if (exportJob.isActive) {
			val buttonType = progressUpdater.showAndWait().getOrNull()
			if (buttonType == ButtonType.CANCEL || progressUpdater.cancelled)
				meshExporter.cancel()
		}

	}
}

abstract class MeshInfoList<T : MeshInfo<K>, K> : ListView<T>() {


	init {
		setCellFactory { MeshInfoListCell() }
	}

	abstract fun updateItems(meshesEnabled: Boolean)

	open fun meshNodeFactory(meshInfo: T): MeshInfoPane<K> {
		val meshInfoPane: MeshInfoPane<K> = MeshInfoPane(meshInfo)
		meshInfoPane.initContent()
		return meshInfoPane
	}

	private inner class MeshInfoListCell : ListCell<T>() {

		var meshInfoPane: MeshInfoPane<K>? = null

		init {
			style = "-fx-padding: 0px"
			sceneProperty().subscribe { scene ->
				if (scene == null) {
					releaseState()
				}
			}
		}

		private fun releaseState() {
			meshInfoPane?.apply {
				unbindProgressBar()
			}
			meshInfoPane = null
		}

		override fun updateItem(item: T?, empty: Boolean) {
			super.updateItem(item, empty)
			text = null
			if (empty || item == null) {
				graphic = null
				releaseState()
			} else {
				if (meshInfoPane == null || meshInfoPane?.meshInfo?.key != item.key) {
					releaseState()
					meshInfoPane = meshNodeFactory(item).also {
						it.bindProgressBar()
					}
				}
				graphic = meshInfoPane
			}
		}
	}
}
