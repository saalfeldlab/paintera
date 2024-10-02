package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.property.*
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.AbstractIntegerType
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.GsonKeyValueN5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser
import org.janelia.saalfeldlab.paintera.*
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys.namedCombinationsCopy
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.offset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.resolution
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.get
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems
import org.janelia.saalfeldlab.util.convertRAI
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlockExists
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

internal object ExportSource {

	private const val DIALOG_HEADER = "Export Source"
	private const val CANCEL_LABEL = "_Cancel"
	private const val EXPORT = "Export"
	private val acceptableDataTypes = DataType.entries.filter { it < DataType.FLOAT32 }.toTypedArray()

	class State {

		val backendProperty = SimpleObjectProperty<N5Backend<*, *>?>()
		val sourceStateProperty = SimpleObjectProperty<ConnectomicsLabelState<*, *>?>()
		val sourceProperty = SimpleObjectProperty<MaskedSource<*, *>?>()

		val datasetProperty = SimpleStringProperty()
		val writerPathProperty = SimpleStringProperty()
		val segmentFragmentMappingProperty = SimpleBooleanProperty(true)
		val scaleLevelProperty = SimpleIntegerProperty(0)
		val dataTypeProperty = SimpleObjectProperty(DataType.UINT64)

		internal val exportableSourceRAI: RandomAccessibleInterval<out NativeType<*>>?
			get() {
				val source = sourceProperty.value ?: return null
				val backend = backendProperty.value ?: return null

				val fragmentMapper = backend.fragmentSegmentAssignment

				val scaleLevel = scaleLevelProperty.value
				val mapFragmentToSegment = segmentFragmentMappingProperty.value
				val dataType = dataTypeProperty.value


				val dataSource = (source.getDataSource(0, scaleLevel) as? RandomAccessibleInterval<IntegerType<*>>)!!
				val typeVal = N5Utils.type(dataType)!! as AbstractIntegerType<out AbstractIntegerType<*>>

				val mappedIntSource = if (mapFragmentToSegment)
					dataSource.convertRAI(typeVal) { src, target -> target.setInteger(fragmentMapper.getSegment(src.integerLong)) }
				else
					dataSource

				return mappedIntSource as RandomAccessibleInterval<out NativeType<*>>
			}
	}

	fun exportDialog() {
		val state = State()
		if (newDialog(state).showAndWait().nullable == ButtonType.OK) {
			exportSource(state)
		}
	}

	private fun exportSource(state: State) {


		val backend = state.backendProperty.value ?: return
		val source = state.sourceProperty.value ?: return
		val writerPath = state.writerPathProperty.value ?: return
		val dataset = state.datasetProperty.value ?: return


		val scaleLevel = state.scaleLevelProperty.value
		val dataType = state.dataTypeProperty.value

		val sourceMetadata: N5SpatialDatasetMetadata = backend.getMetadataState().let { it as? MultiScaleMetadataState }?.metadata?.get(scaleLevel) ?: backend.getMetadataState() as N5SpatialDatasetMetadata
		val n5 = backend.container as GsonKeyValueN5Reader

		val exportRAI = state.exportableSourceRAI!!
		val cellGrid: CellGrid = source.getCellGrid(0, scaleLevel)
		val sourceAttributes: DatasetAttributes = sourceMetadata.attributes

		val exportAttributes = DatasetAttributes(sourceAttributes.dimensions, sourceAttributes.blockSize, dataType, sourceAttributes.compression)

		val totalBlocks = cellGrid.gridDimensions.reduce { acc, dim -> acc * dim }
		val processedBlocks = AtomicInteger()
		val progressUpdater = AnimatedProgressBarAlert(
			"Export Label Source",
			"Exporting data...",
			"Blocks Written",
			processedBlocks::get,
			totalBlocks.toInt()
		)

		CoroutineScope(Dispatchers.IO).launch {
			val writer = Paintera.n5Factory.openWriter(writerPath)
			exportOmeNGFFMetadata(writer, dataset, scaleLevel, exportAttributes, sourceMetadata)
			val scaleLevelDataset = "$dataset/s$scaleLevel"

			forEachBlockExists(source, scaleLevel, n5, sourceMetadata.path, processedBlocks) { cellInterval ->
				val cellRai = exportRAI.interval(cellInterval)
				N5Utils.saveBlock(cellRai, writer, scaleLevelDataset, exportAttributes)
			}
			Paintera.n5Factory.clearKey(writerPath)
		}.invokeOnCompletion {
			progressUpdater.stopAndClose()
		}
		progressUpdater.showAndStart()
	}

	private fun exportOmeNGFFMetadata(
		writer: N5Writer,
		dataset: String,
		scaleLevel: Int,
		datasetAttributes: DatasetAttributes,
		sourceMetadata: N5SpatialDatasetMetadata
	) {
		val scaleLevelDataset = "$dataset/s$scaleLevel"
		writer.createGroup(dataset)
		writer.createDataset(scaleLevelDataset, datasetAttributes)

		val exportMetadata = OmeNgffMetadata.buildForWriting(
			datasetAttributes.numDimensions,
			dataset,
			arrayOf(
				Axis(Axis.SPACE, "x", sourceMetadata.unit(), false),
				Axis(Axis.SPACE, "y", sourceMetadata.unit(), false),
				Axis(Axis.SPACE, "z", sourceMetadata.unit(), false)
			),
			arrayOf("s$scaleLevel"),
			arrayOf(sourceMetadata.resolution),
			arrayOf(sourceMetadata.offset)
		)

		OmeNgffMetadataParser().writeMetadata(
			exportMetadata,
			writer,
			dataset
		)
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

	private fun newDialog(state: State): Alert = PainteraAlerts.confirmation(EXPORT, CANCEL_LABEL, true, paintera.pane.scene?.window).apply {
		val choiceBox = createSourceChoiceBox().apply {
			maxWidth = Double.MAX_VALUE
		}

		val maxIdProperty = SimpleLongProperty(-1)
		choiceBox.selectionModel.selectedItemProperty().subscribe { (_, _, backend) ->
			backend.getMetadataState().apply {
				val maxId: Long? = reader[dataset, "maxId"]
				maxIdProperty.set(maxId ?: -1)
			}
		}

		state.sourceProperty.bind(choiceBox.selectionModel.selectedItemProperty().map { it.first })
		state.sourceStateProperty.bind(choiceBox.selectionModel.selectedItemProperty().map { it.second })
		state.backendProperty.bind(choiceBox.selectionModel.selectedItemProperty().map { it.third })

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
				state.writerPathProperty.bind(textProperty())
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
			val scaleLevelsBinding = choiceBox.selectionModel.selectedItemProperty().createNonNullValueBinding { (source, _, _) ->
				if (prevScaleLevels != null && prevScaleLevels!!.size == source.numMipmapLevels)
					return@createNonNullValueBinding prevScaleLevels

				FXCollections.observableArrayList<Int>().also {
					for (i in 0 until source.numMipmapLevels) {
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

			maxIdProperty.subscribe { maxId -> dataTypeChoices.selectionModel.select(pickSmallestDataType(maxId.toLong())) }
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

	private fun createSourceChoiceBox(): ChoiceBox<Triple<MaskedSource<*, *>, ConnectomicsLabelState<*, *>, N5Backend<*, *>>> {
		val choices = FXCollections.observableArrayList<Triple<MaskedSource<*, *>, ConnectomicsLabelState<*, *>, N5Backend<*, *>>>()
		val labelSources = paintera.baseView.sourceInfo().trackSources()
			.asSequence()
			.filterIsInstance<MaskedSource<*, *>>()
			.mapNotNull { source ->
				(paintera.baseView.sourceInfo().getState(source) as? ConnectomicsLabelState<*, *>)?.let { state ->
					source to state
				}
			}
			.mapNotNull { (source, state) ->
				(state.backend as? N5Backend<*, *>)?.let { backend ->
					Triple(source, state, backend)
				}

			}.toCollection(choices)

		return ChoiceBox(labelSources).apply {
			val curChoiceIdx = choices.indexOfFirst { it.second == paintera.currentSource }
			if (curChoiceIdx != -1)
				selectionModel.select(curChoiceIdx)
			else
				selectionModel.selectFirst()
			maxWidth = Double.MAX_VALUE
			converter = object : StringConverter<Triple<MaskedSource<*, *>, ConnectomicsLabelState<*, *>, N5Backend<*, *>>>() {
				override fun toString(`object`: Triple<MaskedSource<*, *>, ConnectomicsLabelState<*, *>, N5Backend<*, *>>?): String = `object`?.second?.nameProperty()?.get() ?: "Select a Source..."
				override fun fromString(string: String?) = labelSources.first { it.second.nameProperty().get() == string }
			}
		}
	}

	fun exportSourceDialogAction(
		baseView: PainteraBaseView,
		projectDir: Supplier<String>
	) = painteraActionSet("export-dataset", MenuActionType.ExportSource) {
		KeyEvent.KEY_PRESSED(namedCombinationsCopy(), PainteraBaseKeys.EXPORT_SOURCE) {
			onAction { PainteraMenuItems.EXPORT_SOURCE.menu.fire() }
			handleException {
				val scene = baseView.viewer3D().scene.scene
				exceptionAlert(
					Constants.NAME,
					"Error showing export dataset menu",
					it,
					null,
					scene?.window
				)
			}
		}
	}

}

