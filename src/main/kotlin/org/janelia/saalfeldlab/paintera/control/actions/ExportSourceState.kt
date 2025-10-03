package org.janelia.saalfeldlab.paintera.control.actions

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import javafx.scene.control.Alert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.NativeType
import net.imglib2.type.Type
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.AbstractIntegerType
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
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
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.offset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.resolution
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.get
import org.janelia.saalfeldlab.paintera.ui.dialogs.AnimatedProgressBarAlert
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.util.convertRAI
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.n5.N5Helpers.MAX_ID_KEY
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlock
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlockExists
import kotlin.coroutines.cancellation.CancellationException

private val LOG = KotlinLogging.logger {  }

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
			val source = getSource() ?: return null
			val backend = getBackend() ?: return null

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

	fun getSource(): DataSource<out RealType<*>?, out Type<*>?>? {
		return sourceProperty.value
			?: sourceStateProperty.value?.dataSource
	}

	fun getBackend(): ConnectomicsLabelBackend<out Any?, out Any?>? {
		return backendProperty.value
			?: sourceStateProperty.value?.backend
	}

	//TODO Caleb: some future ideas:
	//  - Export specific label? Maybe only if LabelBlockLookup is present?
	//  - Export multiscale pyramid
	//  - Export interval of label source
	//  - custom fragment to segment mapping
	fun exportSource(showProgressAlert: Boolean = false): Job? {

		val backend = getBackend() ?: return null
		val source = getSource() ?: return null
		val exportLocation = exportLocationProperty.value ?: return null
		val dataset = datasetProperty.value ?: return null


		val scaleLevel = scaleLevelProperty.value
		val dataType = dataTypeProperty.value


		val metadataState = (backend as? SourceStateBackendN5<*, *>)?.metadataState
		val metadata = metadataState?.let { it as? MultiScaleMetadataState }?.metadata?.get(scaleLevel) ?: metadataState?.metadata as? N5SpatialDatasetMetadata
		val translation = when {
			metadataState is MultiScaleMetadataState && metadataState.highestResMetadata != metadata -> metadataState.downscaleTranslation(scaleLevel)
			else -> backend.translation
		}


		val imgSize = source.grids[scaleLevel].run { gridDimensions.apply { forEachIndexed { idx, size -> set(idx, size * cellDimensions[idx]) } } }


		val sourceMetadata: N5SpatialDatasetMetadata = metadata ?: N5SingleScaleMetadata(
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


		val n5 = (backend as? SourceStateBackendN5<*, *>)?.container as? GsonKeyValueN5Reader

		val exportRAI = exportableSourceRAI!!
		val cellGrid: CellGrid = source.getGrid(scaleLevel)
		val sourceAttributes: DatasetAttributes = sourceMetadata.attributes

		val exportAttributes = DatasetAttributes(sourceAttributes.dimensions, sourceAttributes.blockSize, dataType, sourceAttributes.compression)

		val totalBlocks = cellGrid.gridDimensions.reduce { acc, dim -> acc * dim }
		val count = SimpleIntegerProperty(0)
		val labelProp = SimpleStringProperty("Blocks Processed:\t0 / $totalBlocks").apply {
			bind(count.createNonNullValueBinding { "Blocks Processed:\t$it / $totalBlocks" })
		}
		val progressProp = SimpleDoubleProperty(0.0).apply {
			bind(count.createObservableBinding { it.value.toDouble() / totalBlocks })
		}

		val progressUpdater = if (showProgressAlert) {
			AnimatedProgressBarAlert(
				"Export Label Source",
				"Exporting data...",
				labelProp,
				progressProp,
				cancellable = true
			)
		} else null

		val blocksProcessed = MutableStateFlow(0)
		val incrementProcessed = { -> blocksProcessed.update { it + 1 } }

		val blocksWritten = MutableStateFlow(0)
		val incrementWritten = { -> blocksWritten.update { it + 1 } }

		val exportJob = CoroutineScope(Dispatchers.Default).launch {
			val writer = Paintera.n5Factory.newWriter(exportLocation)
			exportOmeNGFFMetadata(writer, dataset, scaleLevel, exportAttributes, sourceMetadata, translation)
			if (maxIdProperty.value > -1)
				writer.setAttribute(dataset, MAX_ID_KEY, maxIdProperty.value)
			val scaleLevelDataset = "$dataset/s$scaleLevel"

			n5?.let {
				forEachBlockExists(it, sourceMetadata.path, { incrementProcessed() }) { cellInterval ->
					val cellRai = exportRAI.interval(cellInterval)
					N5Utils.saveBlock(cellRai, writer, scaleLevelDataset, exportAttributes)
					incrementWritten()
				}
			} ?: forEachBlock(cellGrid) { cellInterval ->
				val cellRai = exportRAI.interval(cellInterval)
				N5Utils.saveBlock(cellRai, writer, scaleLevelDataset, exportAttributes)
				incrementProcessed()
				incrementWritten()
			}
			Paintera.n5Factory.remove(exportLocation)
		}
		progressUpdater?.apply {
			InvokeOnJavaFXApplicationThread {
				while (exportJob.isActive) {
					repeat(3) { awaitPulse() }
					count.value = blocksProcessed.value
				}
				count.value = blocksProcessed.value
			}
			exportJob.invokeOnCompletion { cause ->
				when {
					/* No error, clean up */
					cause == null -> InvokeOnJavaFXApplicationThread {
						finish()
						close()
						LOG.info { "Export Complete ($exportLocation?$dataset/$scaleLevel)" }
						PainteraAlerts.information("Ok").apply {
							title = "Export Complete"
							headerText = "Export complete."
							contentText = """
									Export Location: 
											$exportLocation
									Dataset:        $dataset
									Scale Level:    $scaleLevel
								""".trimIndent()
						}.showAndWait()
					}
					/* Cancellation after some blocks have been written; warn the user */
					blocksWritten.value > 0 && cause is CancellationException -> {
						InvokeOnJavaFXApplicationThread {
							stopAndClose()
							LOG.info { "Export Cancelled with some blocks written ($exportLocation?$dataset/$scaleLevel)" }
							PainteraAlerts.alert(Alert.AlertType.WARNING).apply {
								title = "Export Cancelled"
								headerText = "Export was cancelled.\nPartial dataset export may exist."
								contentText = """
									Export Location: 
											$exportLocation
									Dataset:        $dataset
									Scale Level:    $scaleLevel
									
									Blocks Written: ${blocksWritten.value}
								""".trimIndent()
							}.showAndWait()
						}
					}

					/* Non-cancellation error */
					cause is Exception && cause !is CancellationException -> InvokeOnJavaFXApplicationThread {
						/* hack until the dialog is improved in saalfx*/
						ExceptionNode.exceptionDialog(cause).showAndWait()
					}
				}
			}
			showProgressAndWait().invokeOnCompletion {
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

internal fun MultiScaleMetadataState.downscaleTranslation(scaleLevel: Int) = downscaleTranslation(
	highestResMetadata.resolution,
	highestResMetadata.offset,
	metadata[scaleLevel].resolution
)

internal fun downscaleTranslation(s0Resolution: DoubleArray, s0Offset: DoubleArray, sNResolution: DoubleArray): DoubleArray {

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
	translation: DoubleArray = sourceMetadata.offset,
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