package org.janelia.saalfeldlab.paintera.ui.dialogs

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.type.numeric.IntegerType
import org.controlsfx.control.StatusBar
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.n5.GsonKeyValueN5Reader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.n5.LabelSourceUtils.findMaxId
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.id.LocalIdService
import org.janelia.saalfeldlab.paintera.id.N5IdService
import org.janelia.saalfeldlab.paintera.ui.PositiveLongTextFormatter
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.alert
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.setButtonText
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.io.IOException
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt

object DataSourceDialogs {

	private val LOG = KotlinLogging.logger { }

	/**
	 * Get a [LabelBlockLookup] that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 *
	 * @param source used to determine block sizes for marching cubes
	 * @return [LabelBlockLookup] that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 */
	@JvmStatic
	fun getLabelBlockLookupFromN5DataSource(
		reader: N5Reader?,
		group: String?,
		source: DataSource<*, *>,
	): LabelBlockLookup {
		val alert = alert(Alert.AlertType.CONFIRMATION).apply {
			buttonTypes.setAll(ButtonType.OK, ButtonType.NEXT, ButtonType.CANCEL)
			setButtonText(
				ButtonType.OK to "_Ok",
				ButtonType.NEXT to "_Skip",
				ButtonType.CANCEL to "_Cancel"
			)
			headerText = "Define label-to-block-lookup for on-the-fly mesh generation"
			dialogPane.content = TextArea(
				"""
					Could not deserialize label-to-block-lookup for dataset `$group` in N5 container at 
						${reader?.uri}
						
					label-to-block-lookup is required for fast on-the-fly mesh generation.
					To proceed with slow mesh generation, press "OK".
					If you are not interested in 3D meshes, press "Skip". 
					
					Press "Cancel" to cancel adding this source. 
				""".trimIndent()
			).apply {
				isEditable = false
				isWrapText = true
				prefColumnCount *= 2
				prefRowCount *= 2
			}
		}
		return when (alert.showAndWait().getOrNull()) {
			ButtonType.OK -> {
				val grids = source.grids
				val dims = arrayOfNulls<LongArray>(grids.size)
				val blockSizes = arrayOfNulls<IntArray>(grids.size)
				for (i in grids.indices) {
					dims[i] = grids[i]!!.imgDimensions
					blockSizes[i] = IntArray(grids[i]!!.numDimensions())
					grids[i]!!.cellDimensions(blockSizes[i])
				}
				LOG.debug { "Returning block lookup returning all blocks." }
				LabelBlockLookupAllBlocks(dims, blockSizes)
			}

			ButtonType.NEXT -> LabelBlockLookupNoBlocks()

			else -> throw CancellationException("Add Source Cancelled")
		}
	}


	@Throws(IOException::class)
	@JvmStatic
	fun getN5IdServiceFromData(
		n5: N5Writer,
		dataset: String?,
		source: DataSource<out IntegerType<*>, *>,
	): IdService {
		// sometimes NullPointerExceptions appear. This bug report may be relevant:
		// https://bugs.openjdk.java.net/browse/JDK-8157399

		val alert: Alert = alert(Alert.AlertType.CONFIRMATION).apply {
			buttonTypes.setAll(ButtonType.OK, ButtonType.NEXT, ButtonType.CANCEL)
			setButtonText(
				ButtonType.OK to "_Ok",
				ButtonType.NEXT to "_Skip",
				ButtonType.CANCEL to "_Cancel",
			)
			headerText = "Max ID not specified in dataset."
		}
		val ta = TextArea().apply {
			text = """
				Could not read maxId attribute from dataset '$dataset'' in container at 
					'${n5.uri}'.
				 
				You can specify the max id manually, or read it from the data set (this can take a long time if your data is big).
				Alternatively, press "Skip" to load the data set without an id service. 
				Fragment-segment-assignments require an id service and will not be available if you press "Skip".
				
				Press "Cancel" or close the window to stop loading the dataset. 
			""".trimIndent()
			prefColumnCount *= 2
			prefRowCount *= 2
			isEditable = false
			isWrapText = true
		}
		val maxIdFormatter = PositiveLongTextFormatter()
		val maxIdProperty = SimpleObjectProperty<Long?>(null)

		maxIdFormatter.valueProperty().bindBidirectional(maxIdProperty)
		val maxIdField = TextField().apply {
			alignment = Pos.BASELINE_RIGHT
			textFormatter = maxIdFormatter
			textProperty().subscribe { _, _ -> commitValue() }
		}
		val task = SimpleObjectProperty<Job?>()
		val isNotRunning = task.isNull
		val cannotClickOk = task.isNotNull.or(maxIdProperty.isNull)
		maxIdField.editableProperty().bind(isNotRunning)
		val disableOkButton = alert.dialogPane.lookupButton(ButtonType.OK).disableProperty()
		disableOkButton.bind(cannotClickOk)
		val scanDataButtonText = "_Scan Data"
		val interruptScanButtonText = "_Interrupt"
		val scanButton = Button(scanDataButtonText).apply {
			tooltip = Tooltip("").also {
				val buttonTextNoMnemonics = textProperty().map { it?.replace("_", "") }
				it.textProperty().bind(buttonTextNoMnemonics)
			}
			prefWidth = 100.0
		}

		val initialValue: LongProperty = SimpleLongProperty(maxIdProperty.get() ?: Label.INVALID)
		val statusBar = StatusBar().apply {
			prefWidth = 220.0
			graphic = scanButton
			text = ""
			tooltip = Tooltip("").also {
				val progressText = progressProperty().multiply(100.0).map { it.toDouble().roundToInt() }.map { "$it%" }
				it.textProperty().bind(progressText)
			}
		}

		val runOnScanData = {
			initialValue.set(maxIdProperty.get() ?: Label.INVALID)
			val maxTracker = MutableStateFlow(Label.INVALID)

			val grid = source.getGrid(0)
			val totalBlocks = grid.gridDimensions.reduce { l, r -> l * r }

			val dataSource = source.getDataSource(0, 0)

			val count = MutableStateFlow(0)
			val findMaxIdJob = CoroutineScope(Dispatchers.Default).launch {

				(n5 as? GsonKeyValueN5Reader)?.let {
					N5Helpers.forEachBlockExists(it, dataset ?: "", { count.getAndUpdate { it + 1 } }) { block ->
						val localMax = findMaxId(dataSource.interval(block))
						maxTracker.getAndUpdate { max -> maxOf(max, localMax) }
					}
				} ?: N5Helpers.forEachBlock(grid) { interval ->
					val localMax = findMaxId(dataSource.interval(interval))
					maxTracker.getAndUpdate { max -> maxOf(max, localMax) }
					count.getAndUpdate { it + 1 }
				}
			}

			InvokeOnJavaFXApplicationThread {
				val updateProgress = { statusBar.progress = count.value / totalBlocks.toDouble() }
				while (findMaxIdJob.isActive) {
					repeat(3) { awaitPulse() }
					updateProgress()
				}
				updateProgress()
			}

			findMaxIdJob.invokeOnCompletion { cause ->
				when (cause) {
					is CancellationException -> {
						val initialValue = initialValue.get()
						LOG.info { "Setting next id field $maxIdProperty to initial value $initialValue" }
						maxIdProperty.set(initialValue)
						statusBar.progress = 0.0
					}

					null -> {
						InvokeOnJavaFXApplicationThread {
							maxIdProperty.value = maxTracker.value
							scanButton.text = scanDataButtonText
						}
					}

					else -> LOG.error(cause) { "Could not find max id for dataset $dataset" }
				}
			}
			findMaxIdJob
		}

		val runOnCancel = {
			task.set(null)
			LOG.info { "Setting next id field $maxIdProperty to initial value $initialValue" }
			maxIdProperty.set(initialValue.get())
			statusBar.progress = 0.0
			null
		}

		val actionCycle = sequence {
			while (true) {
				yield(runOnScanData to interruptScanButtonText)
				yield(runOnCancel to scanDataButtonText)
			}
		}.iterator()

		var job: Job? = null
		scanButton.onAction = EventHandler {
			job?.cancel()
			val (action, nextActionText) = actionCycle.next()
			scanButton.text = nextActionText
			job = action()
		}

		val maxIdBox = HBox(Label("Max Id:"), maxIdField, statusBar).apply {
			alignment = Pos.CENTER
			HBox.setHgrow(maxIdField, Priority.ALWAYS)
		}

		alert.dialogPane.content = VBox(ta, maxIdBox)

		return runBlocking {
			InvokeOnJavaFXApplicationThread {
				val buttonType = alert.showAndWait().getOrNull()
				when (buttonType) {
					ButtonType.OK -> {
						val maxId = maxIdProperty.get() ?: Label.INVALID
					runCatching {
						n5.setAttribute(dataset, "maxId", maxId)
						N5IdService(n5, dataset, maxId)
					}.getOrElse {
						LocalIdService(maxId)
					}
					}
					ButtonType.NEXT -> {
					task.get()?.cancel()
					LocalIdService(1)
				}
					else -> throw CancellationException("Open Source Cancelled")
				}
			}.await()
		}
	}
}