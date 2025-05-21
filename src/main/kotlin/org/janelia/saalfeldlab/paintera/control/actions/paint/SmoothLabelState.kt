package org.janelia.saalfeldlab.paintera.control.actions.paint

import bdv.tools.boundingbox.IntervalCorners
import com.google.common.util.concurrent.AtomicDouble
import gnu.trove.set.hash.TLongHashSet
import javafx.event.Event
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.imglib2.*
import net.imglib2.algorithm.convolution.Convolution
import net.imglib2.algorithm.convolution.fast_gauss.FastGauss
import net.imglib2.algorithm.convolution.kernel.Kernel1D
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution
import net.imglib2.algorithm.gauss3.Gauss3
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.img.DiskCachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.ConstantUtils
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothDirection.*
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.SmoothScope
import org.janelia.saalfeldlab.paintera.control.actions.state.ViewerAndPaintableSourceActionState
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.RandomAccessibleIntervalBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*
import java.util.concurrent.RejectedExecutionException
import kotlin.math.roundToInt

internal enum class LabelSelection() {
	ActiveFragments,
	ActiveSegments
}

internal enum class InfillStrategy() {
	Replace,
	Background,
	NearestLabel
}

internal enum class SmoothDirection {
	Shrink,
	Expand,
	Both;

	fun defaultKernelSize(resolution : DoubleArray): Int {
		val min = resolution.min()
		val max = resolution.max()
		return when (this) {
			Expand -> (min + (max - min) / 4.0).roundToInt() // quarter
			else -> (min + (max - min) / 2.0).roundToInt() // half
		}
	}
}

internal enum class SmoothStatus(val text: String) {
	Smoothing("Smoothing..."),
	Done("Done"),
	Applying("Applying..."),
	Empty("")
}

internal enum class Resmooth {
	Cancel,
	Partial,
	Full,
	Finish;
}

internal class SmoothLabelState<D, T> :
	ViewerAndPaintableSourceActionState<ConnectomicsLabelState<D, T>, D, T>(),
	SmoothLabelUI.Model by SmoothLabelUI.Default()
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	var progress by progressProperty.nonnull()

	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)
		action.verify("Mask is in Use") { !this@SmoothLabelState.maskedSource.isMaskInUseBinding().get() }
	}

	private val activeFragments by lazy {
		selectedIds.activeIds.toArray()
	}

	private val activeSegments by lazy {
		val fragments = TLongHashSet()
		assignment.run {
			selectedIds.activeIds.toArray().map { getSegment(it) }.toSet().forEach { getFragments(it).forEach { fragments.add(it) } }
		}
		/* If active segments are the same as fragments, return active fragments.
		* This is an optimization to avoid triggering invalidation for labelsToSmooth */
		if (fragments == TLongHashSet(activeFragments))
			activeFragments
		else
			fragments.toArray()
	}

	val selectedLabels: LongArray
		get() = when (labelSelectionProperty.get()) {
			LabelSelection.ActiveFragments -> activeFragments
			LabelSelection.ActiveSegments -> activeSegments
		}

	override fun newId() = sourceState.idService.next()

	fun progressStatusSubscription(): Subscription = progressProperty.subscribe { progress ->
		val progress = progress.toDouble()
		val isApplyMask = maskedSource.isApplyingMaskProperty()
		statusProperty.value = when {
			progress == 0.0 -> SmoothStatus.Empty
			progress == 1.0 -> SmoothStatus.Done
			isApplyMask.get() -> SmoothStatus.Applying
			progress > 0.0 && progress < 1.0 -> SmoothStatus.Smoothing
			else -> SmoothStatus.Empty
		}
	}


	override fun getLevelResolution(level: Int): DoubleArray {

		if (level == 0)
			return sourceState.resolution

		val n5Backend = sourceState.backend as? SourceStateBackendN5<*, *>
		val metadataState = n5Backend?.metadataState as? MultiScaleMetadataState
		val metadataScales = metadataState?.scaleTransforms?.get(level)
		if (metadataScales != null)
			return metadataScales.resolution

		(sourceState.backend as? RandomAccessibleIntervalBackend<*, *>)?.resolutions?.get(level)?.let { resolution ->
			return resolution
		}

		return doubleArrayOf(1.0, 1.0, 1.0)
	}

	fun blocksForLabels(scaleLevel: Int, labels: LongArray): List<Interval> {
		val blocksFromSource = labels.flatMap { sourceState.labelBlockLookup.read(LabelBlockLookupKey(scaleLevel, it)).toList() }

		/* Read from canvas access (if in canvas) */
		val cellGrid = maskedSource.getCellGrid(timepoint, scaleLevel)
		val cellIntervals = cellGrid.cellIntervals().randomAccess()
		val cellPos = LongArray(cellGrid.numDimensions())
		val blocksFromCanvas = labels.flatMap {
			maskedSource.getModifiedBlocks(scaleLevel, it).toArray().map { block ->
				cellGrid.getCellGridPositionFlat(block, cellPos)
				FinalInterval(cellIntervals.setPositionAndGet(*cellPos))
			}
		}

		return blocksFromSource + blocksFromCanvas
	}

	fun viewerIntervalsInSourceSpace(intersectFilter: List<Interval> = emptyList()): List<Interval> {
		/* get viewer screen intervals for each orthogonal view in source space*/
		val viewerAndTransforms = paintera.baseView.orthogonalViews().viewerAndTransforms()
		return viewerAndTransforms.mapNotNull {
			val globalToViewerTransform = AffineTransform3D()
			it.viewer().state.getViewerTransform(globalToViewerTransform)
			val width = it.viewer().width
			val height = it.viewer().height
			val screenInterval = FinalInterval(width.toLong(), height.toLong(), 1L)
			val sourceToGlobal = AffineTransform3D()
			sourceState.getDataSource().getSourceTransform(timepoint, scaleLevel, sourceToGlobal)
			val viewerToSource = sourceToGlobal.inverse().copy().concatenate(globalToViewerTransform.inverse())
			viewerToSource
				.estimateBounds(screenInterval)
				.takeIf { intersectFilter.isEmpty() || intersectFilter.any { filter -> filter.intersect(it).isNotEmpty() } }
				?.smallestContainingInterval
		}
	}

	fun requestRepaintOverIntervals(intervals: List<Interval>? = null) {
		if (intervals.isNullOrEmpty()) {
			paintera.baseView.orthogonalViews().requestRepaint()
			return
		}
		val smoothedInterval = intervals.reduce(Intervals::union)
		val globalSmoothedInterval = maskedSource.getSourceTransformForMask(MaskInfo(timepoint, scaleLevel)).estimateBounds(smoothedInterval)
		paintera.baseView.orthogonalViews().requestRepaint(globalSmoothedInterval)
	}

	private val labelMask: DiskCachedCellImg<DoubleType, *> by LazyForeignValue(::selectedLabels) { labels ->

		val sourceImg = maskedSource.getReadOnlyDataBackground(timepoint, scaleLevel)
		val canvasImg = maskedSource.getReadOnlyDataCanvas(timepoint, scaleLevel)

		DiskCachedCellImgFactory(DoubleType(0.0)).create(sourceImg) { labelMaskChunk ->
			val sourceChunk = sourceImg.interval(labelMaskChunk).cursor()
			val canvasChunk = canvasImg.interval(labelMaskChunk).cursor()
			val maskCursor = labelMaskChunk.cursor()

			while (sourceChunk.hasNext() && canvasChunk.hasNext()) {
				val canvasLabel = canvasChunk.next()
				val sourceLabel = sourceChunk.next()
				val maskVal = maskCursor.next()
				val label = canvasLabel.get().takeIf { it != Label.INVALID }
					?: sourceLabel.realDouble.toLong().takeIf { it != Label.INVALID }
				label?.takeIf { it in labels }?.let { maskVal.set(1.0) }
			}
		}
	}

	private val labelsImg: DiskCachedCellImg<UnsignedLongType, *> by lazy {
		val sourceImg = maskedSource.getReadOnlyDataBackground(timepoint, scaleLevel)
		val canvasImg = maskedSource.getReadOnlyDataCanvas(timepoint, scaleLevel)
		sourceAndCanvasImg(sourceImg, canvasImg)
	}

	private val voronoiBackgroundLabel = IdService.randomTemporaryId()

	fun mergeNearestLabelUnalignedGrids(labels: LongArray, sqWeights: DoubleArray, blocksWithLabel: List<Interval>, invert: Boolean = false): RandomAccessibleInterval<UnsignedLongType> {

		val cellGrid = maskedSource.getCellGrid(timepoint, scaleLevel)
		val smallGrid = cellGrid.cellDimensions.also { it.forEachIndexed { idx, value -> it[idx] = (value * .75).toInt() } }

		val (nearestLabelsSmallGrid, distancesSmallGrid) = nearestLabelImgs(labelsImg, smallGrid, voronoiBackgroundLabel, blocksWithLabel, labels, sqWeights, invert)
		val (nearestLabelsFullGrid, distancesFullGrid) = nearestLabelImgs(labelsImg, cellGrid.cellDimensions, voronoiBackgroundLabel, blocksWithLabel, labels, sqWeights, invert)

		return DiskCachedCellImgFactory(
			UnsignedLongType(Label.INVALID),
			DiskCachedCellImgOptions.options().cellDimensions(*cellGrid.cellDimensions)
		).create(labelsImg) { nearestLabelCell ->

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
	}

	fun updateSmoothMaskFunction(): suspend (Boolean, Resmooth) -> List<RealInterval> {

		/* Read from the labelBlockLookup (if already persisted) */
		val blocksWithLabel = blocksForLabels(scaleLevel, selectedLabels)
		if (blocksWithLabel.isEmpty()) return { _, _ -> emptyList() }

		val sqWeights = DataSource.getScale(maskedSource, timepoint, scaleLevel).also {
			for ((index, weight) in it.withIndex()) {
				it[index] = weight * weight
			}
		}

		val nearestLabels = mergeNearestLabelUnalignedGrids(selectedLabels, sqWeights, blocksWithLabel)
		val nearestBackgroundLabels = if (selectedLabels.size == 1) {
			ConstantUtils.constantRandomAccessible(UnsignedLongType(selectedLabels.first()), nearestLabels.numDimensions())
		} else {
			smoothDirectionProperty.get().takeIf { it == Expand || it == Both }?.let {
				mergeNearestLabelUnalignedGrids(selectedLabels, sqWeights, blocksWithLabel, invert = true)
			}
		}
		return { preview, resmooth -> smoothMask(labelMask, nearestLabels, nearestBackgroundLabels, blocksWithLabel, preview, resmooth) }
	}

	private var smoothImg: RandomAccessibleInterval<DoubleType>? = null

	private suspend fun smoothMask(
		labelMask: CachedCellImg<DoubleType, *>,
		nearestLabels: RandomAccessibleInterval<UnsignedLongType>,
		nearestBackgroundLabels: RandomAccessible<UnsignedLongType>?,
		blocksWithLabel: List<Interval>,
		preview: Boolean = false,
		resmooth: Resmooth = Resmooth.Full,
	): List<RealInterval> {

		if (resmooth >= Resmooth.Full) InvokeOnJavaFXApplicationThread {
			/* Just to show that smoothing has started */
			progress = 0.0 // The listener only resets to zero if going backward, so do this first
			progress = .05
		}

		val intervalsToSmoothOver = if (preview) viewerIntervalsInSourceSpace(intersectFilter = blocksWithLabel) else blocksWithLabel

		val levelResolution = getLevelResolution(scaleLevel)
		val kernelSize = kernelSizeProperty.get()
		val sigma = DoubleArray(3) { kernelSize / levelResolution[it] }


		val smoothedImg = smoothImg?.takeUnless { resmooth >= Resmooth.Full } ?: DiskCachedCellImgFactory(DoubleType(0.0)).create(labelMask)
		smoothImg = smoothedImg
		val mask = getNewSourceMask(maskedSource, MaskInfo(timepoint, scaleLevel))
		val smoothDirection = smoothDirectionProperty.get()
		val threshold = 0.5

		val smoothShrink = smoothDirection == Shrink || smoothDirection == Both
		val smoothExpand = smoothDirection == Expand || smoothDirection == Both


		val halfKernels: Array<DoubleArray> = sigma.map { Gauss3.halfkernel(it, Gauss3.halfkernelsize(it), false) }.toTypedArray()
		val unnormalizedGauss3 = SeparableKernelConvolution.convolution(*Kernel1D.symmetric(halfKernels))

		val normalizeFastGauss: Convolution<RealType<*>> = FastGauss.convolution(sigma)

		val smoothOverInterval: suspend CoroutineScope.(RealInterval) -> Flow<Int> = { slice ->
			val nearestBackgroundLabelsAccess = nearestBackgroundLabels?.randomAccess()
			flow {
				val smoothedSlice = smoothedImg.interval(slice)
				if (resmooth == Resmooth.Full) {
					runCatching {
						if (!smoothShrink && smoothExpand)
							unnormalizedGauss3.process(labelMask.extendValue(DoubleType(0.0)), smoothedSlice)
						else
							normalizeFastGauss.process(labelMask.extendValue(DoubleType(0.0)), smoothedSlice)
					}.exceptionOrNull()
						?.takeIf { it is RejectedExecutionException && isActive }
						?.let { throw it }
				}
				emit(0)

				val labels = BundleView(labelMask).interval(slice).cursor()
				val smoothed = smoothedSlice.cursor()
				val maskBundleCursor = BundleView(mask.rai.extendValue(Label.INVALID)).interval(slice).cursor()
				val nearestLabelsCursor = nearestLabels.extendValue(Label.INVALID).interval(slice).cursor()

				val infillStrategy = infillStrategyProperty.get()
				ensureActive()
				val replaceLabel = replacementLabelProperty.get()
				val emitUpdateAfter = slice.smallestContainingInterval.numElements() / 5
				var count = 0L
				while (smoothed.hasNext()) {
					(++count).takeIf { it % emitUpdateAfter == 0L }?.let { emit(0) }

					val gaussian = runCatching { smoothed.next().get() }.getOrElse { cause ->
						val cancellation = CancellationException("Gaussian Convolution Shutdown", cause)
						cancel(cancellation)
						throw cancellation
					}

					val labelMaskAccess = labels.next()
					val wasLabel = labelMaskAccess.get().get() == 1.0
					val nearest = nearestLabelsCursor.next()
					val maskPos = maskBundleCursor.next()
					val maskVal = maskPos.get()
					when {
						gaussian < threshold && smoothShrink && wasLabel ->
							when (infillStrategy) {
								InfillStrategy.Replace -> replaceLabel
								InfillStrategy.Background -> Label.BACKGROUND
								InfillStrategy.NearestLabel -> nearest.get()
							}

						gaussian > threshold && smoothExpand && !wasLabel ->
							nearestBackgroundLabelsAccess!!.setPositionAndGet(nearestLabelsCursor).get()

						maskVal.get() == replaceLabel -> Label.INVALID
						else -> null
					}?.let { newLabel -> maskVal.set(newLabel) }
				}
			}
		}

		var localProgress = AtomicDouble(.1)



		coroutineScope {
			launch {
				val increment = (.99 - .1) / intervalsToSmoothOver.size
				for (interval in intervalsToSmoothOver) {
					launch {
						var approachTotal = 0.0
						smoothOverInterval(interval).collect { _ ->
							if (resmooth >= Resmooth.Full) {
								val addProgress = (increment - approachTotal) * .25
								approachTotal += addProgress
								localProgress.updateAndGet { (it + addProgress).coerceAtMost(1.0) }
								SmoothScope.submitUI { progress = localProgress.get() }
							}
						}
					}
				}
			}.invokeOnCompletion { cause ->
				if (cause != null || resmooth >= Resmooth.Full)
					requestRepaintOverIntervals()
				if (cause == null) {
					maskedSource.resetMasks()
					maskedSource.setMask(mask) { it >= 0 }
					requestRepaintOverIntervals(intervalsToSmoothOver.map { it.smallestContainingInterval })
				}
				val finalProgress = when (cause) {
					null -> 1.0
					is CancellationException -> 0.0
					else -> throw cause
				}
				SmoothScope.submitUI { progress = finalProgress }
			}
		}

		return intervalsToSmoothOver
	}

	internal fun getNewSourceMask(maskedSource: MaskedSource<*, *>, maskInfo: MaskInfo): SourceMask {

		val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(maskInfo.level)
		return SourceMask(maskInfo, store, volatileStore.rai, store.cache, volatileStore.invalidate) { store.shutdown() }
	}


	companion object {
		private val AffineTransform3D.resolution
			get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])

		fun sourceAndCanvasImg(
			sourceLabels: RandomAccessibleInterval<out RealType<*>>,
			canvasLabels: RandomAccessibleInterval<UnsignedLongType>,
		): DiskCachedCellImg<UnsignedLongType, *> {
			return DiskCachedCellImgFactory(UnsignedLongType()).create(sourceLabels) { cell ->

				val canvasCursor = canvasLabels.interval(cell).cursor()
				val sourceCursor = sourceLabels.interval(cell).cursor()
				val cursor = cell.cursor()

				while (canvasCursor.hasNext() && sourceCursor.hasNext() && cursor.hasNext()) {
					val canvasLabel = canvasCursor.next()
					val sourceLabel = sourceCursor.next()
					val label = cursor.next()
					label.set(canvasLabel.get().takeIf { it != Label.INVALID } ?: sourceLabel.realDouble.toLong())
				}
			}
		}

		fun nearestLabelImgs(
			labelsImg: RandomAccessibleInterval<UnsignedLongType>,
			cellDimensions: IntArray,
			voronoiBackgroundLabel: Long,
			blocksWithLabels: List<Interval>,
			labelsToSmooth: LongArray,
			sqWeights: DoubleArray,
			invert: Boolean = false,
		): Pair<CachedCellImg<UnsignedLongType, *>, CachedCellImg<DoubleType, *>> {

			val options = DiskCachedCellImgOptions.options()
				.accessFlags(setOf(AccessFlags.VOLATILE))
				.cellDimensions(*cellDimensions)

			val distancesImg = DiskCachedCellImgFactory(DoubleType(), options).create(labelsImg)

			val voronoiLabelsImg = DiskCachedCellImgFactory(UnsignedLongType(Label.INVALID), options).create(labelsImg) { nearestLabelCell ->

				if (!blocksWithLabels.any { (it intersect nearestLabelCell).isNotEmpty() })
					return@create

				val labelsCursor = labelsImg.interval(nearestLabelCell).cursor()
				val voronoiCursor = nearestLabelCell.cursor()
				val distanceRa = distancesImg.randomAccess(nearestLabelCell)

				while (labelsCursor.hasNext()) {
					val label = labelsCursor.next().get()

					val labelInLabels = label in labelsToSmooth
					val acceptLabel = if (invert) !labelInLabels else labelInLabels

					val finalLabel = if (acceptLabel) {
						distanceRa.setPositionAndGet(labelsCursor).set(Double.MAX_VALUE)
						voronoiBackgroundLabel
					} else label
					voronoiCursor.next().set(finalLabel)
				}

				DistanceTransform.voronoiDistanceTransform(nearestLabelCell, distancesImg.interval(nearestLabelCell), *sqWeights)
			}
			return voronoiLabelsImg to distancesImg
		}
	}
}