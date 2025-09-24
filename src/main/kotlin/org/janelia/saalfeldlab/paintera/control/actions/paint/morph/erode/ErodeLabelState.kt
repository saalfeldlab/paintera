package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.erode

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
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.lazyVar
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.*
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.erode.ErodeLabel.ErodeScope
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.erode.ErodedCellImage.Companion.createErodedCellImage
import org.janelia.saalfeldlab.paintera.control.actions.state.ViewerAndPaintableSourceActionState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.numElements

internal object ErodeStatus {
	object Eroding : OperationStatus("Shrinking...")
}

internal open class ErodeLabelState<D, T>(delegate: ErodeLabelModel = ErodeLabelModel.default()) :
	ViewerAndPaintableSourceActionState<ConnectomicsLabelState<D, T>, D, T>(),
	ErodeLabelModel by delegate
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
		action.verify("Mask is in Use") { !this@ErodeLabelState.maskedSource.isMaskInUseBinding().get() }
	}

	fun progressStatusSubscription(): Subscription = progressProperty.subscribe { progress ->
		val progress = progress.toDouble()
		val isApplyMask = maskedSource.isApplyingMaskProperty()
		statusProperty.value = when {
			progress == 0.0 -> Status.Empty
			progress == 1.0 -> Status.Done
			isApplyMask.get() -> Status.Applying
			progress > 0.0 && progress < 1.0 -> ErodeStatus.Eroding
			else -> Status.Empty
		}
	}

	@set:Synchronized
	@get:Synchronized
	private var currentErodedCellImg: ErodedCellImage? = null

	@Synchronized
	fun getErodedCellImage(labelsToErode: LongArray, blocksWithLabels: Set<Interval>, cellDimensions: IntArray? = null): ErodedCellImage {

		return reuseErodedImage(labelsToErode, blocksWithLabels, cellDimensions) ?: setErodedImage(labelsToErode, blocksWithLabels, cellDimensions)
	}

	@Synchronized
	private fun setErodedImage(labels: LongArray, blocksWithLabels: Set<Interval>, cellDimensions: IntArray? = null): ErodedCellImage {
		return createErodedCellImage(
			labelsImg,
			labels,
			{ kernelSizeProperty.get().toDouble() },
			getLevelResolution(scaleLevel),
			infillStrategyProperty::get,
			replacementLabelProperty::get,
			blocksWithLabels,
			cellDimensions
		).also {
			currentErodedCellImg = it
		}
	}

	@Synchronized
	private fun reuseErodedImage(labelsToErode: LongArray, blocksWithLabel: Set<Interval>, cellDimensions: IntArray? = null): ErodedCellImage? {
		/*If we specify the desired dimensions, and the existing one doesn't match, then we can't reuse*/
		cellDimensions?.let {
			if (!cellDimensions.contentEquals(currentErodedCellImg?.img?.cellGrid?.cellDimensions))
				currentErodedCellImg = null
		}

		/* If the initial labels img has changed, then we can't re-use*/
		currentErodedCellImg?.let {
			if (it.initLabelsImg != labelsImg)
				currentErodedCellImg = null
		}

		return currentErodedCellImg?.invalidatedImageOrNull(
			labelsToErode,
			blocksWithLabel,
			kernelSizeProperty.get().toDouble(),
			infillStrategyProperty.get(),
			replacementLabelProperty.get()
		)
	}

	suspend fun erodeMask(preview: Boolean = false, update: UpdateSignal = UpdateSignal.Full, cellDimensions: IntArray? = null): Set<RealInterval> {

		/* If we are eroding the final mask to apply over, we should use the cell dimensions equal to the output cell dimensions. */
		var cellDims = cellDimensions
		if (update == UpdateSignal.Finish) {
			scaleLevel = 0
			/*labelsImg must be queried AFTER scaleLevel is set, since it is a LazyForeignValue of the scaleLevel (and timepoint) */
			cellDims = labelsImg.cellGrid.cellDimensions
		}

		val labelsToErode = getSelectedLabels()
		val blocksWithLabel = blocksForLabels(scaleLevel, labelsToErode)
		val erodedCellImage = getErodedCellImage(labelsToErode, blocksWithLabel, cellDims)

		if (update >= UpdateSignal.Full)
			ErodeScope.submitUI {
				/* The listener only resets to zero if going backward, so do this first */
				progress = 0.0
				/* Just to show the operation has started */
				progress = .05
			}


		val intervalsWithLabel = if (preview)
			viewerIntervalsInSourceSpace(intersectFilters = blocksWithLabel)
		else
			blocksWithLabel

		val intervalsToProcess = intervalsWithLabel

		val mask = newSourceMask()
		val processInterval: suspend CoroutineScope.(RealInterval) -> Flow<Int> = { slice ->
			flow {
				emit(0)

				val maskCursor = mask.rai.extendValue(Label.INVALID).interval(slice).cursor()
				val nearestLabelsCursor = erodedCellImage.img.extendValue(Label.INVALID).interval(slice).cursor()

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
								ErodeScope.submitUI { progress = localProgress.get() }
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
				ErodeScope.submitUI { progress = finalProgress }
			}
		}

		return intervalsToProcess
	}
}