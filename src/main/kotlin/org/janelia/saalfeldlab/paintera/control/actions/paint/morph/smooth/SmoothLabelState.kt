package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.smooth

import com.google.common.util.concurrent.AtomicDouble
import javafx.event.Event
import javafx.util.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
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
import org.janelia.saalfeldlab.paintera.control.actions.state.ViewerAndPaintableSourceActionState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import org.janelia.saalfeldlab.util.*

internal object SmoothStatus {
    object Smoothing : OperationStatus("Smoothing...")
}

internal open class SmoothLabelState<D, T>(delegate: SmoothLabelModel = SmoothLabelModel.default()) :
    ViewerAndPaintableSourceActionState<ConnectomicsLabelState<D, T>, D, T>(),
    SmoothLabelModel by delegate
        where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

    override var scaleLevel by lazyVar { viewer.state.bestMipMapLevel }
    override var timepoint by lazyVar { viewer.state.timepoint }
    override val activeFragments by lazy { activeFragments() }
    override val fragmentsForActiveSegments by lazy { fragmentsForActiveSegments(activeFragments()) }

    var progress by progressProperty.nonnull()
    protected open val labelsImg: DiskCachedCellImg<UnsignedLongType, *> by LazyForeignValue({ timepoint to scaleLevel }) {
        createSourceAndCanvasImage(timepoint, scaleLevel)
    }
    protected open val labelsRai: RandomAccessibleInterval<UnsignedLongType> by LazyForeignValue({ labelsImg }) {
        it.extendValue(Label.INVALID).interval(it)
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
        action.verify("Mask is in Use") { !this@SmoothLabelState.maskedSource.isMaskInUseBinding().get() }
    }

    fun progressStatusSubscription(): Subscription = progressProperty.subscribe { progress ->
        val progress = progress.toDouble()
        val isApplyMask = maskedSource.isApplyingMaskProperty()
        statusProperty.value = when {
            progress == 0.0 -> Status.Empty
            progress == 1.0 -> Status.Done
            isApplyMask.get() -> Status.Applying
            progress > 0.0 && progress < 1.0 -> SmoothStatus.Smoothing
            else -> Status.Empty
        }
    }

    @set:Synchronized
    @get:Synchronized
    private var currentSmoothedCellImg: SmoothedCellImage? = null

    @Synchronized
    fun getSmoothedCellImage(
        labelsToSmooth: LongArray,
        blocksWithLabels: Set<Interval>,
        cellDimensions: IntArray? = null
    ): SmoothedCellImage {

        return reuseSmoothedImage(labelsToSmooth, blocksWithLabels, cellDimensions) ?: setSmoothedImage(
            labelsToSmooth,
            blocksWithLabels,
            cellDimensions
        )
    }

    @Synchronized
    private fun setSmoothedImage(
        labels: LongArray,
        blocksWithLabels: Set<Interval>,
        cellDimensions: IntArray? = null
    ): SmoothedCellImage {
        return SmoothedCellImage.createSmoothedCellImage(
            labelsRai,
            labels,
            { kernelSizeProperty.get().toDouble() },
            getLevelResolution(scaleLevel),
            morphDirectionProperty::get,
            infillStrategyProperty::get,
            replacementLabelProperty::get,
            gaussianThresholdProperty::get,
            blocksWithLabels,
            cellDimensions
        ).also {
            currentSmoothedCellImg = it
        }
    }

    @Synchronized
    private fun reuseSmoothedImage(
        labelsToSmooth: LongArray,
        blocksWithLabel: Set<Interval>,
        cellDimensions: IntArray? = null
    ): SmoothedCellImage? {
        /*If we specify the desired dimensions, and the existing one doesn't match, then we can't reuse*/
        cellDimensions?.let {
            if (!cellDimensions.contentEquals(currentSmoothedCellImg?.erodedCellImg?.img?.cellGrid?.cellDimensions))
                currentSmoothedCellImg = null
        }

        /* If the initial labels img has changed, then we can't re-use*/
        currentSmoothedCellImg?.let {
            if (it.labelsRai != labelsRai)
                currentSmoothedCellImg = null
        }

        return currentSmoothedCellImg?.invalidatedImageOrNull(
            labelsToSmooth,
            blocksWithLabel,
            kernelSizeProperty.get().toDouble(),
            infillStrategyProperty.get(),
            replacementLabelProperty.get(),
            gaussianThresholdProperty.get()
        )
    }

    suspend fun smoothMask(
        preview: Boolean = false,
        update: UpdateSignal = UpdateSignal.Full,
        cellDimensions: IntArray? = null
    ): Set<RealInterval> {

        /* If we are smoothing the final mask to apply over, we should use the cell dimensions equal to the output cell dimensions. */
        val cellDims = if (update == UpdateSignal.Finish) {
            /*labelsImg must be queried AFTER scaleLevel is set, since it is a LazyForeignValue of the scaleLevel (and timepoint) */
            scaleLevel = 0
            labelsImg.cellGrid.cellDimensions
        } else {
            cellDimensions
        }
        val labelsToSmooth = getSelectedLabels()
        val blocksWithLabel = blocksForLabels(scaleLevel, labelsToSmooth)
        val smoothedCellImage = getSmoothedCellImage(labelsToSmooth, blocksWithLabel, cellDims)

        if (update >= UpdateSignal.Full)
            SmoothLabel.submitUI {
                /* The listener only resets to zero if going backward, so do this first */
                progress = 0.0
                /* Just to show the operation has started */
                progress = .05
            }


        val intervalsWithLabel = if (preview) {
            val viewerIntervals = viewerIntervalsInSourceSpace()
            blocksWithLabel.flatMap { labelBlock ->
                viewerIntervals.mapNotNull { viewerInterval ->
                    viewerInterval.intersect(labelBlock).takeIf { it.isNotEmpty() }
                }
            }.toSet()
        } else
            blocksWithLabel


        val intervalsToProcess =
            intervalsWithLabel.mapTo(mutableSetOf()) { it.extendBy(*smoothedCellImage.kernelSizePadding) }

        val mask = newSourceMask()
        val processInterval: suspend CoroutineScope.(RealInterval) -> Flow<Int> = { slice ->
            flow {
                emit(0)

                val maskCursor = mask.rai.extendValue(Label.INVALID).interval(slice).cursor()
                val smoothedImgCursor = smoothedCellImage.img.extendValue(Label.INVALID).interval(slice).cursor()

                val emitUpdateAfter = slice.smallestContainingInterval.numElements() / 5
                var count = 0L
                while (smoothedImgCursor.hasNext()) {
                    (++count).takeIf { it % emitUpdateAfter == 0L }?.let { emit(0) }
                    val nearest = smoothedImgCursor.next()
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
                                SmoothLabel.submitUI { progress = localProgress.get() }
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
                SmoothLabel.submitUI { progress = finalProgress }
            }
        }

        return intervalsToProcess
    }
}