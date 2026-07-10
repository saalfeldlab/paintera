package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close

import gnu.trove.set.hash.TLongHashSet
import java.util.concurrent.atomic.AtomicInteger
import javafx.event.Event
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
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close.ClosedCellImage.Companion.createClosedCellImage
import org.janelia.saalfeldlab.paintera.control.actions.state.ViewerAndPaintableSourceActionState
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.numElements

internal object CloseStatus {
	object Closing : OperationStatus("Closing gaps...")
}

internal open class CloseLabelState<D, T>(delegate: CloseLabelModel = CloseLabelModel.default()) :
	ViewerAndPaintableSourceActionState<ConnectomicsLabelState<D, T>, D, T>(),
	CloseLabelModel by delegate
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	override var scaleLevel by lazyVar { viewer.state.bestMipMapLevel }
	override var timepoint by lazyVar { viewer.state.timepoint }

	override val activeFragment: Long get() = selectedIds.lastSelection
	override val allActiveFragments: LongArray get() = activeFragments()
	override val allActiveSegments: LongArray get() = activeSegments(activeFragments())
	override fun fragmentsForSegment(segment: Long): LongArray = assignment.getFragments(segment).toArray()
	override fun segmentForFragment(fragment: Long): Long = assignment.getSegment(fragment)
	override fun nextId() = super.nextId(activate = false)

	private val replacementLabelOrNull: Long? get() = if (specifyFillLabelProperty.get()) fillLabelProperty.get() else null

	/* the labels a fill may overwrite; null lets any regular label fill */
	private val fillableLabelsOrNull: LongArray? get() = when (replaceModeProperty.get()) {
		ReplaceMode.Any -> null
		ReplaceMode.Background -> longArrayOf(Label.BACKGROUND)
		ReplaceMode.Specify -> labelsToReplace.toLongArray()
	}

	var progress by progressProperty.nonnull()
	protected open val labelsImg: DiskCachedCellImg<UnsignedLongType, *> by LazyForeignValue({ timepoint to scaleLevel }) {
		createSourceAndCanvasImage(timepoint, scaleLevel)
	}

	override fun getLevelResolution(scaleLevel: Int) = resolutionAtLevel(scaleLevel)

	override fun <E : Event> verifyState(action: Action<E>) {
		super.verifyState(action)
		action.verify("Mask is in Use") { !this@CloseLabelState.maskedSource.isMaskInUseBinding().get() }
	}

	@set:Synchronized
	@get:Synchronized
	private var currentClosedCellImg: ClosedCellImage? = null

	/* the last computed preview mask, kept alive so the preview toggle can hide/show it without
	 * recomputing. `previewMaskValid` is cleared when a parameter changes while preview is off. */
	internal var previewMask: SourceMask? = null
	internal var previewMaskValid: Boolean = false

	@Synchronized
	fun getClosedCellImage(labelsToClose: LongArray, blocksWithLabels: Set<Interval>, cellDimensions: IntArray? = null): ClosedCellImage {

		return reuseClosedImage(labelsToClose, blocksWithLabels, cellDimensions) ?: setClosedImage(labelsToClose, blocksWithLabels, cellDimensions)
	}

	@Synchronized
	private fun setClosedImage(labels: LongArray, blocksWithLabels: Set<Interval>, cellDimensions: IntArray? = null): ClosedCellImage {
		return createClosedCellImage(
			labelsImg,
			labels,
			gapSizeProperty.get(),
			iterationsProperty.get(),
			fillableLabelsOrNull,
			replacementLabelOrNull,
			::segmentForFragment,
			blocksWithLabels,
			cellDimensions
		).also {
			currentClosedCellImg = it
		}
	}

	@Synchronized
	private fun reuseClosedImage(labelsToClose: LongArray, blocksWithLabel: Set<Interval>, cellDimensions: IntArray? = null): ClosedCellImage? {
		/*If we specify the desired dimensions, and the existing one doesn't match, then we can't reuse*/
		cellDimensions?.let {
			if (!cellDimensions.contentEquals(currentClosedCellImg?.img?.cellGrid?.cellDimensions))
				currentClosedCellImg = null
		}

		/* If the initial labels img has changed, then we can't re-use*/
		currentClosedCellImg?.let {
			if (it.initLabelsImg != labelsImg)
				currentClosedCellImg = null
		}

		return currentClosedCellImg?.takeIf {
			it.canReuse(labelsToClose, blocksWithLabel, gapSizeProperty.get(), iterationsProperty.get(), fillableLabelsOrNull, replacementLabelOrNull, ::segmentForFragment)
		}
	}

	suspend fun closeMask(preview: Boolean = false, update: UpdateSignal = UpdateSignal.Full, cellDimensions: IntArray? = null): Set<RealInterval> {

		/* If we are closing the final mask to apply over, we should use the cell dimensions equal to the output cell dimensions. */
		var cellDims = cellDimensions
		if (update == UpdateSignal.Finish) {
			scaleLevel = 0
			/*labelsImg must be queried AFTER scaleLevel is set, since it is a LazyForeignValue of the scaleLevel (and timepoint) */
			cellDims = labelsImg.cellGrid.cellDimensions
		}

		val targetLabels = labelsToClose.toLongArray()
		/* a listed segment id contributes its fragments' blocks */
		val blockLabels = TLongHashSet(targetLabels).also { set ->
			targetLabels.forEach { set.addAll(fragmentsForSegment(it)) }
		}.toArray()
		val blocksWithLabel = blocksForLabels(scaleLevel, blockLabels)

		val intervalsWithLabel = if (preview)
			viewerIntervalsInSourceSpace(intersectFilters = blocksWithLabel)
		else
			blocksWithLabel

		/* For preview, size the compute cells to the visible region so the morphology runs as one block
		 * per view rather than many kernel-sized tiles; cuts the per-cell padding overlap and avoids
		 * over-computing the full cell depth for a thin view slab. */
		val computeCellDims = if (preview && cellDims == null)
			previewCellDimensions(intervalsWithLabel, cellDims)
		else cellDims
		val closedCellImage = getClosedCellImage(targetLabels, blocksWithLabel, computeCellDims)

		if (update >= UpdateSignal.Full) {
			setStatus(CloseStatus.Closing)
			CloseGaps.submitUI {
				/* The listener only resets to zero if going backward, so do this first */
				progress = 0.0
				/* Just to show the operation has started */
				progress = .05
			}
		}


		/* a fillable gap run can extend up to the gap size outside a block with the label, so pad the
		 * processed intervals. For preview the visible slabs already span the screen in-plane, and extending
		 * would only add slices perpendicular to each view that are never displayed. */
		val intervalsToProcess = if (preview)
			intervalsWithLabel
		else
			intervalsWithLabel.mapTo(mutableSetOf()) { it.extendBy(*closedCellImage.padding) }

		/* a preview mask hidden by a prior toggle-off would otherwise leak when we recompute */
		previewMask?.takeIf { it !== maskedSource.currentMask }?.shutdown?.run()
		previewMask = null
		previewMaskValid = false

		val mask = newSourceMask()
		val processInterval: suspend CoroutineScope.(RealInterval) -> Flow<Int> = { slice ->
			flow {
				emit(0)

				val maskCursor = mask.rai.extendValue(Label.INVALID).interval(slice).cursor()
				val closedLabelsCursor = closedCellImage.img.extendValue(Label.INVALID).interval(slice).cursor()

				ensureActive()
				val emitUpdateAfter = slice.smallestContainingInterval.numElements() / 5
				var count = 0L
				while (closedLabelsCursor.hasNext()) {
					(++count).takeIf { it % emitUpdateAfter == 0L }?.let { emit(0) }
					val closed = closedLabelsCursor.next()
					val maskVal = maskCursor.next()
					maskVal.set(closed.get())
				}
			}
		}

		val totalUnits = intervalsToProcess.size
		val completedUnits = AtomicInteger(0)

		coroutineScope {
			launch {
				for (interval in intervalsToProcess) {
					launch {
						processInterval(interval).collect { }
						if (update >= UpdateSignal.Full) {
							val done = completedUnits.incrementAndGet()
							CloseGaps.submitUI { progress = .05 + .94 * (done.toDouble() / totalUnits) }
						}
					}
				}
			}.invokeOnCompletion { cause ->
				cause?.let { mask.shutdown?.run() }
				if (cause != null || update >= UpdateSignal.Full)
					requestRepaintOverIntervals()
				if (cause == null) {
					maskedSource.resetMasks()
					if (update == UpdateSignal.Finish) {
						/* apply path: the mask must be current so applyMaskOverIntervals can write it */
						maskedSource.setMask(mask) { it >= 0 }
						requestRepaintOverIntervals(intervalsToProcess.map { it.smallestContainingInterval })
					} else {
						/* preview: always compute, but only display the result when the toggle is on */
						if (previewProperty.get()) {
							maskedSource.setMask(mask) { it >= 0 }
							requestRepaintOverIntervals(intervalsToProcess.map { it.smallestContainingInterval })
						} else {
							requestRepaintOverIntervals()
						}
						previewMask = mask
						previewMaskValid = true
					}
				}
				val finalProgress = when (cause) {
					null -> 1.0
					is CancellationException -> 0.0
					else -> throw cause
				}
				CloseGaps.submitUI { progress = finalProgress }
				/* the computed mask is now displayed; Ready means it can be applied. the Finish apply path
				 * advances this to Applying/Done in CloseLabel.startCloseTask's completion handler */
				setStatus(if (cause == null) Status.Ready else Status.Empty)
			}
		}

		return intervalsToProcess
	}
}
