package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.*
import javafx.scene.control.ButtonType.CANCEL
import javafx.scene.control.ButtonType.OK
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.controlsfx.control.CheckListView
import org.janelia.saalfeldlab.fx.ui.DirectoryField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.meshes.MeshExporter
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterBinary
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterObj
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.dialogs.MeshExportModel.Default
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshExportResult
import org.janelia.saalfeldlab.paintera.ui.vGrow
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.exists

enum class MeshFileFormat {
	Obj,
	Binary;
}

interface MeshExportModel<K> {
	val getMeshSettings : (K) -> MeshSettings

	val meshExporter: Property<MeshExporter<K>>
	val fileFormat: Property<MeshFileFormat>
	val outputDirectory: Property<File>
	val meshFileName: Property<String>
	val scaleLevels: ObservableList<Int>
	val scaleLevel: Property<Int>
	val possibleMeshSelections: ObservableList<K>
	val selectedMeshes: ObservableList<K>
	val hasError: BooleanExpression

	fun resolveFilePath(): String {
		return outputDirectory.value.resolve(meshFileName.value).path.replace("~", System.getProperty("user.home"))
	}


	fun getMeshExportResult() = MeshExportResult<K>(
		meshExporter.value,
		resolveFilePath(),
		scaleLevel.value,
		selectedMeshes.toList(),
	)

	class Default<K>(override val getMeshSettings : (K) -> MeshSettings) : MeshExportModel<K> {
		override val fileFormat = SimpleObjectProperty<MeshFileFormat>(MeshFileFormat.Obj)
		override val meshExporter = SimpleObjectProperty<MeshExporter<K>>().apply {
			val exportBinding = fileFormat.map { format ->
				when (format) {
					MeshFileFormat.Obj -> MeshExporterObj<K>()
					MeshFileFormat.Binary -> MeshExporterBinary<K>()
				}
			}
			bind(exportBinding)
		}
		override val meshFileName = SimpleStringProperty()

		override val outputDirectory = SimpleObjectProperty<File>()

		override val scaleLevels: ObservableList<Int> = FXCollections.observableList<Int>(mutableListOf())
		override val scaleLevel = SimpleObjectProperty<Int>().also { scaleLevel ->
			scaleLevels.subscribe { scaleLevel.set(scaleLevels.minOrNull()) }
		}
		override val possibleMeshSelections: ObservableList<K> = FXCollections.observableList<K>(mutableListOf()).apply {
			subscribe {
				val distinctScaleSettings = distinctBy { key -> getMeshSettings(key).let { it.finestScaleLevel to it.coarsestScaleLevel } }
					.map { key -> getMeshSettings(key).let { it.finestScaleLevel to it.coarsestScaleLevel } }
					.toList()

				val finestScale = distinctScaleSettings.minBy { it.first }.first
				val coarsestScale = distinctScaleSettings.minBy { it.second }.second

				scaleLevels.setAll((finestScale..coarsestScale).toList())
			}
		}

		override val selectedMeshes: ObservableList<K> = FXCollections.observableList(mutableListOf())

		override val hasError: BooleanBinding = Bindings.createBooleanBinding({
			outputDirectory.value == null
					|| meshFileName.value.isNullOrEmpty()
					|| scaleLevel.value == null
					|| selectedMeshes.isEmpty()
					|| Paths.get(resolveFilePath()).exists()
		}, outputDirectory, meshFileName, scaleLevel, meshExporter, selectedMeshes)
	}

	companion object {

		@JvmStatic
		fun <K> fromMeshInfos(vararg meshInfos: MeshInfo<K>): Default<K> {
			val map = meshInfos.associateBy { it.key }
			val model = Default<K> { map[it]!!.meshSettings }
			model.possibleMeshSelections.setAll(map.keys)

			if (meshInfos.size == 1)
				model.meshFileName.set(meshInfos[0].key.toString())

			return model
		}

		@JvmStatic
		fun <K> MeshExportModel<K>.initFromProject() = apply {
			outputDirectory.value = paintera.projectDirectory.actualDirectory
		}
	}
}

private class MeshExportUI<K>(val model: MeshExportModel<K>) : VBox() {

	private val scaleSpinner = Spinner<Int>().apply {
		valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(Integer.MIN_VALUE, Integer.MAX_VALUE).apply {
			val minScaleBinding = Bindings.createIntegerBinding({ model.scaleLevels.min() }, model.scaleLevels)
			minProperty().bind(minScaleBinding)

			val maxScaleBinding = Bindings.createIntegerBinding({ model.scaleLevels.max() }, model.scaleLevels)
			maxProperty().bind(maxScaleBinding)

			valueProperty().bindBidirectional(model.scaleLevel)
		} as SpinnerValueFactory<Int>
		maxWidth = Double.POSITIVE_INFINITY
		isEditable = true
	}

	private val formatComboBox = ComboBox<MeshFileFormat>(FXCollections.observableList(MeshFileFormat.entries)).apply {
		valueProperty().bindBidirectional(model.fileFormat)
		selectionModel.select(value)
		maxWidth = Double.POSITIVE_INFINITY
	}

	private val meshSelectionList = CheckListView<K>().apply {
		itemsProperty().set(model.possibleMeshSelections)
		checkModel.checkedItems.subscribe {
			model.selectedMeshes.setAll(checkModel.checkedItems)
		}
	}

	private val filePathField = DirectoryField(model.outputDirectory.value, USE_COMPUTED_SIZE).apply {
		model.outputDirectory.bind(directoryProperty())
	}

	init {


		children += CheckBox("Select all").apply {
			selectedProperty().set(true)
			selectedProperty().subscribe { selected ->
				when {
					selected -> meshSelectionList.checkModel.checkAll()
					else -> meshSelectionList.checkModel.clearChecks()
				}
			}
		}

		children += meshSelectionList

		children += GridPane().vGrow {
			isFillWidth = true
			var row = 0

			columnConstraints.add(ColumnConstraints().apply {
				hgrow = Priority.NEVER
			})
			add(Label("Scale "), 0, row)
			add(scaleSpinner, 1, row++)
			GridPane.setFillWidth(scaleSpinner, true)
			GridPane.setHgrow(scaleSpinner, Priority.ALWAYS)

			add(Label("Format "), 0, row)
			add(formatComboBox, 1, row++)
			GridPane.setFillWidth(formatComboBox, true)
			GridPane.setHgrow(formatComboBox, Priority.ALWAYS)

			val exportFileName = TextField().apply {
				model.meshFileName.bind(textProperty())
			}
			add(Label("Name "), 0, row)
			add(exportFileName, 1, row++)
			GridPane.setFillWidth(exportFileName, true)
			GridPane.setHgrow(exportFileName, Priority.ALWAYS)

			val filePathNode = filePathField.asNode()
			add(Label("Save to "), 0, row)
			add(filePathNode, 1, row++)
			GridPane.setFillWidth(filePathNode, true)
			GridPane.setHgrow(filePathNode, Priority.ALWAYS)
		}
	}
}

class MeshExportDialog<K>(model: MeshExportModel<K>) : Dialog<MeshExportResult<K>>() {

	companion object {

		private var previousFilePath: File? = null
	}

	init {
		previousFilePath?.let {
			model.outputDirectory.value = previousFilePath
		}

		title = "Export Meshes"
		dialogPane.content = MeshExportUI(model)
		dialogPane.buttonTypes += listOf(CANCEL, OK)
		dialogPane.lookupButton(OK).disableProperty().bind(model.hasError)
		initAppDialog()
		isResizable = true
		setResultConverter { btn ->
			if (btn.buttonData.isCancelButton)
				return@setResultConverter null

			previousFilePath = model.outputDirectory.value
			model.getMeshExportResult()
		}
	}

}

fun main() {
	val model = Default<String>{ MeshSettings((3..6).random()) }.apply {
		outputDirectory.value = Paths.get(System.getProperty("user.home"), "Meshes").toFile()
		scaleLevels.addAll(1, 2, 3, 4)
		possibleMeshSelections.addAll((0..100).map { "Mesh $it" })
	}
	InvokeOnJavaFXApplicationThread {
		val dialog = MeshExportDialog<String>(model)
		dialog.dialogPane.content = MeshExportUI(model)
		dialog.showAndWait()
	}
}