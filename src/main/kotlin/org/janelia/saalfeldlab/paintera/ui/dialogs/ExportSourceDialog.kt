package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyEvent
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import javafx.util.StringConverter
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys.namedCombinationsCopy
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.actions.ExportSourceState
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems
import org.janelia.saalfeldlab.util.n5.N5Helpers.MAX_ID_KEY

object ExportSourceDialog {

	private const val DIALOG_HEADER = "Export Source"
	private const val CANCEL_LABEL = "_Cancel"
	private const val EXPORT = "Export"
	private val acceptableDataTypes = DataType.entries.filter { it < DataType.FLOAT32 }.toTypedArray()


	fun askAndExport() {
		if (getValidExportSources().isEmpty())
			return

		val state = ExportSourceState()
		if (newDialog(state).showAndWait().nullable == ButtonType.OK) {
			state.exportSource(true)
		}
	}

	/**
	 * Picks the smallest data type that can hold all values less than or equal to maxId value.
	 *
	 * @param maxId the maximum ID value
	 * @return the smallest data type that can contain up to maxId
	 */
	private fun pickSmallestDataType(maxId: Long): DataType {

		if (maxId < 0) return DataType.UINT64

		val smallestType = acceptableDataTypes
			.asSequence()
			.map { it to N5Utils.type(it) }
			.filterIsInstance<Pair<DataType, RealType<*>>>()
			.filter { maxId < it.second.maxValue }
			.map { it.first }
			.firstOrNull() ?: DataType.UINT64

		return smallestType
	}

	private fun newDialog(state: ExportSourceState): Alert = PainteraAlerts.confirmation(EXPORT, CANCEL_LABEL).apply {
		val choiceBox = createSourceChoiceBox().apply {
			maxWidth = Double.MAX_VALUE
		}

		choiceBox.selectionModel.selectedItemProperty().subscribe { sourceState ->

			val backend = sourceState.backend as? SourceStateBackendN5<*, *>
			backend?.metadataState?.apply {
				state.maxIdProperty.value = reader[dataset, MAX_ID_KEY] ?: -1
			}
		}

		state.sourceStateProperty.bind(choiceBox.selectionModel.selectedItemProperty())

		headerText = DIALOG_HEADER
		dialogPane.content = GridPane().apply {
			columnConstraints.addAll(
				ColumnConstraints().apply { halignment = HPos.RIGHT },
				ColumnConstraints().apply {
					hgrow = Priority.NEVER
					minWidth = 10.0
				},
				ColumnConstraints().apply {
					halignment = HPos.RIGHT
					isFillWidth = true
					hgrow = Priority.ALWAYS
				},
				ColumnConstraints().apply { hgrow = Priority.NEVER }
			)

			add(Label("Source").apply { alignment = Pos.CENTER_LEFT }, 0, 0)
			add(choiceBox, 2, 0, GridPane.REMAINING, 1)

			add(Label("Export Container").apply { alignment = Pos.CENTER_LEFT }, 0, 1)
			val containerPathField = TextField().apply {
				promptText = "Enter the container path"
				maxWidth = Double.MAX_VALUE
				state.exportLocationProperty.bind(textProperty())
			}
			add(containerPathField, 2, 1, 2, 1)
			add(Button("Browse").apply {
				setOnAction {
					val directoryChooser = DirectoryChooser()
					val selectedDirectory = directoryChooser.showDialog(null)
					selectedDirectory?.let {
						containerPathField.text = it.absolutePath
					}
				}
			}, 3, 1)

			add(Label("Dataset").apply { alignment = Pos.CENTER_LEFT }, 0, 2)
			add(TextField().apply {
				promptText = "Enter the dataset name"
				maxWidth = Double.MAX_VALUE
				state.datasetProperty.bind(textProperty())
			}, 2, 2, GridPane.REMAINING, 1)


			val smallOptions = GridPane().apply {
				columnConstraints.addAll(
					ColumnConstraints().apply { halignment = HPos.RIGHT },
					ColumnConstraints().apply {
						hgrow = Priority.NEVER
						minWidth = 10.0
					},
					ColumnConstraints().apply { halignment = HPos.LEFT },

					ColumnConstraints().apply {
						halignment = HPos.CENTER
						isFillWidth = true
						hgrow = Priority.ALWAYS
					},

					ColumnConstraints().apply { halignment = HPos.RIGHT },
					ColumnConstraints().apply {
						hgrow = Priority.NEVER
						minWidth = 10.0
					},
					ColumnConstraints().apply { halignment = HPos.LEFT },
				)
			}


			var prevScaleLevels: ObservableList<Int>? = null
			val scaleLevelsBinding = choiceBox.selectionModel.selectedItemProperty().createNonNullValueBinding { state ->
				val numMipmapLevels = state.dataSource.numMipmapLevels
				if (prevScaleLevels != null && prevScaleLevels!!.size == numMipmapLevels)
					return@createNonNullValueBinding prevScaleLevels

				FXCollections.observableArrayList<Int>().also {
					for (i in 0 until numMipmapLevels) {
						it.add(i)
					}
					prevScaleLevels = it
				}
			}
			val dataTypeChoices = ChoiceBox(FXCollections.observableArrayList(*acceptableDataTypes)).apply {
				converter = object : StringConverter<DataType>() {
					override fun toString(`object`: DataType?): String = `object`?.name?.uppercase() ?: "Select a Data Type..."
					override fun fromString(string: String?) = DataType.fromString(string?.lowercase())
				}
			}

			state.maxIdProperty.subscribe { maxId -> dataTypeChoices.selectionModel.select(pickSmallestDataType(maxId.toLong())) }
			state.dataTypeProperty.bind(dataTypeChoices.selectionModel.selectedItemProperty())

			smallOptions.add(Label("Map Fragment to Segment ID").apply { alignment = Pos.BOTTOM_RIGHT }, 0, 0)
			smallOptions.add(CheckBox().apply {
				state.segmentFragmentMappingProperty.bind(selectedProperty())
				selectedProperty().set(true)
				alignment = Pos.CENTER_LEFT
			}, 2, 0)
			smallOptions.add(Label("Scale Level").apply { alignment = Pos.BOTTOM_RIGHT }, 4, 0)
			smallOptions.add(ChoiceBox<Int>().apply {
				alignment = Pos.CENTER_LEFT
				itemsProperty().bind(scaleLevelsBinding)
				itemsProperty().subscribe { _ -> selectionModel.selectFirst() }
				state.scaleLevelProperty.bind(valueProperty())
			}, 6, 0)
			smallOptions.add(Label("Data Type").apply { alignment = Pos.BOTTOM_RIGHT }, 4, 1)
			smallOptions.add(dataTypeChoices, 6, 1)

			add(smallOptions, 0, 3, GridPane.REMAINING, 1)
		}
	}

	private fun createSourceChoiceBox(): ChoiceBox<ConnectomicsLabelState<*, *>> {
		val choices = getValidExportSources()

		return ChoiceBox(choices).apply {
			val curChoiceIdx = choices.indexOfFirst { it == paintera.baseView.sourceInfo().currentState() }
			if (curChoiceIdx != -1)
				selectionModel.select(curChoiceIdx)
			else
				selectionModel.selectFirst()
			maxWidth = Double.MAX_VALUE
			converter = object : StringConverter<ConnectomicsLabelState<*, *>>() {
				override fun toString(`object`: ConnectomicsLabelState<*, *>?): String = `object`?.nameProperty()?.get() ?: "Select a Source..."
				override fun fromString(string: String?) = choices.first { it.nameProperty().get() == string }
			}
		}
	}

	internal fun getValidExportSources(): ObservableList<ConnectomicsLabelState<*, *>> {
		return paintera.baseView.sourceInfo().trackSources()
			.asSequence()
			.filterIsInstance<DataSource<*, *>>()
			.mapNotNull { source -> (paintera.baseView.sourceInfo().getState(source) as? ConnectomicsLabelState<*, *>) }
			.toCollection(FXCollections.observableArrayList())
	}

	fun exportSourceDialogAction(
		baseView: PainteraBaseView,
	) = painteraActionSet("export-source", MenuActionType.ExportSource) {
		KeyEvent.KEY_PRESSED(namedCombinationsCopy(), PainteraBaseKeys.EXPORT_SOURCE) {
			verify { getValidExportSources().isNotEmpty() }
			onAction { PainteraMenuItems.EXPORT_SOURCE.menu.fire() }
			handleException {
				val scene = baseView.viewer3D().scene.scene
				exceptionAlert(
					Constants.NAME,
					"Error showing export source menu",
					it,
					null,
					scene?.window
				)
			}
		}
	}

}

