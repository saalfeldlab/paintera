package org.janelia.saalfeldlab.paintera.control.actions

import javafx.beans.property.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.AbstractIntegerType
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.GsonKeyValueN5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.offset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.resolution
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.get
import org.janelia.saalfeldlab.paintera.ui.dialogs.AnimatedProgressBarAlert
import org.janelia.saalfeldlab.util.convertRAI
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.n5.N5Helpers.MAX_ID_KEY
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlockExists
import java.util.concurrent.atomic.AtomicInteger

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

			val mappedIntSource = if (mapFragmentToSegment)
				dataSource.convertRAI(typeVal) { src, target -> target.setInteger(fragmentMapper.getSegment(src.integerLong)) }
			else
				dataSource

			return mappedIntSource as RandomAccessibleInterval<out NativeType<*>>
		}

	//TODO Caleb: some future ideas:
	//  - Export specific label? Maybe only if LabelBlockLookup is present?
	//  - Export multiscale pyramid
	//  - Export interval of label source
	//  - custom fragment to segment mapping
	fun exportSource(showProgressAlert : Boolean = false) {

		val backend = backendProperty.value ?: return
		val source = sourceProperty.value ?: return
		val exportLocation = exportLocationProperty.value ?: return
		val dataset = datasetProperty.value ?: return


		val scaleLevel = scaleLevelProperty.value
		val dataType = dataTypeProperty.value

		val sourceMetadata: N5SpatialDatasetMetadata = backend.metadataState.let { it as? MultiScaleMetadataState }?.metadata?.get(scaleLevel) ?: backend.metadataState as N5SpatialDatasetMetadata
		val n5 = backend.container as GsonKeyValueN5Reader

		val exportRAI = exportableSourceRAI!!
		val cellGrid: CellGrid = source.getCellGrid(0, scaleLevel)
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
			count to  AnimatedProgressBarAlert(
				"Export Label Source",
				"Exporting data...",
				labelProp,
				progressProp
			)
		} else null to null

		val exportJob = CoroutineScope(Dispatchers.IO).launch {
			val writer = Paintera.n5Factory.openWriter(exportLocation)
			exportOmeNGFFMetadata(writer, dataset, scaleLevel, exportAttributes, sourceMetadata)
			if (maxIdProperty.value > -1)
				writer.setAttribute(dataset, MAX_ID_KEY, maxIdProperty.value)
			val scaleLevelDataset = "$dataset/s$scaleLevel"

			forEachBlockExists(n5, sourceMetadata.path, processedBlocks) { cellInterval ->
				val cellRai = exportRAI.interval(cellInterval)
				N5Utils.saveBlock(cellRai, writer, scaleLevelDataset, exportAttributes)
			}
			Paintera.n5Factory.clearKey(exportLocation)
		}
		progressUpdater?.apply {
			exportJob.invokeOnCompletion { finish() }
			showAndStart()
		}
	}
}

internal fun exportOmeNGFFMetadata(
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