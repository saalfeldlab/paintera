package org.janelia.saalfeldlab.paintera.control.actions

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import javafx.scene.control.Alert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.array.ArrayImgFactory
import net.imglib2.img.cell.CellGrid
import net.imglib2.iterator.IntervalIterator
import net.imglib2.loops.LoopBuilder
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import net.imglib2.type.NativeType
import net.imglib2.type.Type
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.AbstractIntegerType
import net.imglib2.type.numeric.integer.UnsignedLongType
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.ui.ExceptionNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.NgffSingleScaleAxesMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueWriter
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.openLabelMultiset
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.cropAtLevel
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.offset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.resolution
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.ui.dialogs.AnimatedProgressBarAlert
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.convertRAI
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.n5.N5Helpers.MAX_ID_KEY
import org.janelia.saalfeldlab.util.n5.N5Helpers.PAINTERA_NAMESPACE
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlock
import org.janelia.saalfeldlab.util.n5.N5Helpers.forEachBlockExists
import org.janelia.saalfeldlab.util.n5.SpatialMapping
import org.janelia.scicomp.n5.zstandard.ZstandardCompression
import org.scijava.annotations.Index
import kotlin.coroutines.cancellation.CancellationException

private val LOG = KotlinLogging.logger {  }

class ExportSourceState {

	companion object {

		internal val COMPRESSION_INDEX: Index<Compression.CompressionType?> = Index.load(
			Compression.CompressionType::class.java,
			Thread.currentThread().contextClassLoader
		)

		internal val DEFAULT_COMPRESSION = COMPRESSION_INDEX
			.firstOrNull { it.annotation()?.value?.lowercase() == ZstandardCompression().type }
			?: COMPRESSION_INDEX.first()
	}

	val backendProperty = SimpleObjectProperty<N5BackendLabel<*, *>?>()
	val maxIdProperty = SimpleLongProperty(-1)
	val sourceStateProperty = SimpleObjectProperty<ConnectomicsLabelState<*, *>?>()
	val sourceProperty = SimpleObjectProperty<MaskedSource<*, *>?>()

	val datasetProperty = SimpleStringProperty()
	val exportLocationProperty = SimpleStringProperty()
	val segmentFragmentMappingProperty = SimpleBooleanProperty(true)
	val exportCropProperty = SimpleBooleanProperty(true)
	val scaleLevelProperty = SimpleIntegerProperty(0)
	val dataTypeProperty = SimpleObjectProperty(DataType.UINT64)
	val storageFormatProperty = SimpleObjectProperty<StorageFormat?>(null)
    val compressionProperty = SimpleObjectProperty<Compression?>(null)

	val hasUncommittedCanvas: Boolean
		get() = (getSource() as? MaskedSource<*, *>)?.affectedBlocks?.isNotEmpty() == true

	/**
	 * The committed data at [dataset] with the export data type.
	 *
	 * @param mapFragmentToSegment if [true] export segment IDs
	 */
	private fun exportableSourceRAI(
		reader: N5Reader,
		dataset: String,
		isLabelMultiset: Boolean,
		mapFragmentToSegment: Boolean
	): RandomAccessibleInterval<out NativeType<*>>? {

		val backend = getBackend() ?: return null
		val fragmentMapper = backend.fragmentSegmentAssignment
		val dataType = dataTypeProperty.value

		val committedSource = when {
			isLabelMultiset -> openLabelMultiset(reader, dataset)
			else -> N5Utils.open<Nothing>(reader, dataset) // type is dictated by the dataset attributes
		} as RandomAccessibleInterval<IntegerType<*>>

		val typeVal = N5Utils.type(dataType)!! as AbstractIntegerType<out AbstractIntegerType<*>>
		val invalidVal = typeVal.copy().also { it.setInteger(Label.INVALID) }

		val getFragmentId : IntegerType<*>.() -> Long = { integerLong }
		val getSegmentId : IntegerType<*>.() -> Long = { fragmentMapper.getSegment(integerLong) }

		val exportId: IntegerType<*>.() -> Long =
			if (mapFragmentToSegment) getSegmentId
			else getFragmentId

		val exportSource = committedSource.convertRAI(typeVal) { src, target ->
			target.setInteger(src.exportId())
			if (target == invalidVal)
				target.setInteger(Label.BACKGROUND)
		}

		return exportSource as RandomAccessibleInterval<out NativeType<*>>
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
	//  - custom fragment to segment mapping
    //  - export sharded zarr source
	fun exportSource(showProgressAlert: Boolean = false): Job? {

		val backend = getBackend() ?: return null
		val source = getSource() ?: return null
		val exportLocation = exportLocationProperty.value ?: return null
		val dataset = datasetProperty.value ?: return null


		val scaleLevel = scaleLevelProperty.value
		val dataType = dataTypeProperty.value


		/* export from the source data */
		val metadataState = (backend as? SourceStateBackendN5<*, *>)?.metadataState ?: return null
		val metadata = (metadataState as? MultiScaleMetadataState)?.metadata?.childrenMetadata[scaleLevel] ?: metadataState.metadata as? N5SpatialDatasetMetadata
		val translation = when {
			metadataState is MultiScaleMetadataState && metadataState.highestResMetadata != metadata -> metadataState.downscaleTranslation(scaleLevel)
			metadata is NgffSingleScaleAxesMetadata -> metadata.translation
			metadata is N5SpatialDatasetMetadata -> metadata.offset
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
				ZstandardCompression()
			)
		)


		val compression = compressionProperty.value ?: sourceMetadata.attributes.compression

		val (formatFromString : StorageFormat?, exportContainer : String) = StorageFormat.getStorageFromNestedScheme(exportLocation).let { pair -> pair.a to pair.b }
		val formatFromChoice = storageFormatProperty.get()
		val storageFormat = formatFromChoice ?: formatFromString

		val writer = getWriterOrAlert(storageFormat, exportContainer, exportLocation, dataset, scaleLevel) ?: return null

		/* only a key-value container can be asked which blocks exist; otherwise every block is written */
		val n5 = metadataState.reader as? GsonKeyValueN5Reader

		val committedRAI = exportableSourceRAI(
			metadataState.reader,
			sourceMetadata.path,
			metadataState.isLabelMultiset,
			this@ExportSourceState.segmentFragmentMappingProperty.value
		) ?: return null
		val sourceAttributes: DatasetAttributes = sourceMetadata.attributes

		/* the crop over this level, in source order with the non-spatial axes whole; null exports the whole dataset block for block */
		val cropInterval = metadataState.xyzView.xyzCrop?.takeIf { exportCropProperty.value }?.let { cropInterval(metadataState, scaleLevel, sourceAttributes.dimensions, it) }
		val exportRAI = cropInterval?.let { Views.zeroMin(Views.interval(committedRAI, it)) } ?: committedRAI
		val exportTranslation = cropInterval?.let { translation.withCropOffset(it, sourceMetadata.resolution, metadataState.xyzView.spatialMapping()) } ?: translation
		val exportDimensions = cropInterval?.dimensionsAsLongArray() ?: sourceAttributes.dimensions

		val exportAttributes = DatasetAttributes(exportDimensions, sourceAttributes.chunkSize, dataType, compression)

		val iterationGrid = CellGrid(exportDimensions, sourceAttributes.blockSize)
		val sourceGrid = CellGrid(sourceAttributes.dimensions, sourceAttributes.blockSize)
		val totalBlocks = iterationGrid.gridDimensions.reduce { acc, dim -> acc * dim }
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
			val createdAttributes = exportOmeNGFFMetadata(writer, dataset, scaleLevel, exportAttributes, sourceMetadata, exportTranslation)
			writer.setAttribute(dataset, "$PAINTERA_NAMESPACE/isLabel", true)
			if (maxIdProperty.value > -1)
				writer.setAttribute(dataset, "$PAINTERA_NAMESPACE/$MAX_ID_KEY", maxIdProperty.value)
			val scaleLevelDataset = "$dataset/s$scaleLevel"
			val writeBlock = { cellInterval: Interval ->
				exportBlock<UnsignedLongType>(exportRAI, cellInterval, writer, scaleLevelDataset, createdAttributes)
			}

			when {
				/* the export grid is the source grid, so an existing source block is an export block */
				cropInterval == null && n5 != null -> forEachBlockExists(n5, sourceMetadata.path, { incrementProcessed() }) { cellInterval ->
					writeBlock(cellInterval)
					incrementWritten()
				}

				else -> forEachBlock(iterationGrid) { cellInterval ->
					val sourceBlockInterval = cropInterval?.let { Intervals.translate(cellInterval, *it.minAsLongArray()) } ?: cellInterval
					if (n5 == null || n5.anyBlockExists(sourceMetadata.path, sourceGrid, sourceBlockInterval)) {
						writeBlock(cellInterval)
						incrementWritten()
					}
					incrementProcessed()
				}
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
						PainteraCache.RECENT_EXPORT_LOCATIONS.appendLine(exportLocation)
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

	/**
	 * Write the [blockInterval] of [source] to [dataset].
	 *
	 * Block writing wants to resolve to primitive blocks, which is not supported for
	 * label multiset types. To avoid this, copy the block before saving it.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun <T : NativeType<T>> exportBlock(
		source: RandomAccessibleInterval<out NativeType<*>>,
		blockInterval: Interval,
		writer: N5Writer,
		dataset: String,
		attributes: DatasetAttributes
	) {
		val typedSource = source as RandomAccessibleInterval<T>
		val block = Intervals.intersect(blockInterval, typedSource)

		val blockCopy = ArrayImgFactory(typedSource.type.createVariable()).create(*block.dimensionsAsLongArray())
		LoopBuilder.setImages(typedSource.interval(block), blockCopy).forEachPixel { exported, target -> target.set(exported) }

		N5Utils.saveBlock(Views.translate(blockCopy, *block.minAsLongArray()), writer, dataset, attributes)
	}

	private fun getWriterOrAlert(
		storageFormat: StorageFormat?,
		exportContainer: String,
		exportLocation: String,
		dataset: String,
		scaleLevel: Int?
	): N5Writer? = runCatching {
		Paintera.n5Factory.newWriter(storageFormat, exportContainer)
	}.onFailure {
		LOG.error(it) { }
		PainteraAlerts.alert(Alert.AlertType.WARNING).apply {
			title = "Export Failed"
			headerText = "Export Failed.\nCould not open $exportLocation."
			contentText = """
										Export Location: 
												$exportLocation
										Dataset:        $dataset
										Scale Level:    $scaleLevel
										
										Error:
											${it.message}
									""".trimIndent()
		}.showAndWait()
	}.getOrNull()
}

/* [dimensions] of scale [level] narrowed to [s0Crop] on the spatial axes; the non-spatial axes stay whole */
internal fun cropInterval(metadataState: MetadataState, level: Int, dimensions: LongArray, s0Crop: Interval): Interval {
	val fullSourceInterval = FinalInterval(*dimensions)
	val sourceToXyzTransforms = (metadataState as? MultiScaleMetadataState)?.sourceToXyzTransforms ?: arrayOf(metadataState.sourceToXyz)
	val cropXyz = cropAtLevel(s0Crop, sourceToXyzTransforms, level)
	val mapping = metadataState.xyzView.spatialMapping()
	val sourceCropMin = mapping.toSource(cropXyz.minAsLongArray(), fullSourceInterval.minAsLongArray())
	val sourceCropMax = mapping.toSource(cropXyz.maxAsLongArray(), fullSourceInterval.maxAsLongArray())
	return Intervals.intersect(FinalInterval(sourceCropMin, sourceCropMax), fullSourceInterval)
}

/** This translation moved to the min of [exportInterval], in source order */
internal fun DoubleArray.withCropOffset(exportInterval: Interval, resolution: DoubleArray, mapping: SpatialMapping): DoubleArray {
	val numDimensions = exportInterval.numDimensions()
	val translation = toSource(mapping, numDimensions, 0.0)
	val voxelSize = resolution.toSource(mapping, numDimensions, 1.0)
	return DoubleArray(numDimensions) { axis -> translation[axis] + exportInterval.min(axis) * voxelSize[axis] }
}

/* a 3D array over an nD source is indexed by xyz slot; the other axes get [fill] */
private fun DoubleArray.toSource(mapping: SpatialMapping, numDimensions: Int, fill: Double): DoubleArray =
	if (size == numDimensions) this else mapping.toSource(this, DoubleArray(numDimensions) { fill })

/** Whether any block of [sourceGrid] meeting [sourceInterval] exists in [dataset] */
private fun GsonKeyValueN5Reader.anyBlockExists(dataset: String, sourceGrid: CellGrid, sourceInterval: Interval): Boolean {
	val attributes = getDatasetAttributes(dataset)
	val inSource = Intervals.intersect(sourceInterval, FinalInterval(*sourceGrid.imgDimensions))
	if (Intervals.isEmpty(inSource))
		return false
	val minCell = LongArray(inSource.numDimensions()) { inSource.min(it) / sourceGrid.cellDimension(it) }
	val maxCell = LongArray(inSource.numDimensions()) { inSource.max(it) / sourceGrid.cellDimension(it) }
	val cells = IntervalIterator(FinalInterval(minCell, maxCell))
	while (cells.hasNext()) {
		cells.fwd()
		if (blockExists(dataset, attributes, *cells.positionAsLongArray()))
			return true
	}
	return false
}

internal fun MultiScaleMetadataState.downscaleTranslation(scaleLevel: Int) = downscaleTranslation(
	highestResMetadata.resolution,
	highestResMetadata.offset,
	metadata.childrenMetadata[scaleLevel].resolution
)

internal fun downscaleTranslation(s0Resolution: DoubleArray, s0Offset: DoubleArray, sNResolution: DoubleArray): DoubleArray {

	return DoubleArray(s0Offset.size) { idx ->
		s0Offset[idx] + (sNResolution[idx] - s0Resolution[idx]) / 2.0
	}
}

/** [fill] wherever the source left an axis undefined; a >3D dataset without spatial metadata reads back as `NaN` there, which is not valid JSON. */
private fun DoubleArray.definedOr(fill: Double) = DoubleArray(size) { if (this[it].isFinite()) this[it] else fill }

private fun fallbackAxes(unit: String, numDimensions: Int): Array<Axis> {
	val axes = mutableListOf<Axis>().apply {
		for (idx in 0 until numDimensions) {
			val axis = when(idx) {
				0 -> Axis(Axis.SPACE, "x", unit, false)
				1 -> Axis(Axis.SPACE, "y", unit, false)
				2 -> Axis(Axis.SPACE, "z", unit, false)
				3 -> Axis(Axis.CHANNEL, "c", null, true)
				4 -> Axis(Axis.TIME, "t", null, true)
				else -> Axis(Axis.CHANNEL, "c$idx", null, true)
			}
			add(axis)
		}
	}
	return axes.toTypedArray()
}

internal fun exportOmeNGFFMetadata(
	writer: N5Writer,
	dataset: String,
	scaleLevel: Int,
	datasetAttributes: DatasetAttributes,
	sourceMetadata: N5SpatialDatasetMetadata,
	translation: DoubleArray = sourceMetadata.offset,
): DatasetAttributes {
	val scaleLevelDataset = "$dataset/s$scaleLevel"
	writer.createGroup(dataset)
	val createdAttributes = writer.createDataset(scaleLevelDataset, datasetAttributes)

	val axes = (sourceMetadata as? AxisMetadata)?.axes ?: fallbackAxes(sourceMetadata.unit(), datasetAttributes.numDimensions)

	/* zarr2 containers get OME-Zarr 0.4 metadata; everything else get 0.5 */
	val ngffVersion = if (writer is ZarrKeyValueWriter) "0.4" else "0.5"
	val exportMetadata = OmeNgffMetadata.buildForWriting(
		datasetAttributes.numDimensions,
		dataset,
		ngffVersion,
		axes,
		arrayOf("s$scaleLevel"),
		arrayOf(sourceMetadata.resolution.definedOr(1.0)),
		arrayOf(translation.definedOr(0.0))
	)

	OmeNgffMetadataParser(writer).writeMetadata(
		exportMetadata,
		writer,
		dataset
	)
	return createdAttributes
}