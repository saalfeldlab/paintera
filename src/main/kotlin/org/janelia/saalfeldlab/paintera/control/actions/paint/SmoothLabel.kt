package org.janelia.saalfeldlab.paintera.control.actions.paint

import bdv.tools.boundingbox.IntervalCorners
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.imglib2.*
import net.imglib2.algorithm.convolution.fast_gauss.FastGauss
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*
import java.util.concurrent.RejectedExecutionException
import net.imglib2.type.label.Label as Imglib2Label


object SmoothLabel : MenuAction("_Smooth...") {

	internal var scopeJob: Job? = null
	private var smoothScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
	internal var smoothJob: Deferred<List<Interval>?>? = null

	internal var finalizeSmoothing = false
	private var resmooth = false

	private lateinit var updateSmoothMask: suspend ((Boolean) -> List<RealInterval>)

	init {
		verifyPermission(PaintActionType.Smooth, PaintActionType.Erase, PaintActionType.Background, PaintActionType.Fill)
		onActionWithState<SmoothLabelState<*, *>> {
			finalizeSmoothing = false
			/* Set lateinit values */
			progress = 0.0
			startSmoothTask()
			getDialog(name?.replace("_", "") ?: "Smooth Label").show()
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

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun SmoothLabelState<*, *>.startSmoothTask() {
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
			labelSelectionProperty.subscribe { selection ->
				smoothing = true
				initializeSmoothLabel()
				smoothing = false
			}
			var intervals: List<Interval>? = emptyList()
			while (coroutineContext.isActive) {
				if (resmooth || finalizeSmoothing) {
					val preview = !finalizeSmoothing
					try {
						smoothing = true
						intervals = updateSmoothMask(preview).map { it.smallestContainingInterval }
						scopeJob?.join()
						if (!preview)
							break
					} catch (_: CancellationException) {
						intervals = null
						dataSource.resetMasks()
						paintera.baseView.orthogonalViews().requestRepaint()
					} finally {
						smoothing = false
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
					dataSource.resetMasks()
					paintera.baseView.orthogonalViews().requestRepaint()
				} ?: task.getCompleted()?.let { intervals ->
					dataSource.apply {
						val applyProgressProperty = SimpleDoubleProperty()
						applyProgressProperty.addListener { _, _, applyProgress -> progress = applyProgress.toDouble() }
						applyMaskOverIntervals(currentMask, intervals, applyProgressProperty) { it >= 0 }
					}
					requestRepaintOverIntervals(intervals)
					refreshMeshes()
				}

				paintera.baseView.disabledPropertyBindings -= task
				smoothTriggerSubscription.unsubscribe()
				paintera.baseView.orthogonalViews().setScreenScales(prevScales)
			}
		}
	}

	private fun SmoothLabelState<*, *>.requestRepaintOverIntervals(intervals: List<Interval>? = null) {
		if (intervals.isNullOrEmpty()) {
			paintera.baseView.orthogonalViews().requestRepaint()
			return
		}
		val smoothedInterval = intervals.reduce(Intervals::union)
		val time = 0
		val globalSmoothedInterval = dataSource.getSourceTransformForMask(MaskInfo(time, scaleLevel)).estimateBounds(smoothedInterval)
		paintera.baseView.orthogonalViews().requestRepaint(globalSmoothedInterval)
	}

	private fun SmoothLabelState<*, *>.cancelActiveSmoothing(reason: String) {
		scopeJob?.cancel(CancellationException(reason))
		progress = 0.0
	}

	private fun SmoothLabelState<*, *>.initializeSmoothLabel() {

		val maskedSource = dataSource as MaskedSource<*, *>
		val labels = labelsToSmooth


		/* Read from the labelBlockLookup (if already persisted) */
		val blocksWithLabel = maskedSource.blocksForLabels(scaleLevel, labels)
		if (blocksWithLabel.isEmpty()) return

		val timePoint = 0
		val sourceImg = maskedSource.getReadOnlyDataBackground(timePoint, scaleLevel)
		val canvasImg = maskedSource.getReadOnlyDataCanvas(timePoint, scaleLevel)

		val cellGrid = maskedSource.getCellGrid(timePoint, scaleLevel)

		val labelMask = DiskCachedCellImgFactory(DoubleType(0.0)).create(sourceImg) { labelMaskChunk ->
			val sourceChunk = sourceImg.interval(labelMaskChunk).cursor()
			val canvasChunk = canvasImg.interval(labelMaskChunk).cursor()
			val maskCursor = labelMaskChunk.cursor()

			while (sourceChunk.hasNext() && canvasChunk.hasNext()) {
				val canvasLabel = canvasChunk.next()
				val sourceLabel = sourceChunk.next()
				val maskVal = maskCursor.next()
				val label = canvasLabel.get().takeIf { it != Imglib2Label.INVALID }
					?: sourceLabel.realDouble.toLong().takeIf { it != Imglib2Label.INVALID }
				label?.takeIf { it in labels }?.let { maskVal.set(1.0) }
			}
		}

		val voronoiBackgroundLabel = IdService.randomTemporaryId()
		val sqWeights = DataSource.getScale(maskedSource, timePoint, scaleLevel).also {
			for ((index, weight) in it.withIndex()) {
				it[index] = weight * weight
			}
		}


		fun nearestLabelImgs(cellDimensions: IntArray): Pair<CachedCellImg<UnsignedLongType, *>, CachedCellImg<DoubleType, *>> {

			val options = DiskCachedCellImgOptions.options()
				.accessFlags(setOf(AccessFlags.VOLATILE))
				.cellDimensions(*cellDimensions)

			val distances = DiskCachedCellImgFactory(DoubleType(), options).create(sourceImg)

			val labelsImg = DiskCachedCellImgFactory(UnsignedLongType(Imglib2Label.INVALID), options).create(sourceImg) { nearestLabelCell ->

				if (!blocksWithLabel.any { (it intersect nearestLabelCell).isNotEmpty() }) {
					return@create
				}

				val canvasCursor = canvasImg.interval(nearestLabelCell).cursor()
				val sourceCursor = sourceImg.interval(nearestLabelCell).cursor()
				val voronoiCursor = nearestLabelCell.cursor()
				val distanceRa = distances.randomAccess(nearestLabelCell)

				while (canvasCursor.hasNext() && sourceCursor.hasNext()) {
					val canvasLabel = canvasCursor.next()
					val sourceLabel = sourceCursor.next()
					val label = canvasLabel.get().takeIf { it != Imglib2Label.INVALID } ?: sourceLabel.realDouble.toLong()

					val finalLabel = if (label in labels) {
						distanceRa.setPositionAndGet(canvasCursor).set(Double.MAX_VALUE)
						voronoiBackgroundLabel
					} else label
					voronoiCursor.next().set(finalLabel)
				}

				DistanceTransform.voronoiDistanceTransform(nearestLabelCell, distances.interval(nearestLabelCell), *sqWeights)
			}
			return labelsImg to distances
		}

		val smallGrid = cellGrid.cellDimensions.also { it.forEachIndexed { idx, value -> it[idx] = (value * .75).toInt() } }
		val (nearestLabelsSmallGrid, distancesSmallGrid) = nearestLabelImgs(smallGrid)
		val (nearestLabelsFullGrid, distancesFullGrid) = nearestLabelImgs(cellGrid.cellDimensions)


		val nearestLabels = DiskCachedCellImgFactory(
			UnsignedLongType(Imglib2Label.INVALID),
			DiskCachedCellImgOptions.options().cellDimensions(*cellGrid.cellDimensions)
		).create(sourceImg) { nearestLabelCell ->

			val nearestLabelCursor = nearestLabelCell.cursor()
			val labelSmallGridRa = nearestLabelsSmallGrid.randomAccess()
			val labelFullGridRa = nearestLabelsFullGrid.randomAccess()
			/* ensure the necessary cells have been processed before we use the distances */

			labelFullGridRa.setPositionAndGet(*nearestLabelCell.minAsLongArray())
			IntervalCorners.corners(nearestLabelCell).forEach { corner -> labelSmallGridRa.setPositionAndGet(*corner.map { it.toLong() }.toLongArray()) }

			for (nearestLabel in nearestLabelCursor) {
				val distFullGrid = distancesFullGrid.getAt(nearestLabelCursor).realDouble
				if (distFullGrid == 0.0) //If either is 0.0, they should both be 0.0, so only need to check one
					continue

				val distSmallGrid = distancesSmallGrid.getAt(nearestLabelCursor).realDouble
				val labelRa = if (distFullGrid < distSmallGrid) labelFullGridRa else labelSmallGridRa
				val label = labelRa.setPositionAndGet(nearestLabelCursor)
				nearestLabel.set(label)
			}
		}
		updateSmoothMask = { preview -> smoothMask(labelMask, nearestLabels, blocksWithLabel, preview) }
		resmooth = true
	}


	@JvmStatic
	fun MaskedSource<*, *>.blocksForLabels(scaleLevel: Int, labels: LongArray): List<Interval> {
		val sourceState = paintera.baseView.sourceInfo().getState(this) as ConnectomicsLabelState
		val blocksFromSource = labels.flatMap { sourceState.labelBlockLookup.read(LabelBlockLookupKey(scaleLevel, it)).toList() }

		/* Read from canvas access (if in canvas) */
		val timePoint = 0
		val cellGrid = getCellGrid(timePoint, scaleLevel)
		val cellIntervals = cellGrid.cellIntervals().randomAccess()
		val cellPos = LongArray(cellGrid.numDimensions())
		val blocksFromCanvas = labels.flatMap {
			getModifiedBlocks(scaleLevel, it).toArray().map { block ->
				cellGrid.getCellGridPositionFlat(block, cellPos)
				FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
			}
		}

		return blocksFromSource + blocksFromCanvas
	}

	private fun SmoothLabelState<*, *>.pruneBlock(blocksWithLabel: List<Interval>): List<RealInterval> {
		val viewsInSourceSpace = viewerIntervalsInSourceSpace()

		/* remove any blocks that don't intersect with them*/
		return blocksWithLabel
			.mapNotNull { it.takeIf { viewsInSourceSpace.any { viewer -> !Intervals.isEmpty(viewer.intersect(it)) } } }
			.toList()

	}

	private fun SmoothLabelState<*, *>.viewerIntervalsInSourceSpace(): Array<FinalRealInterval> {
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
				val time = 0
				sourceState.getDataSource().getSourceTransform(time, scaleLevel, sourceToGlobal)
				val viewerToSource = sourceToGlobal.inverse().copy().concatenate(globalToViewerTransform.inverse())
				viewerToSource.estimateBounds(screenInterval)
			}.toTypedArray()
		return viewsInSourceSpace
	}

	private fun SmoothLabelState<*, *>.smoothMask(labelMask: CachedCellImg<DoubleType, *>, nearestLabels: RandomAccessibleInterval<UnsignedLongType>, blocksWithLabel: List<Interval>, preview: Boolean = false): List<RealInterval> {

		/* Just to show that smoothing has started */
		progress = 0.0 // The listener only resets to zero if going backward, so do this first
		progress = .05

		val intervalsToSmoothOver = if (preview) pruneBlock(blocksWithLabel) else blocksWithLabel

		val levelResolution = getLevelResolution(scaleLevel)
		val kernelSize = kernelSizeProperty.get()
		val sigma = DoubleArray(3) { kernelSize / levelResolution[it] }

		val convolution = FastGauss.convolution(sigma)
		val smoothedImg = DiskCachedCellImgFactory(DoubleType(0.0)).create(labelMask)

		dataSource.resetMasks()
		val time = 0
		setNewSourceMask(dataSource, MaskInfo(time, scaleLevel)) { it >= 0 }
		val mask = dataSource.currentMask

		val smoothOverInterval: suspend CoroutineScope.(RealInterval) -> Flow<Int> = { slice ->
			flow {
				val smoothedSlice = smoothedImg.interval(slice)
				runCatching {
					convolution.process(labelMask.extendValue(DoubleType(0.0)), smoothedSlice)
				}.exceptionOrNull()
					?.takeIf { it is RejectedExecutionException && isActive }
					?.let { throw it }
				emit(0)

				val labels = BundleView(labelMask).interval(slice).cursor()
				val smoothed = smoothedSlice.cursor()
				val maskBundle = BundleView(mask.rai.extendValue(Imglib2Label.INVALID)).interval(slice).cursor()
				val nearestLabelsSlice = nearestLabels.extendValue(Imglib2Label.INVALID).interval(slice).cursor()

				ensureActive()
				val infillStrategy = infillStrategyProperty.get()
				val replaceLabel = replacementLabelProperty.get()
				val emitUpdateAfter = slice.smallestContainingInterval.numElements() / 5
				var count = 0L
				while (smoothed.hasNext()) {
					(++count).takeIf { it % emitUpdateAfter == 0L }?.let { emit(0) }

					//This is the slow one, check cancellation immediately before and after
					val smoothness: Double
					try {
						smoothness = smoothed.next().get()
					} catch (e: Exception) {
						val cancellation = CancellationException("Gaussian Convolution Shutdown", e)
						cancel(cancellation)
						throw cancellation
					}

					val labelVal = labels.next().get()
					val nearest = nearestLabelsSlice.next()
					val maskPos = maskBundle.next()
					val maskVal = maskPos.get()
					if (smoothness < 0.5 && labelVal.get() == 1.0) {
						when (infillStrategy) {
							InfillStrategy.Replace -> replaceLabel
							InfillStrategy.Background -> Imglib2Label.BACKGROUND
							InfillStrategy.NearestLabel -> nearest.get()
						}
					} else if (maskVal.get() == replaceLabel) {
						maskVal.setInteger(Imglib2Label.INVALID)
					}
				}
			}
		}


		/*Start smoothing */
		if (!smoothScope.isActive)
			smoothScope = CoroutineScope(Dispatchers.Default)

		scopeJob = smoothScope.launch {
			progress = .1
			val increment = (.99 - .1) / intervalsToSmoothOver.size
			intervalsToSmoothOver.forEach {
				launch {
					var approachTotal = 0.0
					smoothOverInterval(it).apply {
						collect { it ->
							val addProgress = (increment - approachTotal) * .25
							approachTotal += addProgress
							InvokeOnJavaFXApplicationThread {
								progress = (progress + addProgress).coerceAtMost(1.0)
							}
						}
					}
				}
			}
		}.apply {
			invokeOnCompletion { cause ->
				requestRepaintOverIntervals(intervalsToSmoothOver.map { it.smallestContainingInterval })
				progress = when (cause) {
					null -> 1.0
					is CancellationException -> 0.0
					else -> throw cause
				}
			}
		}

		return intervalsToSmoothOver
	}

	internal fun setNewSourceMask(maskedSource: MaskedSource<*, *>, maskInfo: MaskInfo, acceptMaskValue: (Long) -> Boolean = { it >= 0 }) {

		val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(maskInfo.level)
		val mask = SourceMask(maskInfo, store, volatileStore.rai, store.cache, volatileStore.invalidate) { store.shutdown() }
		maskedSource.setMask(mask, acceptMaskValue)
	}
}
