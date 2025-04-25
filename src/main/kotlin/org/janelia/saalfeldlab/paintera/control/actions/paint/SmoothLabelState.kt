package org.janelia.saalfeldlab.paintera.control.actions.paint

import bdv.tools.boundingbox.IntervalCorners
import com.google.common.util.concurrent.AtomicDouble
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.event.Event
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
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.component1
import org.janelia.saalfeldlab.fx.extensions.component2
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.net.imglib2.view.BundleView
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.SmoothScope
import org.janelia.saalfeldlab.paintera.control.actions.paint.SmoothLabel.smoothJob
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
import kotlin.math.floor
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
	In,
	Out,
	Both;
}

internal enum class SmoothStatus(val text: String) {
	Smoothing("Smoothing... "),
	Done("        Done "),
	Applying(" Applying... "),
	Empty("             ")
}

internal class SmoothLabelState<D, T> :
	ViewerAndPaintableSourceActionState<ConnectomicsLabelState<D, T>, D, T>(),
	SmoothLabelUI.Model by SmoothLabelUI.Default()
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	val resolution by LazyForeignValue(::scaleLevel) { getLevelResolution(it) }
	private val defaultKernelSize: Int
		get() {
			val min = resolution.min()
			val max = resolution.max()
			return (min + (max - min) / 2.0).roundToInt()
		}

	override val minKernelSize
		get() = floor(resolution.min() / 2).toInt()
	override val maxKernelSize
		get() = (resolution.max() * 10).toInt()
	override val kernelSizeProperty: IntegerProperty by lazy { SimpleIntegerProperty(defaultKernelSize) }

	var progress by progressProperty.nonnull()

	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)
		action.verify("Mask is in Use") { !this@SmoothLabelState.dataSource.isMaskInUseBinding().get() }
	}

	val dataSource by lazy { paintContext.dataSource }
	private val selectedIds by lazy { paintContext.selectedIds }
	fun refreshMeshes() = paintContext.refreshMeshes()

	//TODO Caleb: Currently, this is always 0
	//  At higher scale levels, currently it can leave small artifacts at the previous boundary when
	//  going back to higher resolution
	internal val scaleLevel = 0

	val labelsToSmooth: LongArray
		get() = when (labelSelectionProperty.get()) {
			LabelSelection.ActiveFragments -> selectedIds.activeIds.toArray()
			LabelSelection.ActiveSegments -> let {
				val fragments = mutableSetOf<Long>()
				with(paintContext.assignment) {
					selectedIds.activeIds.toArray().map { getSegment(it) }.toSet().forEach { getFragments(it).forEach { fragments.add(it) } }
				}
				fragments.toLongArray()
			}
		}

	override fun newId() = sourceState.idService.next()

	fun progressStatusSubscription(): Subscription = progressProperty.subscribe { progress ->
		val progress = progress.toDouble()
		val isApplyMask = dataSource.isApplyingMaskProperty()
		statusProperty.value = when {
			progress == 0.0 -> SmoothStatus.Empty
			progress == 1.0 -> SmoothStatus.Done
			isApplyMask.get() -> SmoothStatus.Applying
			smoothJob?.isActive == true -> SmoothStatus.Smoothing
			else -> SmoothStatus.Empty
		}
	}

	fun getLevelResolution(level: Int): DoubleArray {

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
		val timePoint = 0
		val cellGrid = maskedSource.getCellGrid(timePoint, scaleLevel)
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

	fun viewerIntervalsInSourceSpace(): Array<FinalRealInterval> {
		/* get viewer screen intervals for each orthogonal view in source space*/
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

	fun requestRepaintOverIntervals(intervals: List<Interval>? = null) {
		if (intervals.isNullOrEmpty()) {
			paintera.baseView.orthogonalViews().requestRepaint()
			return
		}
		val smoothedInterval = intervals.reduce(Intervals::union)
		val time = 0
		val globalSmoothedInterval = dataSource.getSourceTransformForMask(MaskInfo(time, scaleLevel)).estimateBounds(smoothedInterval)
		paintera.baseView.orthogonalViews().requestRepaint(globalSmoothedInterval)
	}

	fun updateSmoothMaskFunction(): suspend (Boolean) -> List<RealInterval> {

		val maskedSource = dataSource as MaskedSource<*, *>
		val labels = labelsToSmooth


		/* Read from the labelBlockLookup (if already persisted) */
		val blocksWithLabel = blocksForLabels(scaleLevel, labels)
		if (blocksWithLabel.isEmpty()) return { emptyList() }

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
				val label = canvasLabel.get().takeIf { it != Label.INVALID }
					?: sourceLabel.realDouble.toLong().takeIf { it != Label.INVALID }
				label?.takeIf { it in labels }?.let { maskVal.set(1.0) }
			}
		}

		val voronoiBackgroundLabel = IdService.randomTemporaryId()
		val sqWeights = DataSource.getScale(maskedSource, timePoint, scaleLevel).also {
			for ((index, weight) in it.withIndex()) {
				it[index] = weight * weight
			}
		}

		val smoothDirection = smoothDirectionProperty.get()
		val smallGrid = cellGrid.cellDimensions.also { it.forEachIndexed { idx, value -> it[idx] = (value * .75).toInt() } }
		val mergeNearestLabelUnalignedGrids: (Boolean) -> RandomAccessibleInterval<UnsignedLongType> = { invert ->
			val (nearestLabelsSmallGrid, distancesSmallGrid) = nearestLabelImgs(sourceImg, canvasImg, smallGrid, voronoiBackgroundLabel, blocksWithLabel, labels, sqWeights, invert)
			val (nearestLabelsFullGrid, distancesFullGrid) = nearestLabelImgs(sourceImg, canvasImg, cellGrid.cellDimensions, voronoiBackgroundLabel, blocksWithLabel, labels, sqWeights, invert)

			DiskCachedCellImgFactory(
				UnsignedLongType(Label.INVALID),
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
		}

		val invert = true
		val nearestLabels = mergeNearestLabelUnalignedGrids(!invert)
		val nearestBackgroundLabels = smoothDirection.takeIf { it == SmoothDirection.Out || it == SmoothDirection.Both }?.let { mergeNearestLabelUnalignedGrids(invert) }
		return { preview -> smoothMask(labelMask, nearestLabels, nearestBackgroundLabels, blocksWithLabel, preview) }
	}

	private fun pruneBlock(blocksWithLabel: List<Interval>): List<RealInterval> {
		val viewsInSourceSpace = viewerIntervalsInSourceSpace()

		/* remove any blocks that don't intersect with them*/
		return blocksWithLabel
			.mapNotNull { it.takeIf { viewsInSourceSpace.any { viewer -> !Intervals.isEmpty(viewer.intersect(it)) } } }
			.toList()

	}

	private suspend fun smoothMask(
		labelMask: CachedCellImg<DoubleType, *>,
		nearestLabels: RandomAccessibleInterval<UnsignedLongType>,
		nearestBackgroundLabels: RandomAccessibleInterval<UnsignedLongType>?,
		blocksWithLabel: List<Interval>,
		preview: Boolean = false,
	): List<RealInterval> {

		/* Just to show that smoothing has started */
		progress = 0.0 // The listener only resets to zero if going backward, so do this first
		progress = .05

		val intervalsToSmoothOver = if (preview) pruneBlock(blocksWithLabel) else blocksWithLabel

		val levelResolution = getLevelResolution(scaleLevel)
		val kernelSize = kernelSizeProperty.get()
		val sigma = DoubleArray(3) { kernelSize / levelResolution[it] }

		val convolution = FastGauss.convolution(sigma)
		val smoothedImg = DiskCachedCellImgFactory(DoubleType(0.0)).create(labelMask)

		val time = 0
		dataSource.resetMasks()
		setNewSourceMask(dataSource, MaskInfo(time, scaleLevel)) { it >= 0 }
		val mask = dataSource.currentMask
		val smoothDirection = smoothDirectionProperty.get()
		val smoothIn = smoothDirection == SmoothDirection.In || smoothDirection == SmoothDirection.Both
		val smoothOut = smoothDirection == SmoothDirection.Out || smoothDirection == SmoothDirection.Both
		val smoothOverInterval: suspend CoroutineScope.(RealInterval) -> Flow<Int> = { slice ->
			val nearestBackgroundLabelsAccess = nearestBackgroundLabels?.randomAccess()
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
				val maskBundleCursor = BundleView(mask.rai.extendValue(Label.INVALID)).interval(slice).cursor()
				val nearestLabelsCursor = nearestLabels.extendValue(Label.INVALID).interval(slice).cursor()

				val infillStrategy = infillStrategyProperty.get()
				ensureActive()
				val replaceLabel = replacementLabelProperty.get()
				val emitUpdateAfter = slice.smallestContainingInterval.numElements() / 5
				var count = 0L
				while (smoothed.hasNext()) {
					(++count).takeIf { it % emitUpdateAfter == 0L }?.let { emit(0) }

					val smoothness = runCatching { smoothed.next().get() }.getOrElse { cause ->
						val cancellation = CancellationException("Gaussian Convolution Shutdown", cause)
						cancel(cancellation)
						throw cancellation
					}

					val labelMaskAccess = labels.next()
					val wasLabel = labelMaskAccess.get().get() == 1.0
					val nearest = nearestLabelsCursor.next()
					val maskPos = maskBundleCursor.next()
					val maskVal = maskPos.get()
					if (smoothness < 0.5 && smoothIn && wasLabel) {
						val newLabel = when (infillStrategy) {
							InfillStrategy.Replace -> replaceLabel
							InfillStrategy.Background -> Label.BACKGROUND
							InfillStrategy.NearestLabel -> nearest.get()
						}
						maskVal.set(newLabel)
						continue
					}
					if (smoothness > 0.5 && smoothOut && !wasLabel) {
						val newLabel = nearestBackgroundLabelsAccess!!.setPositionAndGet(nearestLabelsCursor)
						maskVal.set(newLabel)
						continue
					}
					if (maskVal.get() == replaceLabel)
						maskVal.set(Label.INVALID)
				}
			}
		}

		var localProgress = AtomicDouble(.1)

		/*Start smoothing */
		val job = SmoothScope.submit {
			val increment = (.99 - .1) / intervalsToSmoothOver.size
			for (interval in intervalsToSmoothOver) {
				launch {
					var approachTotal = 0.0
					smoothOverInterval(interval).collect { _ ->
						val addProgress = (increment - approachTotal) * .25
						approachTotal += addProgress
						localProgress.updateAndGet { (it + addProgress).coerceAtMost(1.0) }
						SmoothScope.submitUI { progress = localProgress.get() }
					}
				}
			}
		}.apply {
			invokeOnCompletion { cause ->
				requestRepaintOverIntervals(intervalsToSmoothOver.map { it.smallestContainingInterval })
				val finalProgress = when (cause) {
					null -> 1.0
					is CancellationException -> 0.0
					else -> throw cause
				}
				SmoothScope.submitUI { progress = finalProgress }
			}
		}

		job.join()

		return intervalsToSmoothOver
	}

	internal fun setNewSourceMask(maskedSource: MaskedSource<*, *>, maskInfo: MaskInfo, acceptMaskValue: (Long) -> Boolean = { it >= 0 }) {

		val (store, volatileStore) = maskedSource.createMaskStoreWithVolatile(maskInfo.level)
		val mask = SourceMask(maskInfo, store, volatileStore.rai, store.cache, volatileStore.invalidate) { store.shutdown() }
		maskedSource.setMask(mask, acceptMaskValue)
	}


	companion object {
		private val AffineTransform3D.resolution
			get() = doubleArrayOf(this[0, 0], this[1, 1], this[2, 2])

		fun nearestLabelImgs(
			sourceLabels: RandomAccessibleInterval<out RealType<*>>,
			canvasLabels: RandomAccessibleInterval<UnsignedLongType>,
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

			val distances = DiskCachedCellImgFactory(DoubleType(), options).create(sourceLabels)

			val labelsImg = DiskCachedCellImgFactory(UnsignedLongType(Label.INVALID), options).create(sourceLabels) { nearestLabelCell ->

				if (!blocksWithLabels.any { (it intersect nearestLabelCell).isNotEmpty() })
					return@create

				val canvasCursor = canvasLabels.interval(nearestLabelCell).cursor()
				val sourceCursor = sourceLabels.interval(nearestLabelCell).cursor()
				val voronoiCursor = nearestLabelCell.cursor()
				val distanceRa = distances.randomAccess(nearestLabelCell)

				while (canvasCursor.hasNext() && sourceCursor.hasNext()) {
					val canvasLabel = canvasCursor.next()
					val sourceLabel = sourceCursor.next()
					val label = canvasLabel.get().takeIf { it != Label.INVALID } ?: sourceLabel.realDouble.toLong()

					val labelInLabels = label in labelsToSmooth
					val acceptLabel = if (invert) !labelInLabels else labelInLabels

					val finalLabel = if (acceptLabel) {
						distanceRa.setPositionAndGet(canvasCursor).set(Double.MAX_VALUE)
						voronoiBackgroundLabel
					} else label
					voronoiCursor.next().set(finalLabel)
				}

				DistanceTransform.voronoiDistanceTransform(nearestLabelCell, distances.interval(nearestLabelCell), *sqWeights)
			}
			return labelsImg to distances
		}
	}
}