package org.janelia.saalfeldlab.paintera.control.actions.paint

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.ActionEvent
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.util.Subscription
import kotlinx.coroutines.*
import net.imglib2.FinalInterval
import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.algorithm.convolution.fast_gauss.FastGauss
import net.imglib2.algorithm.lazy.Lazy
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.img.cell.CellGrid
import net.imglib2.loops.LoopBuilder
import net.imglib2.parallel.Parallelization
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.onAction
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import net.imglib2.type.label.Label as Imglib2Label


object SmoothLabel : MenuAction("_Smooth...") {

	private fun newConvolutionExecutor(): ThreadPoolExecutor {
		val threads = Runtime.getRuntime().availableProcessors()
		return ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(), ThreadFactoryBuilder().setNameFormat("gaussian-smoothing-%d").build())
	}

	private val LOG = KotlinLogging.logger { }

	private var scopeJob: Job? = null
	private var smoothScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
	private var smoothJob: Deferred<List<Interval>?>? = null
	private var convolutionExecutor = newConvolutionExecutor()

	private val progressBarProperty = SimpleDoubleProperty()
	private val currentProgressBar by progressBarProperty.nonnull()

	private var finalizeSmoothing = false
	private var resmooth = false

	private lateinit var updateSmoothMask: suspend ((Boolean) -> List<RealInterval>)

	private var subscriptions: Subscription? = null

	init {
		verifyPermission(PaintActionType.Smooth, PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onAction(::SmoothLabelState) {
			subscriptions?.unsubscribe()
			replacementLabelProperty.subscribe { prev, next ->
				activateReplacementLabel(prev?.toLong(), next?.toLong())
			}.also { subscriptions = subscriptions?.and(it) ?: it }

			activateReplacementLabelProperty.subscribe { _, activate ->
				if (activate) activateReplacementLabel(0L, replacementLabelProperty.value)
				else activateReplacementLabel(replacementLabelProperty.value, 0L)
			}.also { subscriptions = subscriptions?.and(it) ?: it }

			startSmoothTask()
			showDialog()
		}
	}

	private fun SmoothLabelState.activateReplacementLabel(current: Long?, next: Long?) {
		val selectedIds = paintContext.selectedIds
		if (current != selectedIds.lastSelection) {
			current?.also { selectedIds.deactivate(it) }
		}
		next ?: return
		if (activateReplacementLabelProperty.value && next > 0L) {
			selectedIds.activateAlso(selectedIds.lastSelection, next)
		}
	}

	private fun SmoothLabelState.showDialog() {
		Dialog<Boolean>().apply {
			isResizable = true
			Paintera.registerStylesheets(dialogPane)
			dialogPane.buttonTypes += ButtonType.APPLY
			dialogPane.buttonTypes += ButtonType.CANCEL
			title = name?.replace("_", "")
			headerText = "Smooth Label"

			dialogPane.content = SmoothLabelUI(this@showDialog)

			val cleanupOnDialogClose = {
				scopeJob?.cancel()
				smoothJob?.cancel()
				kernelSizeProperty.unbind()
				replacementLabelProperty.unbind()
				close()
			}
			dialogPane.lookupButton(ButtonType.APPLY).also { applyButton ->
				applyButton.disableProperty().bind(paintera.baseView.isDisabledProperty.or(replacementLabelProperty.isNull))
				applyButton.cursorProperty().bind(paintera.baseView.node.cursorProperty())
				applyButton.addEventFilter(ActionEvent.ACTION) { event ->
					//So the dialog doesn't close until the smoothing is done
					event.consume()
					// but listen for when the smoothTask finishes
					smoothJob?.invokeOnCompletion { cause ->
						cause?.let {
							cleanupOnDialogClose()
						}
					}
					// indicate the smoothTask should try to apply the current smoothing mask to canvas
					progressProperty.value = 0.0
					finalizeSmoothing = true
				}
			}
			dialogPane.lookupButton(ButtonType.CANCEL).also { cancelButton ->
				cancelButton.disableProperty().bind(paintContext.dataSource.isApplyingMaskProperty())
				cancelButton.addEventFilter(ActionEvent.ACTION) { _ -> cleanupOnDialogClose() }
			}

			dialogPane.scene.window.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
				if (event.code == KeyCode.ESCAPE && (progressProperty.value < 1.0 || (scopeJob?.isActive == true))) {
					/* Cancel if still running */
					event.consume()
					cancelActiveSmoothing("Escape Pressed")
				}
			}

			dialogPane.scene.window.setOnCloseRequest {
				if (!paintContext.dataSource.isApplyingMaskProperty().get()) {
					cleanupOnDialogClose()
				}
			}
		}.show()
	}


	private val smoothingProperty = SimpleBooleanProperty("Smoothing", "Smooth Action is Running", false)

	/**
	 * Smoothing flag to indicate if a smoothing task is actively running.
	 * Bound to Paintera.isDisabled, so if set to `true` then Paintera will
	 * be "busy" until set to `false` again. This is to block unpermitted state
	 * changes while waiting for smoothing to finish.
	 */
	private var smoothing by smoothingProperty.nonnull()

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun SmoothLabelState.startSmoothTask() {
		val prevScales = viewer.screenScales
		val smoothTriggerListener = { reason: String ->
			{ _: Any? ->
				if (scopeJob?.isActive == true)
					cancelActiveSmoothing(reason)
				resmooth = true
			}
		}
		var smoothTriggerSubscription: Subscription = Subscription.EMPTY

		smoothJob = CoroutineScope(Dispatchers.Default).async {
			val kernelSizeChangeSubscription = kernelSizeProperty.subscribe(smoothTriggerListener("Kernel Size Changed"))
			val replacementLabelChangeSubscription = replacementLabelProperty.subscribe(smoothTriggerListener("Replacement Label Changed"))
			smoothTriggerSubscription.unsubscribe()
			smoothTriggerSubscription = kernelSizeChangeSubscription.and(replacementLabelChangeSubscription)
			paintera.baseView.orthogonalViews().setScreenScales(doubleArrayOf(prevScales[0]))
			smoothing = true
			initializeSmoothLabel()
			smoothing = false
			var intervals: List<Interval>? = emptyList()
			while (coroutineContext.isActive) {
				if (resmooth || finalizeSmoothing) {
					val preview = !finalizeSmoothing
					try {
						smoothing = true
						intervals = updateSmoothMask(preview).map { it.smallestContainingInterval }
						if (!preview) break
						else requestRepaintOverIntervals(intervals)
					} catch (_: CancellationException) {
						intervals = null
						paintContext.dataSource.resetMasks()
						paintera.baseView.orthogonalViews().requestRepaint()
					} finally {
						smoothing = false
						/* If any remain on the queue, shut it down */
						convolutionExecutor.queue.peek()?.let { convolutionExecutor.shutdown() }
						/* reset for the next loop */
						resmooth = false
						finalizeSmoothing = false
					}
				} else {
					delay(100)
				}
			}
			return@async intervals
		}.also { task ->
			paintera.baseView.disabledPropertyBindings[task] = smoothingProperty
			task.invokeOnCompletion { cause ->
				cause?.let {
					paintContext.dataSource.resetMasks()
					paintera.baseView.orthogonalViews().requestRepaint()
				} ?: task.getCompleted()?.let { intervals ->
					paintContext.dataSource.apply {
						val applyProgressProperty = SimpleDoubleProperty()
						applyProgressProperty.addListener { _, _, applyProgress -> progressProperty.value = applyProgress.toDouble() }
						applyMaskOverIntervals(currentMask, intervals, applyProgressProperty) { it >= 0 }
					}
					requestRepaintOverIntervals(intervals)
					paintContext.refreshMeshes()
				}

				paintera.baseView.disabledPropertyBindings -= task
				smoothTriggerSubscription.unsubscribe()
				paintera.baseView.orthogonalViews().setScreenScales(prevScales)
				convolutionExecutor.shutdown()
			}
		}
	}

	private fun SmoothLabelState.requestRepaintOverIntervals(intervals: List<Interval>? = null) {
		if (intervals.isNullOrEmpty()) {
			paintera.baseView.orthogonalViews().requestRepaint()
			return
		}
		val smoothedInterval = intervals.reduce(Intervals::union)
		val globalSmoothedInterval = paintContext.dataSource.getSourceTransformForMask(MaskInfo(0, 0)).estimateBounds(smoothedInterval)
		paintera.baseView.orthogonalViews().requestRepaint(globalSmoothedInterval)
	}

	private fun SmoothLabelState.cancelActiveSmoothing(reason: String) {
		convolutionExecutor.shutdown()
		scopeJob?.cancel(CancellationException(reason))
		progressProperty.value = 0.0
	}

	private fun SmoothLabelState.initializeSmoothLabel() {

		val maskedSource = paintContext.dataSource as MaskedSource<*, *>
		val labels = paintContext.selectedIds.activeIds


		/* Read from the labelBlockLookup (if already persisted) */
		val scale0 = 0

		val blocksWithLabel = maskedSource.blocksForLabels(scale0, labels.toArray())
		if (blocksWithLabel.isEmpty()) return

		val sourceImg = maskedSource.getReadOnlyDataBackground(0, scale0)
		val canvasImg = maskedSource.getReadOnlyDataCanvas(0, scale0)

		val source = sourceImg.convert(UnsignedLongType(Imglib2Label.INVALID)) { input, output ->
			output.set(input.realDouble.toLong())
		}
		val bundleSourceImg = BundleView(source).interval(sourceImg)

		val cellGrid = maskedSource.getCellGrid(0, scale0)
		val labelMask = DiskCachedCellImgFactory(DoubleType(0.0)).create(bundleSourceImg, { labelMaskChunk ->
			val sourceChunk = bundleSourceImg.interval(labelMaskChunk)
			val canvasChunk = canvasImg.interval(labelMaskChunk)

			LoopBuilder.setImages(canvasChunk, sourceChunk, labelMaskChunk).forEachPixel { canvasLabel, sourceBundle, maskVal ->
				val hasLabel = canvasLabel.get().let { label ->
					if (label != Imglib2Label.INVALID && label in labels)
						1.0
					else null
				} ?: sourceBundle.get().get().let { label ->
					if (label != Imglib2Label.INVALID && label in labels)
						1.0
					else null
				}
				hasLabel?.let { maskVal.set(it) }
			}
		}, DiskCachedCellImgOptions().cellDimensions(*cellGrid.cellDimensions))
		var labelRoi: Interval = FinalInterval(blocksWithLabel[0])
		blocksWithLabel.forEach { labelRoi = labelRoi union it }

		updateSmoothMask = { preview -> smoothMask(labelMask, cellGrid, blocksWithLabel, preview) }
		resmooth = true
	}


	@JvmStatic
	fun MaskedSource<*, *>.blocksForLabels(scale0: Int, labels: LongArray): List<Interval> {
		val sourceState = paintera.baseView.sourceInfo().getState(this) as ConnectomicsLabelState
		val blocksFromSource = labels.flatMap { sourceState.labelBlockLookup.read(LabelBlockLookupKey(scale0, it)).toList() }

		/* Read from canvas access (if in canvas) */
		val cellGrid = getCellGrid(0, scale0)
		val cellIntervals = cellGrid.cellIntervals().randomAccess()
		val cellPos = LongArray(cellGrid.numDimensions())
		val blocksFromCanvas = labels.flatMap {
			getModifiedBlocks(scale0, it).toArray().map { block ->
				cellGrid.getCellGridPositionFlat(block, cellPos)
				FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
			}
		}

		return blocksFromSource + blocksFromCanvas
	}

	private fun SmoothLabelState.getBlocksInView(blocksWithLabel: List<Interval>): List<RealInterval> {
		val viewsInSourceSpace = viewerIntervalsInSourceSpace()

		/* remove any blocks that don't intersect with them*/
		return blocksWithLabel
			.mapNotNull { block -> viewsInSourceSpace.firstOrNull { viewer -> !Intervals.isEmpty(viewer.intersect(block)) } }
			.toList()
	}

	private fun SmoothLabelState.viewerIntervalsInSourceSpace(): Array<FinalRealInterval> {
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
				labelSource.getDataSource().getSourceTransform(0, 0, sourceToGlobal)
				val viewerToSource = sourceToGlobal.inverse().copy().concatenate(globalToViewerTransform.inverse())
				viewerToSource.estimateBounds(screenInterval)
			}.toTypedArray()
		return viewsInSourceSpace
	}

	private suspend fun SmoothLabelState.smoothMask(labelMask: CachedCellImg<DoubleType, *>, cellGrid: CellGrid, blocksWithLabel: List<Interval>, preview: Boolean = false): List<RealInterval> {

		replacementLabelProperty.value ?: return emptyList()
		/* Just to show that smoothing has started */
		progressProperty.value = 0.0
		progressProperty.value = .05

		val intervalsToSmoothOver = if (preview) getBlocksInView(blocksWithLabel) else blocksWithLabel

		val scale0 = 0
		val levelResolution = getLevelResolution(scale0)
		val sigma = DoubleArray(3) { kernelSizeProperty.value / levelResolution[it] }

		if (convolutionExecutor.isShutdown)
			convolutionExecutor = newConvolutionExecutor()

		val convolution = FastGauss.convolution(sigma)
		if (convolutionExecutor.isShutdown)
			convolutionExecutor = newConvolutionExecutor()


		val smoothedImg = Lazy.generate(labelMask, cellGrid.cellDimensions, DoubleType(), AccessFlags.setOf()) {}

		paintContext.dataSource.resetMasks()
		setNewSourceMask(paintContext.dataSource, MaskInfo(0, scale0)) { it >= 0 }
		val mask = paintContext.dataSource.currentMask

		val smoothOverInterval: suspend CoroutineScope.(RealInterval) -> Job = { slice ->
			launch {
				val smoothedSlice = smoothedImg.interval(slice)
				runCatching {
					Parallelization.runWithExecutor(convolutionExecutor) {
						convolution.process(labelMask.extendValue(DoubleType(0.0)), smoothedSlice)
					}
				}.exceptionOrNull()?.let { if (isActive) throw it }

				val labels = BundleView(labelMask).interval(slice).cursor()
				val smoothed = smoothedSlice.cursor()
				val maskBundle = BundleView(mask.rai.extendValue(Imglib2Label.INVALID)).interval(slice).cursor()


				ensureActive()
				while (smoothed.hasNext()) {
					//This is the slow one, check cancellation immediately before and after
					val smoothness = runCatching { smoothed.next().get() }.getOrElse { e ->
						val cancellationException = CancellationException("Gaussian Convolution Shutdown", e)
						cancel(cancellationException)
						throw cancellationException
					}

					val labelVal = labels.next().get()
					val maskVal = maskBundle.next().get()
					val replacementLabel = replacementLabelProperty.value
					if (replacementLabel == null) {
						yield()
						continue
					}

					if (smoothness < 0.5 && labelVal.get() == 1.0) {
						maskVal.setInteger(replacementLabel)
					} else if (maskVal.get() == replacementLabel) {
						maskVal.setInteger(Imglib2Label.INVALID)
					}
				}
			}
		}


		/*Start smoothing */
		if (!smoothScope.isActive)
			smoothScope = CoroutineScope(Dispatchers.Default)

		scopeJob = smoothScope.launch {
			intervalsToSmoothOver.forEach { smoothOverInterval(it) }
		}

		/* wait and update progress */
		val progressUpdateJob = CoroutineScope(Dispatchers.Default).launch {
			while (scopeJob?.isActive == true) {
				with(convolutionExecutor) {
					val remaining = 1.0 - currentProgressBar
					val numTasksModifier = 1 / log10(taskCount + 10.0)
					/* Max quarter remaining increments*/
					progressProperty.value = currentProgressBar + (remaining * numTasksModifier).coerceAtMost(.25)
				}
				delay(200)
			}
		}

		while (scopeJob?.isActive == true) {
			delay(50)
			if (scopeJob?.isCancelled == true) {
				progressProperty.value = 0.0
				progressUpdateJob.cancelAndJoin()
				throw CancellationException("Smoothing Cancelled")
			}
		}
		progressUpdateJob.cancelAndJoin()
		progressProperty.value = 1.0
		return intervalsToSmoothOver
	}

	internal fun setNewSourceMask(maskedSource: MaskedSource<*, *>, maskInfo: MaskInfo, acceptMaskValue: (Long) -> Boolean = { it >= 0 }) {
		val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(maskInfo.level)
		val mask = SourceMask(maskInfo, store, volatileStore.rai, store.cache, volatileStore.invalidate) { store.shutdown() }
		maskedSource.setMask(mask, acceptMaskValue)
	}
}
