package org.janelia.saalfeldlab.paintera.ui

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Window
import kotlinx.coroutines.*
import net.imglib2.Dimensions
import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.type.numeric.IntegerType
import net.imglib2.util.Intervals
import org.controlsfx.control.StatusBar
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.ui.NumberField.Companion.longField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.Companion.invoke
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.LockFile.UnableToCreateLock
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.ProjectDirectory
import org.janelia.saalfeldlab.paintera.Version
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.id.IdService.IdServiceNotProvided
import org.janelia.saalfeldlab.paintera.id.N5IdService
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts.initOwnerWithDefault
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.interval
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.*
import kotlin.Throws
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt


private typealias Imglib2Label = org.janelia.saalfeldlab.labels.Label

object PainteraAlerts {
	private val LOG = KotlinLogging.logger { }

	/**
	 *
	 *
	 * @param type type of alert
	 * @param isResizable set to `true` if dialog should be resizable
	 * @return [Alert] with the title set to [Constants.NAME]
	 */
	@JvmStatic
	@JvmOverloads
	fun alert(type: AlertType?, isResizable: Boolean = true) = runBlocking {
		InvokeOnJavaFXApplicationThread {
			Alert(type)
		}.run {
			invokeOnCompletion { cause ->
				cause?.let { LOG.error(it) { "Could not create alert" } }
			}
			await()
		}
	}.also { alert ->
		alert.title = Constants.NAME
		alert.isResizable = isResizable

		/* Keep the alert on top */
		alert.initAppDialog()
	}

	/**
	 * initOwner as specified by [initOwnerWithDefault].

	 * initModality with [Modality.APPLICATION_MODAL].
	 *
	 * @receiver initOwner and initModality for
	 * @param owner  to init owner and modality with
	 * @param modality to initModality with
	 */
	@JvmStatic
	@JvmOverloads
	fun Dialog<*>.initAppDialog(owner: Window? = null, modality: Modality? = Modality.APPLICATION_MODAL) {
		initOwnerWithDefault(owner)
		initModality(modality)
		Paintera.registerStylesheets(dialogPane.scene)
	}


	/**
	 * If owner is provided, use it. Otherwise, fallback vai [.initOwner]
	 *
	 * @receiver to init owner for
	 * @param owner to initOwner with
	 */
	@JvmStatic
	@JvmOverloads
	fun Dialog<*>.initOwnerWithDefault(owner: Window? = null) {
		(owner ?: Window.getWindows().firstOrNull())?.let { this.initOwner(it) }
	}

	private fun Dialog<*>.setButtonText(vararg buttonToText: Pair<ButtonType, String?>) = buttonToText.toMap()
		.filterValues { it != null }
		.forEach { (buttonType, text) ->
			(dialogPane.lookupButton(buttonType) as Button).setText(text)
		}

	@JvmStatic
	@JvmOverloads
	fun confirmation(
		okButtonText: String? = null,
		cancelButtonText: String? = null,
		isResizable: Boolean = true,
		window: Window? = null,
	) = alert(AlertType.CONFIRMATION, isResizable).apply {
		setButtonText(ButtonType.OK to okButtonText, ButtonType.CANCEL to cancelButtonText)
		initAppDialog(window)
	}

	@JvmStatic
	@JvmOverloads
	fun information(
		okButtonText: String? = null,
		isResizable: Boolean = true,
		window: Window? = null,
	) = alert(AlertType.INFORMATION, isResizable).apply {
		setButtonText(ButtonType.OK to okButtonText)
		initAppDialog(window)
	}

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
		}
		val ta = TextArea(
			String.format(
				"Could not deserialize label-to-block-lookup for dataset `%s' in N5 container `%s' " +
						"that is required for on the fly mesh generation. " +
						"If you are not interested in 3D meshes, press cancel. Otherwise, press OK. Generating meshes on the fly will be slow " +
						"as the sparsity of objects can not be utilized.", group, reader
			)
		)
		ta.setEditable(false)
		ta.setWrapText(true)
		alert.getDialogPane().setContent(ta)
		val bt = alert.showAndWait()
		if (bt.isPresent() && ButtonType.OK == bt.get()) {
			val grids = source.getGrids()
			val dims = arrayOfNulls<LongArray>(grids.size)
			val blockSizes = arrayOfNulls<IntArray>(grids.size)
			for (i in grids.indices) {
				dims[i] = grids[i]!!.getImgDimensions()
				blockSizes[i] = IntArray(grids[i]!!.numDimensions())
				grids[i]!!.cellDimensions(blockSizes[i])
			}
			LOG.debug("Returning block lookup returning all blocks.")
			return LabelBlockLookupAllBlocks(dims, blockSizes)
		} else {
			return LabelBlockLookupNoBlocks()
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

		val alert: Alert = alert(AlertType.CONFIRMATION).apply {
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
			isEditable = false
			isWrapText = true
		}
		val maxIdField = longField(
			Imglib2Label.INVALID,
			LongPredicate { v: Long -> true },
			ObjectField.SubmitOn.ENTER_PRESSED,
			ObjectField.SubmitOn.FOCUS_LOST
		)
		val isValidMaxId = maxIdField.valueProperty().greaterThanOrEqualTo(0L)
		val task = SimpleObjectProperty<ThreadWithCancellation?>().apply {
			subscribe { old, new ->
				old?.cancelAndJoin()
				new?.start()
			}
		}
		val isRunning = task.isNotNull()
		val isNotRunning = isRunning.not()
		val cannotClickOk = isRunning.or(isValidMaxId.not())
		maxIdField.textField.editableProperty().bind(isNotRunning)
		alert.dialogPane.lookupButton(ButtonType.OK).disableProperty().bind(cannotClickOk)
		val buttonTexts = arrayOf<String?>("_Scan Data", "_Abort")
		val currentIndex: IntegerProperty = SimpleIntegerProperty(0)
		val scanButton = Button(buttonTexts[0])
		scanButton.setTooltip(Tooltip(""))
		val buttonTextNoMnemonics = Bindings.createStringBinding(Callable { scanButton.getText().replace("_", "") }, scanButton.textProperty())
		scanButton.getTooltip().textProperty().bind(buttonTextNoMnemonics)
		scanButton.setPrefWidth(100.0)
		val initialValue: LongProperty = SimpleLongProperty(maxIdField.valueProperty().get())

		val statusBar = StatusBar().apply {
			prefWidth = 220.0
			graphic = scanButton
			text = ""
			tooltip = Tooltip("")
			val percentProgress = progressProperty().multiply(100.0)
			val percentProgressInt = Bindings.createIntegerBinding(Callable { percentProgress.get().roundToInt() }, percentProgress)
			val progressString = Bindings.createStringBinding(Callable { String.format("%d%%", percentProgressInt.get()) }, percentProgressInt)
			tooltip.textProperty().bind(progressString)
		}

		val runOnScanData = {
			initialValue.set(maxIdField.valueProperty().get())
			val t = ThreadWithCancellation(Consumer { wasCanceled: AtomicBoolean? ->
				findMaxId(
					source,
					0,
					LongConsumer { maxId: Long ->
						InvokeOnJavaFXApplicationThread {
							task.get()?.takeIf { !it.wasCancelled() }.let {
								val max = maxIdField.valueProperty().get().takeIf { it >= maxId } ?: maxId
								maxIdField.valueProperty().set(max)
							}
						}
					},
					wasCanceled!!,
					DoubleConsumer { p: Double -> invoke(Runnable { statusBar.setProgress(p) }) })
				if (!wasCanceled.get()) {
					currentIndex.set(0)
					invoke(Runnable {
						scanButton.text = buttonTexts[0]
						task.set(null)
					})
				}
			})
			t.setDaemon(true)
			t.setName(String.format("find-max-id-for-%s-in-%s", n5, dataset))
			task.set(t)
		}

		val runOnAbort = {
			task.set(null)
			LOG.info("Setting next id field {} to initial value {}", maxIdField.valueProperty(), initialValue)
			maxIdField.valueProperty().set(initialValue.get())
			statusBar.progress = 0.0
		}

		val actions = arrayOf(runOnScanData, runOnAbort)

		scanButton.onAction = EventHandler { e: ActionEvent? ->
			val action = actions[currentIndex.get()]
			currentIndex.set((currentIndex.get() + 1) % actions.size)
			scanButton.text = buttonTexts[currentIndex.get()]
			action()
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
			task.set(null)
			return IdServiceNotProvided()
		}
	}

	@JvmOverloads
	fun ignoreLockFileDialog(
		projectDirectory: ProjectDirectory,
		directory: File?,
		cancelButtontext: String? = "_Cancel",
		logFailure: Boolean = true,
	): Boolean {
		val useItProperty = SimpleBooleanProperty(true)
		val alert = confirmation("_Ignore Lock", cancelButtontext).apply {

			headerText = "Paintera project locked"
			contentText = """
				Paintera project at '$directory' is currently locked.
				A project is locked if it is accessed by a currently running Paintera instance or a Paintera instance did not terminate properly, in which case you may ignore the lock.
				Please make sure that no currently running Paintera instances access the project directory to avoid inconsistent project files.
			""".trimIndent()
		}
		try {
			projectDirectory.setDirectory(directory, Function { it: UnableToCreateLock? ->
				useItProperty.set(alert.showAndWait().getOrNull() == ButtonType.OK)
				useItProperty.get()
			})
		} catch (e: UnableToCreateLock) {
			if (logFailure) {
				LOG.error(e) { "Unable to ignore lock file" }
				exceptionAlert(Constants.NAME, "Unable to ignore lock file", e).show()
			}
			return false
		} catch (e: IOException) {
			if (logFailure) {
				LOG.error(e) { "Unable to ignore lock file" }
				exceptionAlert(Constants.NAME, "Unable to ignore lock file", e).show()
			}
			return false
		}
		return useItProperty.get()
	}

	fun versionDialog(): Alert {
		val versionField = TextField(Version.VERSION_STRING).apply {
			isEditable = false
			tooltip = Tooltip(text)
			HBox.setHgrow(this, Priority.ALWAYS)
		}
		val versionBox = HBox(Label("Paintera Version"), versionField).apply {
			alignment = Pos.CENTER
		}
		return information(isResizable = false).apply {
			dialogPane.content = versionBox
			headerText = "Paintera Version"
		}

	}

	@Deprecated("")
	fun askConvertDeprecatedStatesShowAndWait(
		choiceProperty: BooleanProperty,
		rememberChoiceProperty: BooleanProperty,
		deprecatedStateType: Class<*>,
		convertedStateType: Class<*>,
		datasetDescriptor: Any?,
	): Boolean {
		if (rememberChoiceProperty.get()) return choiceProperty.get()
		val alert = askConvertDeprecatedStates(rememberChoiceProperty, deprecatedStateType, convertedStateType, datasetDescriptor)
		return if (alert.showAndWait().getOrNull() == ButtonType.OK) {
			choiceProperty.value = true
			true
		} else
			false
	}

	@Deprecated("")
	fun askConvertDeprecatedStates(
		rememberChoiceProperty: BooleanProperty?,
		deprecatedStateType: Class<*>,
		convertedStateType: Class<*>,
		datasetDescriptor: Any?,
	): Alert {
		val alert = confirmation("_Update", "_Skip", true, null)
		val message = TextArea(
			"""
				Dataset '$datasetDescriptor' was opened in a deprecated format (${deprecatedStateType.simpleName}). 
				Paintera can try to convert and update the dataset into a new format (${convertedStateType.simpleName}) that supports relative data locations. 
				The new format is incompatible with Paintera versions 0.21.0 and older but updating datasets is recommended. 
				Backup files are generated for the Paintera files as well as for any dataset attributes that may have been modified during the process.
			""".trimIndent()
		)
		message.setWrapText(true)
		val rememberChoice = CheckBox("_Remember choice for all datasets in project")
		rememberChoice.selectedProperty().bindBidirectional(rememberChoiceProperty)
		alert.setHeaderText("Update deprecated data set")
		alert.getDialogPane().setContent(VBox(message, rememberChoice))
		return alert
	}

	private fun findMaxId(
		source: DataSource<out IntegerType<*>, *>,
		level: Int,
		maxIdTracker: LongConsumer,
		cancel: AtomicBoolean,
		progressTracker: DoubleConsumer,
	) {
		val rai: RandomAccessibleInterval<out IntegerType<*>> = source.getDataSource(0, level)
		val totalNumVoxels = ReadOnlyLongWrapper(Intervals.numElements(rai))
		maxIdTracker.accept(Imglib2Label.INVALID)
		val numProcessedVoxels = SimpleLongProperty(0).apply {
			subscribe { numProcessed -> progressTracker.accept(numProcessed.toDouble() / totalNumVoxels.doubleValue()) }
		}

		val blockSize = blockSizeFromRai(rai)
		val intervals = Grids.collectAllContainedIntervals(Intervals.minAsLongArray(rai), Intervals.maxAsLongArray(rai), blockSize)

		runBlocking {
			CoroutineScope(Dispatchers.Default).launch {
				for (interval in intervals) {
					launch {
						cancel.get().takeUnless { it } ?: cancel()
						ensureActive()
						val maxId = findMaxId(rai.interval(interval))
						cancel.get().takeUnless { it } ?: cancel()
						ensureActive()
						synchronized(numProcessedVoxels) {
							numProcessedVoxels.set(numProcessedVoxels.get() + Intervals.numElements(interval))
							maxIdTracker.accept(maxId)
						}
					}
				}
			}.join()
		}
	}

	private fun findMaxId(rai: RandomAccessibleInterval<out IntegerType<*>>): Long {
		var maxId: Long = Imglib2Label.INVALID
		for (t in rai) {
			val id = t.integerLong
			if (id > maxId) maxId = id
		}
		return maxId
	}

	private fun blockSizeFromRai(rai: RandomAccessibleInterval<*>): IntArray {
		if (rai is AbstractCellImg<*, *, *, *>) {
			val cellGrid = rai.cellGrid
			val blockSize = IntArray(cellGrid.numDimensions())
			cellGrid.cellDimensions(blockSize)
			LOG.debug("{} is a cell img with block size {}", rai, blockSize)
			return blockSize
		}
		val argMaxDim = argMaxDim(rai)
		val blockSize = Intervals.dimensionsAsIntArray(rai)
		blockSize[argMaxDim] = 1
		return blockSize
	}

	private fun argMaxDim(dims: Dimensions): Int {
		var max: Long = -1
		var argMax = -1
		for (d in 0..<dims.numDimensions()) {
			if (dims.dimension(d) > max) {
				max = dims.dimension(d)
				argMax = d
			}
		}
		return argMax
	}

	private class ThreadWithCancellation(private val task: Consumer<AtomicBoolean?>) : Thread() {
		private val cancelled = AtomicBoolean(false)

		override fun run() {
			if (!cancelled.get()) this.task.accept(this.cancelled)
		}

		fun cancel() {
			this.cancelled.set(true)
		}

		@Throws(InterruptedException::class)
		fun cancelAndJoin() {
			cancel()
			join()
		}

		fun wasCancelled(): Boolean {
			return this.cancelled.get()
		}
	}
}
