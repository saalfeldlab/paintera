package org.janelia.saalfeldlab.paintera.control.actions

import javafx.beans.property.*
import javafx.scene.control.Alert
import javafx.scene.control.TitledPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.AbstractIntegerType
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.ui.ExceptionNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.offset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.resolution
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.SingleScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.get
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.dialogs.AnimatedProgressBarAlert
import org.janelia.saalfeldlab.util.convertRAI
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.n5.N5Helpers.MAX_ID_KEY
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlock
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlockExists
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class ExportSourceState {

	val backendProperty = SimpleObjectProperty<N5BackendLabel<*, *>?>()
	val maxIdProperty = SimpleLongProperty(-1)
	val sourceStateProperty = SimpleObjectProperty<ConnectomicsLabelState<*, *>?>()
	val sourceProperty = SimpleObjectProperty<MaskedSource<*, *>?>()

	val datasetProperty = SimpleStringProperty()
	val exportLocationProperty = SimpleStringProperty()
	val segmentFragmentMappingProperty = SimpleBooleanProperty(true)
	val scaleLevelProperty = SimpleIntegerProperty(0)
	val dataTypeProperty = SimpleObjectProperty(DataType.UINT64)

	private val exportableSourceRAI: RandomAccessibleInterval<out NativeType<*>>?
		get() {
			val source = sourceProperty.value ?: return null
			val backend = backendProperty.value ?: return null

			val fragmentMapper = backend.fragmentSegmentAssignment

			val scaleLevel = scaleLevelProperty.value
			val mapFragmentToSegment = segmentFragmentMappingProperty.value
			val dataType = dataTypeProperty.value

			val dataSource = (source.getDataSource(0, scaleLevel) as? RandomAccessibleInterval<IntegerType<*>>)!!
			val typeVal = N5Utils.type(dataType)!! as AbstractIntegerType<out AbstractIntegerType<*>>
			val invalidVal = typeVal.copy().also { it.setInteger(Label.INVALID) }

			val mappedIntSource = if (mapFragmentToSegment)
				dataSource.convertRAI(typeVal) { src, target ->
					target.setInteger(fragmentMapper.getSegment(src.integerLong))
					if (target == invalidVal)
						target.setInteger(Label.BACKGROUND)
				}
			else
				dataSource.convertRAI(typeVal) { src, target ->
					val srcVal = src.integerLong
					target.setInteger(srcVal)
					if (target == invalidVal)
						target.setInteger(Label.BACKGROUND)
				}

			return mappedIntSource as RandomAccessibleInterval<out NativeType<*>>
		}

	//TODO Caleb: some future ideas:
	//  - Export specific label? Maybe only if LabelBlockLookup is present?
	//  - Export multiscale pyramid
	//  - Export interval of label source
	//  - custom fragment to segment mapping
	fun exportSource(showProgressAlert: Boolean = false): Job? {

		val backend = backendProperty.value ?: return null
		val source = sourceProperty.value ?: return null
		val exportLocation = exportLocationProperty.value ?: return null
		val dataset = datasetProperty.value ?: return null


		val scaleLevel = scaleLevelProperty.value
		val dataType = dataTypeProperty.value

		val metadataState = backend.metadataState

		val metadata = (backend as? SourceStateBackendN5<*, *>)?.run {
			metadataState.let { it as? MultiScaleMetadataState }?.highestResMetadata ?: metadataState.metadata as N5SpatialDatasetMetadata
		}

		val translation = when {
			scaleLevel == 0 -> backend.translation
			metadataState is MultiScaleMetadataState -> metadataState.downscaleTranslation(scaleLevel)
			else -> backend.translation
		}

		val imgSize = source.grids[scaleLevel].run { gridDimensions.apply { forEachIndexed { idx, size -> set(idx, size * cellDimensions[idx]) } } }


		val sourceMetadata: N5SpatialDatasetMetadata = metadata ?: let {
			N5SingleScaleMetadata(
				dataset,
				source.getSourceTransformCopy(0, scaleLevel),
				doubleArrayOf(1.0, 1.0, 1.0),
				backend.resolution,
				translation,
				source.voxelDimensions?.unit() ?: "pixel",
				DatasetAttributes(
					imgSize,
					source.grids[scaleLevel].cellDimensions,
					dataType,
					GzipCompression()
				)
			)
		}


		val n5 = (backend as? SourceStateBackendN5<*, *>)?.container as? GsonKeyValueN5Reader

		val exportRAI = exportableSourceRAI!!
		val cellGrid: CellGrid = source.getGrid(scaleLevel)
		val sourceAttributes: DatasetAttributes = sourceMetadata.attributes

		val exportAttributes = DatasetAttributes(sourceAttributes.dimensions, sourceAttributes.blockSize, dataType, sourceAttributes.compression)

		val totalBlocks = cellGrid.gridDimensions.reduce { acc, dim -> acc * dim }
		val count = SimpleIntegerProperty(0)
		val labelProp = SimpleStringProperty("Blocks Written 0 / $totalBlocks").apply {
			bind(count.createObservableBinding { "Blocks Written ${it.value} / $totalBlocks" })
		}
		val progressProp = SimpleDoubleProperty(0.0).apply {
			bind(count.createObservableBinding { it.get().toDouble() / totalBlocks })
		}

		val (processedBlocks, progressUpdater) = if (showProgressAlert) {
			count to AnimatedProgressBarAlert(
				"Export Label Source",
				"Exporting data...",
				labelProp,
				progressProp
			)
		} else null to null

		val blocksWritten = AtomicInteger(0)

		val exportJob = CoroutineScope(Dispatchers.Default).launch {
			val writer = Paintera.n5Factory.newWriter(exportLocation)
			exportOmeNGFFMetadata(writer, dataset, scaleLevel, exportAttributes, sourceMetadata, translation)
			if (maxIdProperty.value > -1)
				writer.setAttribute(dataset, MAX_ID_KEY, maxIdProperty.value)
			val scaleLevelDataset = "$dataset/s$scaleLevel"

			n5?.let {
				forEachBlockExists(it, sourceMetadata.path, processedBlocks) { cellInterval ->
					val cellRai = exportRAI.interval(cellInterval)
					N5Utils.saveBlock(cellRai, writer, scaleLevelDataset, exportAttributes)
				}
			} ?: forEachBlock(cellGrid, processedBlocks) { cellInterval ->
				val cellRai = exportRAI.interval(cellInterval)
				N5Utils.saveBlock(cellRai, writer, scaleLevelDataset, exportAttributes)
			}
			Paintera.n5Factory.clearKey(exportLocation)
		}
		progressUpdater?.apply {
			exportJob.invokeOnCompletion {
				/* no error, just finish*/
				if (it == null) {
					finish()
					return@invokeOnCompletion
				}

				/* If we are here, there was an error.
				 *  If it was cancellation, just close and warn the user of potential partial export.
				 *  Otherwise, show an exception dialog */
				stopAndClose()
				when {
					blocksWritten.get() > 0 && it is CancellationException -> {
						InvokeOnJavaFXApplicationThread {
							PainteraAlerts.alert(Alert.AlertType.WARNING).apply {
								title = "Export Cancelled"
								headerText = "Export was cancelled.\nPartial dataset export may exist."
								contentText = """
									Export Location: 
											$exportLocation
									Dataset:        $dataset
									Scale Level:    $scaleLevel
									
									Blocks Written: ${blocksWritten.get()}
								""".trimIndent()
							}.showAndWait()
						}
					}

					it is CancellationException -> {}

					it is Exception -> {
						InvokeOnJavaFXApplicationThread {
							/* hack until the dialog is improved in saalfx*/
							val content = ExceptionNode(it).pane.apply {
								children.firstNotNullOfOrNull { it as? TitledPane }?.apply {
									VBox.setVgrow(this, Priority.ALWAYS)
									isExpanded = true
								}
							}
							PainteraAlerts.information("_Ok", true).apply {
								title = "Caught Exception"
								dialogPane.content = content
							}.showAndWait()
						}
					}
				}
			}
			showAndStart().invokeOnCompletion {
				when (it) {
					null -> Unit
					is CancellationException -> exportJob.cancel(it)
					else -> exportJob.cancel("Error with ProgressAlert", it)
				}
			}
		}
		return exportJob
	}
}

internal fun MultiScaleMetadataState.downscaleTranslation(scaleLevel: Int): DoubleArray {
	downscaleTranslation(highestResMetadata.resolution, highestResMetadata.offset, metadata[scaleLevel].resolution)
}

internal fun downscaleTranslation(s0Resolution : DoubleArray, s0Offset : DoubleArray, sNResolution: DoubleArray) : DoubleArray {

	return DoubleArray(3) { idx ->
		s0Offset[idx] + (sNResolution[idx] - s0Resolution[idx]) / 2.0
	}
}

internal fun exportOmeNGFFMetadata(
	writer: N5Writer,
	dataset: String,
	scaleLevel: Int,
	datasetAttributes: DatasetAttributes,
	sourceMetadata: N5SpatialDatasetMetadata,
	translation: DoubleArray = sourceMetadata.offset
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
		arrayOf(translation)
	)

	OmeNgffMetadataParser().writeMetadata(
		exportMetadata,
		writer,
		dataset
	)
}