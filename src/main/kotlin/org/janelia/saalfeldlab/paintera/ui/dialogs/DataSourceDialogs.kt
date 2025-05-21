package org.janelia.saalfeldlab.paintera.ui.dialogs

import com.google.common.util.concurrent.AtomicDouble
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.numeric.IntegerType
import org.controlsfx.control.StatusBar
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.n5.GsonKeyValueN5Reader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.n5.LabelSourceUtils
import org.janelia.saalfeldlab.paintera.data.n5.LabelSourceUtils.findMaxId
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.id.N5IdService
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.alert
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.confirmation
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.setButtonText
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.function.LongPredicate
import kotlin.Throws
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
		val alert = confirmation().apply {
			headerText = "Define label-to-block-lookup for on-the-fly mesh generation"
			dialogPane.content = TextArea(
				"""
					Could not deserialize label-to-block-lookup for dataset `$group` in N5 container at `${reader?.uri}` that is required for on-the-fly mesh generation. 
					If you are not interested in 3D meshes, press cancel. Otherwise, press OK. 
					Generating meshes on the fly will be slow as the sparsity of objects cannot be utilized.
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

			else -> LabelBlockLookupNoBlocks()
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
			setButtonText(ButtonType.OK to "_Ok", ButtonType.CANCEL to "_Cancel")
			headerText = "maxId not specified in dataset."
		}
		val ta = TextArea().apply {
			text = """
				Could not read maxId attribute from dataset '$dataset'' in container '$n5'. 
				You can specify the max id manually, or read it from the data set (this can take a long time if your data is big).
				Alternatively, press cancel to load the data set without an id service. 
				Fragment-segment-assignments and selecting new (wrt to the data) labels require an id service 
				and will not be available if you press cancel.
			""".trimIndent()
			prefColumnCount *= 2
			prefRowCount *= 2
			isEditable = false
			isWrapText = true
		}
		val maxIdField = NumberField.longField(
			Label.Companion.INVALID,
			LongPredicate { v: Long -> true },
			ObjectField.SubmitOn.ENTER_PRESSED,
			ObjectField.SubmitOn.FOCUS_LOST
		)
		val maxIdIsInvalid = maxIdField.valueProperty().lessThan(0L)
		val task = SimpleObjectProperty<Job?>()
		val isNotRunning = task.isNull
		val cannotClickOk = task.isNotNull.or(maxIdIsInvalid)
		maxIdField.textField.editableProperty().bind(isNotRunning)
		alert.dialogPane.lookupButton(ButtonType.OK).disableProperty().bind(cannotClickOk)
		val scanDataButtonText = "_Scan Data"
		val cancelButtonText = "_Cancel"
		val scanButton = Button(scanDataButtonText).apply {
			tooltip = Tooltip("").also {
				val buttonTextNoMnemonics = textProperty().map { it?.replace("_", "") }
				it.textProperty().bind(buttonTextNoMnemonics)
			}
			prefWidth = 100.0
		}

		val initialValue: LongProperty = SimpleLongProperty(maxIdField.valueProperty().get())
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
			initialValue.set(maxIdField.valueProperty().get())
			val progress = AtomicDouble(0.0)
			val maxTracker = AtomicLong(Label.INVALID)

			val count = SimpleIntegerProperty(0)
			val findMaxIdJob = CoroutineScope(Dispatchers.Default).launch {

				(n5 as? GsonKeyValueN5Reader)?.let {

					val blockGrid = n5.getDatasetAttributes(dataset).run {
						CellGrid(dimensions, blockSize)
					}
					val numBlocks = blockGrid.cellIntervals().size()
					count.subscribe { it -> statusBar.progress = it.toDouble() / numBlocks.toDouble() }

					var dataSource = source.getDataSource(0, 0)
					N5Helpers.forEachBlockExists(it, dataset ?: "", count) { block ->
						val id = findMaxId(dataSource.interval(block))
						maxTracker.getAndUpdate { max -> maxOf(max, id) }
					}
				} ?: run {
					val maxId = findMaxId(source, progress) { localMax ->
						maxTracker.getAndUpdate { max -> maxOf(max, localMax) }
					}
					maxTracker.set(maxId)
				}
			}
			findMaxIdJob.invokeOnCompletion { cause ->
				when (cause) {
					is CancellationException -> {
						val initialValue = initialValue.get()
						LOG.info { "Setting next id field ${maxIdField.valueProperty()} to initial value $initialValue" }
						maxIdField.valueProperty().set(initialValue)
						statusBar.progress = 0.0
					}

					null -> {
						InvokeOnJavaFXApplicationThread {
							maxIdField.value = maxTracker.get()
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
			LOG.info("Setting next id field {} to initial value {}", maxIdField.valueProperty(), initialValue)
			maxIdField.valueProperty().set(initialValue.get())
			statusBar.progress = 0.0
			null
		}

		val actionCycle = sequence {
			while (true) {
				yield(runOnScanData to cancelButtonText)
				yield(runOnCancel to scanDataButtonText)
			}
		}.iterator()

		var job : Job? = null
		scanButton.onAction = EventHandler {
			job?.cancel()
			val (action, nextActionText) = actionCycle.next()
			scanButton.text = nextActionText
			job = action()
		}

		val maxIdBox = HBox(Label("Max Id:"), maxIdField.textField, statusBar).apply {
			alignment = Pos.CENTER
			HBox.setHgrow(maxIdField.textField, Priority.ALWAYS)
		}

		alert.dialogPane.content = VBox(ta, maxIdBox)

		if (alert.showAndWait().getOrNull() == ButtonType.OK) {
			val maxId = maxIdField.valueProperty().get() + 1
			n5.setAttribute(dataset, "maxId", maxId)
			return N5IdService(n5, dataset, maxId)
		} else {
			task.get()?.cancel()
			return IdService.IdServiceNotProvided()
		}
	}
}