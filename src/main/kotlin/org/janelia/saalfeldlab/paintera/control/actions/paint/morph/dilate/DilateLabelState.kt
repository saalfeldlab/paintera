package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.dilate

import com.google.common.util.concurrent.AtomicDouble
import javafx.event.Event
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.Volatile
import net.imglib2.cache.img.DiskCachedCellImg
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.volatiles.VolatileUnsignedLongType
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.lazyVar
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.*
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.dilate.DilateLabel.DilateScope
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.dilate.DilatedCellImage.Companion.createDilatedCellImage
import org.janelia.saalfeldlab.paintera.control.actions.state.ViewerAndPaintableSourceActionState
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.numElements
import org.janelia.saalfeldlab.util.translate

internal object DilateStatus {
	object Dilating : OperationStatus("Expanding...")
}

internal open class DilateLabelState<D, T>(delegate: DilateLabelModel = DilateLabelModel.default()) :
	ViewerAndPaintableSourceActionState<ConnectomicsLabelState<D, T>, D, T>(),
	DilateLabelModel by delegate
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	override var scaleLevel by lazyVar { viewer.state.bestMipMapLevel }
	override var timepoint by lazyVar { viewer.state.timepoint }
	override val activeFragments by lazy { activeFragments() }
	override val fragmentsForActiveSegments by lazy { fragmentsForActiveSegments(activeFragments()) }

	var progress by progressProperty.nonnull()
	protected open val labelsImg: DiskCachedCellImg<UnsignedLongType, *> by LazyForeignValue({ timepoint to scaleLevel }) {
		createSourceAndCanvasImage(timepoint, scaleLevel)
	}

	override fun getLevelResolution(scaleLevel: Int) = resolutionAtLevel(scaleLevel)
	override fun nextId() = super.nextId(activate = false)
	override fun getSelectedLabels(): LongArray {
		return when (labelSelectionProperty.get()) {
			LabelSelection.ActiveFragments -> activeFragments
			LabelSelection.ActiveSegments -> fragmentsForActiveSegments
		}
	}

	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)
		action.verify("Mask is in Use") { !this@DilateLabelState.maskedSource.isMaskInUseBinding().get() }
	}

	fun progressStatusSubscription(): Subscription = progressProperty.subscribe { progress ->
		val progress = progress.toDouble()
		val isApplyMask = maskedSource.isApplyingMaskProperty()
		statusProperty.value = when {
			progress == 0.0 -> Status.Empty
			progress == 1.0 -> Status.Done
			isApplyMask.get() -> Status.Applying
			progress > 0.0 && progress < 1.0 -> DilateStatus.Dilating
			else -> Status.Empty
		}
	}

	@set:Synchronized
	@get:Synchronized
	private var currentDilatedCellImg: DilatedCellImage? = null

	@Synchronized
	fun getDilatedCellImage(labelsToDilate: LongArray, blocksWithLabels: Set<Interval>, cellDimensions: IntArray? = null): DilatedCellImage {

		return reuseDilatedImage(labelsToDilate, blocksWithLabels, cellDimensions) ?: setDilatedImage(labelsToDilate, blocksWithLabels, cellDimensions)
	}

	@Synchronized
	private fun setDilatedImage(labels: LongArray, blocksWithLabels: Set<Interval>, cellDimensions: IntArray? = null): DilatedCellImage {
		return createDilatedCellImage(
			labelsImg,
			labels,
			{ kernelSizeProperty.get().toDouble() },
			getLevelResolution(scaleLevel),
			infillStrategyProperty::get,
			replacementLabelProperty::get,
			blocksWithLabels,
			cellDimensions
		).also {
			currentDilatedCellImg = it
		}
	}

	@Synchronized
	private fun reuseDilatedImage(labelsToDilate: LongArray, blocksWithLabel: Set<Interval>, cellDimensions: IntArray? = null): DilatedCellImage? {
		/*If we specify the desired dimensions, and the existing one doesn't match, then we can't reuse*/
		cellDimensions?.let {
			if (!cellDimensions.contentEquals(currentDilatedCellImg?.img?.cellGrid?.cellDimensions))
				currentDilatedCellImg = null
		}

		/* If the initial labels img has changed, then we can't re-use*/
		currentDilatedCellImg?.let {
			if (it.initLabelsImg != labelsImg)
				currentDilatedCellImg = null
		}

		return currentDilatedCellImg?.invalidatedImageOrNull(
			labelsToDilate,
			blocksWithLabel,
			kernelSizeProperty.get().toDouble(),
			infillStrategyProperty.get(),
			replacementLabelProperty.get()
		)
	}

	suspend fun dilateMask(preview: Boolean = false, update: UpdateSignal = UpdateSignal.Full, cellDimensions: IntArray? = null): Set<RealInterval> {

		/* If we are dilating the final mask to apply over, we should use the cell dimensions equal to the output cell dimensions. */
		var cellDims = cellDimensions
		if (update == UpdateSignal.Finish) {
			scaleLevel = 0
			/*labelsImg must be queried AFTER scaleLevel is set, since it is a LazyForeignValue of the scaleLevel (and timepoint) */
			cellDims = labelsImg.cellGrid.cellDimensions
		}

		val labelsToDilate = getSelectedLabels()
		val blocksWithLabel = blocksForLabels(scaleLevel, labelsToDilate)
		val dilatedCellImage = getDilatedCellImage(labelsToDilate, blocksWithLabel, cellDims)

		if (update >= UpdateSignal.Full)
			DilateLabel.submitUI {
				/* The listener only resets to zero if going backward, so do this first */
				progress = 0.0
				/* Just to show the operation has started */
				progress = .05
			}


		val intervalsWithLabel = if (preview)
			viewerIntervalsInSourceSpace(intersectFilters = blocksWithLabel)
		else
			blocksWithLabel

		/* Dilation needs to pad the intervals based on the kernel size, so when
		* processing the intervals, we need to ensure we account for the padding.
		* Potentially, we could track the interval to find the minimal extend we can process,
		* instead of always doing the max. This should otherwise be safe to do though.*/
		val intervalsToProcess = intervalsWithLabel.mapTo(mutableSetOf()) { it.extendBy(*dilatedCellImage.kernelSizePadding) }

		val mask = newSourceMask()
		val processInterval: suspend CoroutineScope.(RealInterval) -> Flow<Int> = { slice ->
			flow {
				emit(0)

				val maskCursor = mask.rai.extendValue(Label.INVALID).interval(slice).cursor()
				val nearestLabelsCursor = dilatedCellImage.img.extendValue(Label.INVALID).interval(slice).cursor()

				ensureActive()
				val emitUpdateAfter = slice.smallestContainingInterval.numElements() / 5
				var count = 0L
				while (nearestLabelsCursor.hasNext()) {
					(++count).takeIf { it % emitUpdateAfter == 0L }?.let { emit(0) }
					val nearest = nearestLabelsCursor.next()
					val maskVal = maskCursor.next()
					maskVal.set(nearest.get())
				}
			}
		}

		val localProgress = AtomicDouble(.1)

		coroutineScope {
			launch {
				val increment = (.99 - .1) / intervalsToProcess.size
				for (interval in intervalsToProcess) {
					launch {
						var approachTotal = 0.0
						processInterval(interval).collect { _ ->
							if (update >= UpdateSignal.Full) {
								val addProgress = (increment - approachTotal) * .25
								approachTotal += addProgress
								localProgress.updateAndGet { (it + addProgress).coerceAtMost(1.0) }
								DilateLabel.submitUI { progress = localProgress.get() }
							}
						}
					}
				}
			}.invokeOnCompletion { cause ->
				if (cause != null || update >= UpdateSignal.Full)
					requestRepaintOverIntervals()
				if (cause == null) {

					maskedSource.resetMasks()
					maskedSource.setMask(mask) { it >= 0 }
					requestRepaintOverIntervals(intervalsToProcess.map { it.smallestContainingInterval })
				}
				val finalProgress = when (cause) {
					null -> 1.0
					is CancellationException -> 0.0
					else -> throw cause
				}
				DilateLabel.submitUI { progress = finalProgress }
			}
		}

		return intervalsToProcess
	}
}