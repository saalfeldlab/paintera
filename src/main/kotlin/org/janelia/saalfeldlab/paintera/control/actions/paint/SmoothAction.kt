package org.janelia.saalfeldlab.paintera.control.actions.paint

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.value.ChangeListener
import javafx.event.ActionEvent
import javafx.event.Event
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import net.imglib2.FinalInterval
import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.algorithm.convolution.kernel.Kernel1D
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution
import net.imglib2.algorithm.gauss3.Gauss3
import net.imglib2.algorithm.lazy.Lazy
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.converter.Converters
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.img.cell.CellGrid
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import net.imglib2.view.BundleView
import net.imglib2.view.Views
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Style.ADD_GLYPH
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothActionVerifiedState.Companion.verifyState
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode.statePaintContext
import org.janelia.saalfeldlab.paintera.control.tools.paint.StatePaintContext
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.util.extendZero
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.union
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.*
import kotlin.collections.set
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.properties.Delegates
import kotlin.reflect.KMutableProperty0
import net.imglib2.type.label.Label as Imglib2Label


open class MenuAction(val label: String) : Action<Event>(Event.ANY) {


	init {
		keysDown = null
		name = label
	}

	val menuItem by lazy {
		MenuItem(label).also {
			it.onAction = EventHandler { this(it) }
			it.isDisable = isValid(null)
		}
	}

}

class SmoothActionVerifiedState {
	internal lateinit var labelSource: ConnectomicsLabelState<*, *>
	internal lateinit var paintContext: StatePaintContext<*, *>
	internal var mipMapLevel by Delegates.notNull<Int>()


	fun <E : Event> Action<E>.verifyState() {
		verify(::labelSource, "Label Source is Active") { paintera.currentSource as? ConnectomicsLabelState<*, *> }
		verify(::paintContext, "Paint Label Mode has StatePaintContext") { statePaintContext }
		verify(::mipMapLevel, "Viewer is Focused") { paintera.activeViewer.get()?.state?.bestMipMapLevel }
	}

	fun <E : Event, T> Action<E>.verify(property: KMutableProperty0<T>, description: String, stateProvider: () -> T?) {
		verify(description) { stateProvider()?.also { state -> property.set(state) } != null }
	}

	companion object {
		fun <E : Event> Action<E>.verifyState(state: SmoothActionVerifiedState) {
			state.run { verifyState() }
		}
	}
}

object SmoothAction : MenuAction("_Smooth") {

	private fun newConvolutionExecutor() : ThreadPoolExecutor {
		val threads = Runtime.getRuntime().availableProcessors()
		return ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
	}

	private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

	private val smoothScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
	private var smoothTask: UtilityTask<Unit>? = null
	private var convolutionExecutor = newConvolutionExecutor()

	private val replacementLabelProperty = SimpleLongProperty(0)
	private val replacementLabel by replacementLabelProperty.nonnullVal()

	private val kernelSizeProperty = SimpleDoubleProperty()
	private val kernelSize by kernelSizeProperty.nonnullVal()

	private var finalizeSmoothing = false

	private lateinit var updateSmoothMask: ((Boolean) -> Unit)

	private var sourceSmoothInterval: Interval? = null
	private var globalSmoothInterval: RealInterval? = null

	private val state = SmoothActionVerifiedState()

	private val SmoothActionVerifiedState.defaultKernelSize: Double
		get() {
			val levelResolution = getLevelResolution(mipMapLevel)
			val min = levelResolution.min()
			val max = levelResolution.max()
			return min + (max - min) / 2.0
		}

	init {
		verifyState(state)
		verify("Paint Label Mode is Active") { paintera.currentMode is PaintLabelMode }
		verify("Paintera is not disabled") { !paintera.baseView.isDisabledProperty.get() }
		verify("Mask is in Use") { !state.paintContext.dataSource.isMaskInUseBinding().get() }
		onAction {
			finalizeSmoothing = false
			/* Set lateinit values */
			state.run {
				kernelSizeProperty.unbind()
				kernelSizeProperty.set(defaultKernelSize)
				startSmoothTask()
				showSmoothDialog()
			}
		}
	}


	private val AffineTransform3D.resolution
		get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])

	private fun SmoothActionVerifiedState.showSmoothDialog() {
		Dialog<Boolean>().apply {
			Paintera.registerStylesheets(dialogPane)
			dialogPane.buttonTypes += ButtonType.APPLY
			dialogPane.buttonTypes += ButtonType.CANCEL
			title = name

			val levelResolution: DoubleArray = getLevelResolution(mipMapLevel)
			val replacementLabelField = NumberField.longField(Imglib2Label.BACKGROUND, { it >= Imglib2Label.BACKGROUND }, *SubmitOn.values())
			replacementLabelProperty.bind(replacementLabelField.valueProperty())

			val nextIdButton = Button().apply {
				styleClass += ADD_GLYPH
				graphic = FontAwesome[FontAwesomeIcon.PLUS, 2.0]
				onAction = EventHandler { replacementLabelField.valueProperty().set(labelSource.idService.next()) }
				tooltip = Tooltip("Next New ID")
			}
			val minRes = levelResolution.min()
			val minKernelSize = floor(minRes / 2)
			val maxKernelSize = levelResolution.max() * 10
			val initialKernelSize = defaultKernelSize
			val kernelSizeSlider = Slider(log10(minKernelSize), log10(maxKernelSize), log10(initialKernelSize))
			val kernelSizeField = NumberField.doubleField(initialKernelSize, { it > 0.0 }, *SubmitOn.values())

			/* slider sets field */
			kernelSizeSlider.valueProperty().addListener { _, _, sliderVal ->
				kernelSizeField.valueProperty().set(10.0.pow(sliderVal.toDouble()).roundToLong().toDouble())
			}
			/* field sets slider*/
			kernelSizeField.valueProperty().addListener { _, _, fieldVal ->
				/* Let the user go over if they want to explicitly type a larger number in the field.
				* otherwise, set the slider */
				if (fieldVal.toDouble() <= maxKernelSize)
					kernelSizeSlider.valueProperty().set(log10(fieldVal.toDouble()))
			}

			val prevStableKernelSize = SimpleDoubleProperty(initialKernelSize)

			val stableKernelSizeBinding = Bindings
				.`when`(kernelSizeSlider.valueChangingProperty().not())
				.then(kernelSizeField.valueProperty())
				.otherwise(prevStableKernelSize)

			/* size property binds field */
			kernelSizeProperty.bind(stableKernelSizeBinding)
			val sizeChangeListener = ChangeListener<Number> { _, _, size -> prevStableKernelSize.value = size.toDouble() }
			kernelSizeProperty.addListener(sizeChangeListener)

			dialogPane.content = VBox(10.0).apply {
				val replacementIdLabel = Label("Replacement Label")
				val kernelSizeLabel = Label("Kernel Size (phyiscal units)")
				replacementIdLabel.alignment = Pos.BOTTOM_RIGHT
				kernelSizeLabel.alignment = Pos.BOTTOM_RIGHT
				children += HBox(10.0, replacementIdLabel, nextIdButton, replacementLabelField.textField).also {
					it.disableProperty().bind(paintera.baseView.isDisabledProperty)
					it.cursorProperty().bind(paintera.baseView.node.cursorProperty())
				}
				children += Separator(Orientation.HORIZONTAL)
				children += HBox(10.0, kernelSizeLabel, kernelSizeField.textField)
				children += HBox(10.0, kernelSizeSlider)
				HBox.setHgrow(kernelSizeLabel, Priority.NEVER)
				HBox.setHgrow(kernelSizeSlider, Priority.ALWAYS)
				HBox.setHgrow(kernelSizeField.textField, Priority.ALWAYS)
				HBox.setHgrow(replacementLabelField.textField, Priority.ALWAYS)
			}
			dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
				applyButton.disableProperty().bind(paintera.baseView.isDisabledProperty)
				applyButton.cursorProperty().bind(paintera.baseView.node.cursorProperty())
				applyButton.addEventFilter(ActionEvent.ACTION) { _ -> finalizeSmoothing = true }
			}
			val cleanupOnDialogClose = {
				smoothTask?.cancel()
				kernelSizeProperty.removeListener(sizeChangeListener)
				kernelSizeProperty.unbind()
				replacementLabelProperty.unbind()
				close()
			}
			dialogPane.lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION) { _ -> cleanupOnDialogClose() }
			dialogPane.scene.window.setOnCloseRequest { cleanupOnDialogClose() }
		}.show()
	}

	private fun SmoothActionVerifiedState.getLevelResolution(level: Int): DoubleArray {
		val metadataScales = ((labelSource.backend as? SourceStateBackendN5<*, *>)?.getMetadataState() as? MultiScaleMetadataState)?.scaleTransforms?.get(level)
		val resFromRai = (labelSource.backend as? RandomAccessibleIntervalBackend<*, *>)?.resolutions
		return when {
			level == 0 -> labelSource.resolution
			metadataScales != null -> metadataScales.resolution
			resFromRai != null -> resFromRai[level]
			else -> doubleArrayOf(1.0, 1.0, 1.0)
		}
	}

	private val smoothingProperty = SimpleBooleanProperty("Smoothing", "Smooth Action is Running", false)

	/**
	 * Smoothing flag to indicate if a smoothing task is actively running.
	 * Bound to Paintera.isDisabled, so if set to `true` then Paintera will
	 * be "busy" until set to `false` again. This is to block unpermitted state
	 * changes while waiting for smoothing to finish.
	 */
	private var smoothing by smoothingProperty.nonnull()

	private fun SmoothActionVerifiedState.startSmoothTask() {
		val prevScales = paintera.activeViewer.get()!!.screenScales

		var resmooth = false
		val resmoothOnKernelSizeChange: ChangeListener<in Number> = ChangeListener { _, _, _ ->
			smoothScope.coroutineContext.cancelChildren(CancellationException("Kernel Size Changed"))
			convolutionExecutor.shutdownNow()
			convolutionExecutor = newConvolutionExecutor()
			resmooth = true
		}

		smoothTask = Tasks.createTask { task ->
			paintera.baseView.disabledPropertyBindings[task] = smoothingProperty
			kernelSizeProperty.addListener(resmoothOnKernelSizeChange)
			paintera.baseView.orthogonalViews().setScreenScales(doubleArrayOf(prevScales[0]))
			smoothing = true
			initializeSmoothLabel()
			smoothing = false
			while (!finalizeSmoothing && !task.isCancelled) {
				if (resmooth) {
					resmooth = false
					smoothing = true
					updateSmoothMask(true)
					smoothing = false
				} else {
					Thread.sleep(100)
				}
			}
			println("Task Loop End")
			if (!task.isCancelled)
				updateSmoothMask(false)
		}.onCancelled { _, _ ->
			paintContext.dataSource.resetMasks()
			paintera.baseView.orthogonalViews().requestRepaint(globalSmoothInterval)
			paintera.baseView.disabledPropertyBindings -= smoothTask
			println("Cancelled")
		}.onSuccess { _, _ ->
			var refreshAfterApplyingMask: ChangeListener<Boolean>? = null
			refreshAfterApplyingMask = ChangeListener<Boolean> { obs, _, isApplyingMask ->
				if (!isApplyingMask) {
					paintera.baseView.orthogonalViews().requestRepaint(globalSmoothInterval)
					statePaintContext?.refreshMeshes?.invoke()
					obs.removeListener(refreshAfterApplyingMask!!)
					paintera.baseView.disabledPropertyBindings -= smoothTask
				}
			}
			val maskedSource = paintContext.dataSource
			maskedSource.isApplyingMaskProperty.addListener(refreshAfterApplyingMask)
			val mask = maskedSource.currentMask
			maskedSource.applyMask(mask, sourceSmoothInterval) { it.integerLong >= 0 }
			println("Completed")
		}.onEnd {
			kernelSizeProperty.removeListener(resmoothOnKernelSizeChange)
			paintera.baseView.orthogonalViews().setScreenScales(prevScales)
			convolutionExecutor.shutdownNow()
			println("Done")
		}.submit()
	}

	private fun SmoothActionVerifiedState.initializeSmoothLabel() {

		val maskedSource = paintContext.dataSource as MaskedSource<*, *>
		val labels = paintContext.selectedIds.activeIds


		/* Read from the labelBlockLookup (if already persisted) */
		val blocksFromSource = labels.toArray().flatMap { paintContext.getBlocksForLabel(mipMapLevel, it).toList() }

		/* Read from canvas access (if in canvas) */
		val cellGrid = maskedSource.getCellGrid(0, mipMapLevel)
		val cellIntervals = cellGrid.cellIntervals().randomAccess()
		val cellPos = LongArray(cellGrid.numDimensions())
		val blocksFromCanvas = let {
			labels.toArray().flatMap {
				maskedSource.getModifiedBlocks(mipMapLevel, it).toArray().map { block ->
					cellGrid.getCellGridPositionFlat(block, cellPos)
					FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
				}
			}
		}

		val blocksWithLabel = blocksFromSource + blocksFromCanvas
		if (blocksWithLabel.isEmpty()) return

		val sourceLabels = Converters.convert(
			maskedSource.getReadOnlyDataBackground(0, mipMapLevel),
			{ source, output -> output.set(source.realDouble.toLong()) },
			UnsignedLongType()
		)

		val collapsedLabelStack = Views.collapse(
			Views.stack(sourceLabels, maskedSource.getReadOnlyDataCanvas(0, mipMapLevel))
		)

		val virtualLabelMask = Converters.convert(collapsedLabelStack, { input, output ->
			for (i in 1 downTo 0) {
				val labelVal = input.get(i.toLong()).get()
				if (labelVal != Imglib2Label.INVALID) {
					output.set(if (labelVal in labels) 1.0 else 0.0)
					break
				}
			}
		}, DoubleType())

		val labelMask = Lazy.generate(virtualLabelMask, cellGrid.cellDimensions, DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE)) { output ->
			val input = virtualLabelMask.interval(output)
			LoopBuilder.setImages(input, output).multiThreaded().forEachPixel { inVal, outVal -> if (inVal.get() == 1.0) outVal.set(1.0) }
		}

		var labelRoi: Interval = FinalInterval(blocksWithLabel[0])
		blocksWithLabel.forEach { labelRoi = labelRoi union it }

		updateSmoothMask = { preview -> smoothMask(labelMask, cellGrid, blocksWithLabel, preview) }
		updateSmoothMask(true)
	}

	private fun SmoothActionVerifiedState.pruneBlock(blocksWithLabel: List<Interval>): List<Interval> {
		val viewsInSourceSpace = viewerIntervalsInSourceSpace()

		/* remove any blocks that don't intersect with them*/
		return blocksWithLabel
			.filter { block -> viewsInSourceSpace.firstOrNull { viewer -> !Intervals.isEmpty(viewer.intersect(block)) } != null }
			.toList()
	}

	private fun SmoothActionVerifiedState.viewerIntervalsInSourceSpace(): Array<FinalRealInterval> {
		/* get viewer screen intervals for each orthognal view in source space*/
		val viewerAndTransforms = paintera.baseView.orthogonalViews().viewerAndTransforms()
		val viewsInSourceSpace = viewerAndTransforms
			.map {
				val globalToViewerTransform = AffineTransform3D()
				it.viewer().state.getViewerTransform(globalToViewerTransform)
				val width = it.viewer().width
				val height = it.viewer().height
				val screenInterval = FinalInterval(width.toLong(), height.toLong(), 1L)
				val sourceToGlobal = AffineTransform3D()
				labelSource.getDataSource().getSourceTransform(0, mipMapLevel, sourceToGlobal)
				val viewerToSource = sourceToGlobal.inverse().copy().concatenate(globalToViewerTransform.inverse())
				viewerToSource.estimateBounds(screenInterval)
			}.toTypedArray()
		return viewsInSourceSpace
	}

	private fun SmoothActionVerifiedState.smoothMask(labelMask: CachedCellImg<DoubleType, *>, cellGrid: CellGrid, blocksWithLabel: List<Interval>, preview: Boolean = false) {

		val blocksToSmoothOver = if (preview) pruneBlock(blocksWithLabel) else blocksWithLabel

		val levelResolution = getLevelResolution(mipMapLevel)
		val sigma = DoubleArray(3) { kernelSize / levelResolution[it] }

		val halfkernels = Gauss3.halfkernels(sigma)
		val symmetric = Kernel1D.symmetric(halfkernels)
		val convolution = SeparableKernelConvolution.convolution(*symmetric)
		if (convolutionExecutor.isShutdown) {
			 convolutionExecutor = newConvolutionExecutor()
		}
		convolution.setExecutor(convolutionExecutor)
		val smoothedImg = Lazy.generate(labelMask, cellGrid.cellDimensions, DoubleType(), AccessFlags.setOf(AccessFlags.VOLATILE)) {
			try {
				println("Start")
				convolution.process(labelMask.extendZero(), it)
			} catch (e : RejectedExecutionException) {
				e.printStackTrace()
				throw e
			}
			println("Finished")
		}

		val maskInfo = MaskInfo(0, mipMapLevel)
		paintContext.dataSource.resetMasks()
		setNewSourceMask(paintContext.dataSource, mipMapLevel, maskInfo)
		val mask = paintContext.dataSource.currentMask

		val viewerIntervalsInSource = viewerIntervalsInSourceSpace()

		val smoothOverInterval: suspend CoroutineScope.(RealInterval) -> Job = { slice ->
			launch {
				val labels = BundleView(labelMask).interval(slice).cursor()
				val smoothed = smoothedImg.interval(slice).cursor()
				val maskBundle = BundleView(mask.rai).interval(slice).cursor()

				while (smoothed.hasNext()) {
					ensureActive()
					//This is the slow one, check cancellation immediately before and after
					val smoothness: Double
					try {
						smoothness = smoothed.next().get()
					} catch (e: Exception) {
						val cancelled = CancellationException("Gaussian Convolution Shutdown", e)
						cancel(cancelled)
						throw cancelled
					}
					val labelVal = labels.next().get()
					val maskVal = maskBundle.next().get()
					if (smoothness < 0.5 && labelVal.get() == 1.0) {
						maskVal.setInteger(replacementLabel)
					} else if (maskVal.get() == replacementLabel) {
						maskVal.setInteger(Imglib2Label.INVALID)
					}
				}
			}
		}

		val maskedSource = paintContext.dataSource

		val runAllJobs = {
			runBlocking(smoothScope.coroutineContext) {
				val smoothJobs = mutableListOf<Job>()
				for (block in blocksToSmoothOver) {
					if (preview) {
						for (viewerInterval in viewerIntervalsInSource) {
							val slice = viewerInterval.intersect(block)
							if (Intervals.isEmpty(slice)) continue
							smoothJobs += smoothOverInterval(slice)
						}
					} else {
						smoothJobs += smoothOverInterval(block)
					}
				}
				println("Waiting...")
				while (smoothJobs.any { it.isActive }) {
					delay(50)
				}
				println("Waiting...")
			}
		}

		var labelRoi: Interval = FinalInterval(blocksToSmoothOver[0])
		blocksToSmoothOver.forEach { labelRoi = labelRoi union it }

		try {
			runAllJobs()
		} catch (c: CancellationException) {
			LOG.debug(c.message)
			convolutionExecutor.shutdownNow()
			paintContext.dataSource.resetMasks()
		}

		sourceSmoothInterval = labelRoi
		globalSmoothInterval = maskedSource.getSourceTransformForMask(maskInfo).estimateBounds(sourceSmoothInterval)
		paintera.baseView.orthogonalViews().requestRepaint(globalSmoothInterval)
	}

	private fun setNewSourceMask(maskedSource: MaskedSource<*, *>, level: Int, maskInfo: MaskInfo) {
		val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(level)
		val mask = SourceMask(maskInfo, store, volatileStore.rai, store.cache, volatileStore.invalidate) { store.shutdown() }
		maskedSource.setMask(mask) { it.integerLong >= 0 }
	}
}
