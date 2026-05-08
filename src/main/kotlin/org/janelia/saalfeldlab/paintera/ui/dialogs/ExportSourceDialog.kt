package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.binding.ObjectBinding
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyEvent
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import javafx.util.StringConverter
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.set
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton
import org.janelia.saalfeldlab.n5.Compression
import org.janelia.saalfeldlab.n5.Compression.CompressionType
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys.namedCombinationsCopy
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.actions.ExportSourceState
import org.janelia.saalfeldlab.paintera.control.actions.ExportSourceState.Companion.COMPRESSION_INDEX
import org.janelia.saalfeldlab.paintera.control.actions.ExportSourceState.Companion.DEFAULT_COMPRESSION
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.n5.N5Helpers.MAX_ID_KEY
import org.janelia.scicomp.n5.zstandard.ZstandardCompression
import kotlin.jvm.optionals.getOrNull

object ExportSourceDialog {

	private const val DIALOG_HEADER = "Export Source"
	private const val CANCEL_LABEL = "_Cancel"
	private const val EXPORT = "Export"
	private val acceptableDataTypes = DataType.entries.filter { it < DataType.FLOAT32 }.toTypedArray()


	fun askAndExport() {
		if (getValidExportSources().isEmpty())
			return

		val state = ExportSourceState()
		when (newDialog(state).showAndWait().getOrNull()) {
			ButtonType.OK -> state.exportSource(true)
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
        val choiceBox = sourceChoiceNode().apply {
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
				state.exportLocationProperty.bindBidirectional(textProperty())
			}
            add(containerPathField, 2, 1, 1, 1)
			add(HBox().apply {
				children += Button("Browse").apply {
					setOnAction {
						val directoryChooser = DirectoryChooser()
						val selectedDirectory = directoryChooser.showDialog(null)
						selectedDirectory?.let {
							containerPathField.text = it.absolutePath
						}
					}
				}
				PainteraCache.RECENT_EXPORT_LOCATIONS.readLines().reversed().takeIf { it.isNotEmpty() }?.let { recentExports ->
					containerPathField.text = recentExports.firstOrNull()
					containerPathField.prefColumnCount *= 2
					val recentMatcher = MatchSelectionMenuButton(recentExports, "_Recent") {
						containerPathField.text = it
					}
					children += recentMatcher
				}
			}, 3, 1)

			add(Label("Dataset").apply { alignment = Pos.CENTER_LEFT }, 0, 2)
			add(TextField().apply {
				promptText = "Enter the dataset name"
				maxWidth = Double.MAX_VALUE
				state.datasetProperty.bind(textProperty())
			}, 2, 2, GridPane.REMAINING, 1)


			val smallOptions = GridPane().apply {
                padding = Insets(5.0, 0.0, 5.0, 0.0)

                hgap = 10.0
                vgap = 5.0
				columnConstraints.addAll(
					ColumnConstraints().apply { halignment = HPos.RIGHT },
                    ColumnConstraints().apply { halignment = HPos.LEFT },

                    ColumnConstraints().apply { halignment = HPos.RIGHT },
					ColumnConstraints().apply { halignment = HPos.LEFT },


					ColumnConstraints().apply { halignment = HPos.RIGHT },
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
            //@formatter:off
            val configNodes : List<List<ConfigNode?>> = listOf(
                listOf( SegmentFragmentMappingConfig(state), StorageFormatConfig(state), ScaleLevelConfig(scaleLevelsBinding, state) ),
                listOf(                                null,   CompressionConfig(state),                       DataTypeConfig(state) )
            )
            //@formatter:on

            configNodes.forEachIndexed { row, nodes ->
                nodes.forEachIndexed { colIdx, configNode ->
                    configNode?.apply {
                        val column = colIdx*2
                        smallOptions[column, row] = label
                        smallOptions[column + 1, row] = config
                    }
                }
            }



            object : StringConverter<StorageFormat?>() {
                override fun toString(`object`: StorageFormat?) = `object`?.name?.uppercase() ?: "Default"

                override fun fromString(string: String?): StorageFormat? {
                    string ?: return null
                    return runCatching { StorageFormat.valueOf(string.uppercase()) }.getOrNull()
                }
            }

            add(smallOptions, 0, 3, GridPane.REMAINING, 1)
        }
    }

    private interface ConfigNode {
        val label: Label
        val config: Node
    }

    private data class DataTypeConfig(override val label: Label, override val config: ChoiceBox<DataType>) : ConfigNode {
        constructor(state: ExportSourceState) : this(
            Label("Data Type").apply { alignment = Pos.BOTTOM_RIGHT },
            ChoiceBox(FXCollections.observableArrayList(*acceptableDataTypes)).apply {
                converter = DataTypeConverter
                state.maxIdProperty.subscribe { maxId -> selectionModel.select(pickSmallestDataType(maxId.toLong())) }
                state.dataTypeProperty.bind(selectionModel.selectedItemProperty())
            }
        )



        companion object DataTypeConverter : StringConverter<DataType>() {
					override fun toString(`object`: DataType?): String = `object`?.name?.uppercase() ?: "Select a Data Type..."
					override fun fromString(string: String?) = DataType.fromString(string?.lowercase())
				}
			}

    private data class CompressionConfig(override val label: Label, override val config: ChoiceBox<CompressionType>) :
        ConfigNode {
        constructor(state: ExportSourceState) : this(
            Label("Compression").apply { alignment = Pos.BOTTOM_RIGHT },
            ChoiceBox<CompressionType>().apply {
                GridPane.setFillWidth(this, true)
                maxWidth = Double.MAX_VALUE
                converter = CompressionTypeConverter
                itemsProperty().get().setAll(*COMPRESSION_INDEX.map { it.annotation() }.toTypedArray())
                selectionModel.select(DEFAULT_COMPRESSION.annotation())
                selectionModel.selectedItemProperty().subscribe { compressionType ->
                    val compression = runCatching {
                        COMPRESSION_INDEX.first { it.annotation() == compressionType }
                            .className().let { cls ->
                                Class.forName(cls)
                                    .asSubclass(Compression::class.java)
                                    .getDeclaredConstructor()
                                    .newInstance()
                            }
                    }.getOrNull() ?: ZstandardCompression()
                    state.compressionProperty.set(compression)

                }
            }
        )

        companion object CompressionTypeConverter : StringConverter<CompressionType>() {
            override fun toString(`object`: CompressionType) = `object`.value
            override fun fromString(string: String) =
                COMPRESSION_INDEX.first { it.annotation()!!.value == string }.annotation()
        }
    }


    private data class ScaleLevelConfig(override val label: Label, override val config: ChoiceBox<Int>) : ConfigNode {
        constructor(scaleLevelsBinding: ObjectBinding<ObservableList<Int>?>, state: ExportSourceState) : this(
            Label("Scale Level").apply { alignment = Pos.BOTTOM_RIGHT },
            ChoiceBox<Int>().apply {
                GridPane.setFillWidth(this, true)
                maxWidth = Double.MAX_VALUE
				itemsProperty().bind(scaleLevelsBinding)
				itemsProperty().subscribe { _ -> selectionModel.selectFirst() }
				state.scaleLevelProperty.bind(valueProperty())
            }
        )
    }

    private data class StorageFormatConfig(override val label: Label, override val config: ChoiceBox<StorageFormat?>) :
        ConfigNode {

        constructor(state: ExportSourceState) : this(

            Label("Storage Format").apply { alignment = Pos.BOTTOM_RIGHT },

            ChoiceBox<StorageFormat?>().apply {
                GridPane.setFillWidth(this, true)
                maxWidth = Double.MAX_VALUE
                converter = StorageFormatConverter
                itemsProperty().get().setAll(null, *StorageFormat.entries.toTypedArray())
                selectionModel.selectFirst()
                selectionModel.selectedItemProperty().subscribe { format -> state.storageFormatProperty.set(format) }
            }
        )

        companion object StorageFormatConverter : StringConverter<StorageFormat?>() {
            override fun toString(`object`: StorageFormat?) = `object`?.name?.uppercase() ?: "Default"

            override fun fromString(string: String?): StorageFormat? {
                string ?: return null
                return runCatching { StorageFormat.valueOf(string.uppercase()) }.getOrNull()
            }
		}
	}

    private data class SegmentFragmentMappingConfig(override val label: Label, override val config: CheckBox) :
        ConfigNode {
        constructor(state: ExportSourceState) : this(
            Label("Map Fragment to Segment ID").apply { alignment = Pos.BOTTOM_RIGHT },
            CheckBox().apply {
                GridPane.setFillWidth(this, true)
                maxWidth = Double.MAX_VALUE
                alignment = Pos.CENTER_LEFT
                state.segmentFragmentMappingProperty.bind(selectedProperty())
                selectedProperty().set(true)
            }
        )
    }

    private fun sourceChoiceNode(): ChoiceBox<ConnectomicsLabelState<*, *>> {
		val choices = getValidExportSources()

		return ChoiceBox(choices).apply {
            maxWidth = Double.MAX_VALUE
			val curChoiceIdx = choices.indexOfFirst { it == paintera.baseView.sourceInfo().currentState() }
			if (curChoiceIdx != -1)
				selectionModel.select(curChoiceIdx)
			else
				selectionModel.selectFirst()
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

