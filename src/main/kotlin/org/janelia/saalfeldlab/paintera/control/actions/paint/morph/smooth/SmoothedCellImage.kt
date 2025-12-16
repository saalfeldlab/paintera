package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.smooth

import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.DiskCachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.type.label.Label
import net.imglib2.type.label.Label.BACKGROUND
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.InfillStrategy
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphDirection
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphOperations
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.dilate.DilatedCellImage
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.erode.ErodedCellImage
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.isEmpty
import kotlin.math.ceil

data class SmoothedCellImage(
    val initLabelsImg: RandomAccessibleInterval<UnsignedLongType>,
    val gaussianSmoothingMask: RandomAccessibleInterval<DoubleType>,
    val erodedCellImg: ErodedCellImage,
    val dilatedCellImg: DilatedCellImage,
    val img: DiskCachedCellImg<UnsignedLongType, *>,
    val smoothedLabels: LongArray,
    val intervalsToSmooth: Set<Interval>,
    val kernelSize: Double,
    val kernelSizePadding: IntArray,
    var gaussianThreshold: Double,
    var infillStrategy: InfillStrategy,
    var replacementLabel: Long,
) {

    /**
     * Invalidate if possible. See [canInvalidate] for valid states.
     *
     * @param kernelSize desired after invalidation
     * @param labelsToSmooth desired after invalidation
     * @param infillStrategy desired after invalidation
     * @param replacementLabel desired after invalidation
     * @return true if the image was invalidated, else false.
     */
    fun invalidatedImageOrNull(
        labelsToSmooth: LongArray,
        intervalsToSmooth: Set<Interval>,
        kernelSize: Double,
        infillStrategy: InfillStrategy,
        replacementLabel: Long,
        threshold: Double,
    ): SmoothedCellImage? {
        val canInvalidate =
            canInvalidate(kernelSize, labelsToSmooth, intervalsToSmooth, infillStrategy, replacementLabel, threshold)
        if (canInvalidate) {
            invalidate(infillStrategy, replacementLabel, threshold)
            return this
        }
        return null
    }

    /**
     * Can invalidate if the new kernel size is smaller or the same as the initial kernel size, and
     * at least one of the other properties is different.
     * - If the kernel size is larger, a new image needs to be generated, and invalidation is not possible
     * - If the smoothed labels changes, a new image needs to be generated
     * - If the kernel size is smaller, we can invalidate
     * - If kernel size is the same, we can invalidate if
     *  - infill strategy has changed
     *  - infill strategy is [InfillStrategy.Replace] and replacementLabel has changed
     *
     *
     * @param kernelSize
     * @param infillStrategy
     */
    fun canInvalidate(
        kernelSize: Double,
        labelsToSmooth: LongArray,
        intervalsToSmooth: Set<Interval>,
        infillStrategy: InfillStrategy,
        replacementLabel: Long,
        threshold: Double,
    ): Boolean {
        return when {
            kernelSize > this.kernelSize -> false
            !smoothedLabels.contentEquals(labelsToSmooth) -> false
            intervalsToSmooth != this.intervalsToSmooth -> false
            kernelSize < this.kernelSize -> true
            infillStrategy != this.infillStrategy -> true
            infillStrategy == InfillStrategy.Replace && replacementLabel != this.replacementLabel -> true
            threshold != this.gaussianThreshold -> true
            else -> false
        }
    }


    @Synchronized
    private fun invalidate(infill: InfillStrategy, replaceLabel: Long, threshold: Double) {
        if (infill != this.infillStrategy || replaceLabel != this.replacementLabel) {
            erodedCellImg.img.cache.invalidateAll()
            dilatedCellImg.img.cache.invalidateAll()
        }

        infillStrategy = infill
        replacementLabel = replaceLabel
        gaussianThreshold = threshold
        img.cache.invalidateAll()
    }

    companion object {
        fun createSmoothedCellImage(
            initialLabels: RandomAccessibleInterval<UnsignedLongType>,
            labelsToSmooth: LongArray,
            kernelSize: () -> Double, //Kernel size to smooth with in the same physical units
            resolution: DoubleArray,
            morphDirection: () -> MorphDirection,
            infillStrategy: () -> InfillStrategy,
            replacementLabel: () -> Long,
            gaussianThreshold: () -> Double,
            intervalsToSmooth: Set<Interval> = emptySet(), //if not empty, cells that don't intersect with any of these intervals will be left unprocessed.
            cellDimensions: IntArray? = null, //optional override
        ): SmoothedCellImage {

            val erodedCellImage = ErodedCellImage.createErodedCellImage(
                initialLabels,
                labelsToSmooth,
                kernelSize,
                resolution,
                infillStrategy,
                replacementLabel,
                intervalsToSmooth,
                cellDimensions
            )
            val dilatedCellImage = DilatedCellImage.createDilatedCellImage(
                initialLabels,
                labelsToSmooth,
                kernelSize,
                resolution,
                infillStrategy,
                replacementLabel,
                intervalsToSmooth,
                cellDimensions
            )

            val labelsMask = DiskCachedCellImgFactory(DoubleType(0.0)).create(initialLabels) { cell ->
                val labels = initialLabels.interval(cell).cursor()
                val mask = cell.cursor()
                while (mask.hasNext()) {
                    val maskVal = mask.next()
                    val label = labels.next().get()
                    if (label in labelsToSmooth)
                        maskVal.set(1.0)
                }
            }

            val initKernelSize = kernelSize()
            val kernelSizeInPixels = IntArray(resolution.size) {
                ceil(initKernelSize / resolution[it]).coerceAtLeast(1.0).toInt()
            }
            val cellDimensions =
                cellDimensions ?: IntArray(resolution.size) { (kernelSizeInPixels[it] * 4).coerceAtLeast(32) }

            val sigma = DoubleArray(3) { kernelSize() / resolution[it] }
            val (gaussianSmoothingMask) = MorphOperations.paddedCellGaussianSmoothing(
                cellDimensions,
                kernelSizeInPixels,
                labelsMask,
                sigma
            )

            val smoothExtent = intervalsToSmooth.map { it.extendBy(*kernelSizeInPixels) }

            /**
             * Process the block if it intersects the blocks with label (plus padding) OR the blocksWithLabel is empty.
             *
             * @param interval to test for intersection
             * @return if the block should be processed or not
             */
            fun blockOutOfRange(interval: Interval): Boolean {
                if (smoothExtent.isEmpty())
                    return false
                return smoothExtent.all { (it intersect interval.asRealInterval).isEmpty() }
            }

            val extendedLabelsMask = Views.extendValue(labelsMask, 0.0)

            val smoothedImg = DiskCachedCellImgFactory(
                UnsignedLongType(Label.TRANSPARENT),
                DiskCachedCellImgOptions.options().cellDimensions(*cellDimensions)
            ).create(initialLabels) { smoothedCell ->

                val direction = morphDirection()
                val infill = infillStrategy()
                val replacement = replacementLabel()
                val threshold = gaussianThreshold()

                val shrink = direction == MorphDirection.Shrink || direction == MorphDirection.Both
                val expand = direction == MorphDirection.Expand || direction == MorphDirection.Both

                if (blockOutOfRange(smoothedCell)) {
                    smoothedCell.forEach { it.set(Label.TRANSPARENT) }
                    return@create
                }


                val initialLabelsCursor = initialLabels.interval(smoothedCell).cursor()
                val labelsMaskCursor = extendedLabelsMask.interval(smoothedCell).cursor()
                val erodedLabelsCursor = erodedCellImage.img.interval(smoothedCell).cursor()
                val dilatedLabelsCursor = dilatedCellImage.img.interval(smoothedCell).cursor()
                val gaussianCursor = gaussianSmoothingMask.interval(smoothedCell).cursor()

                val smoothedLabelsCursor = smoothedCell.cursor()

                while (smoothedLabelsCursor.hasNext()) {
                    val smoothedLabel = smoothedLabelsCursor.next()
                    val gaussian = gaussianCursor.next().get()

                    initialLabelsCursor.fwd()
                    labelsMaskCursor.fwd()
                    erodedLabelsCursor.fwd()
                    dilatedLabelsCursor.fwd()

                    val wasLabel by lazy(LazyThreadSafetyMode.NONE) { labelsMaskCursor.get().get() == 1.0 }

                    val smoothFillLabel = when {
                        gaussian < threshold && shrink && wasLabel -> {
                            when (infill) {
                                InfillStrategy.Replace -> replacement
                                InfillStrategy.Background -> BACKGROUND
                                InfillStrategy.NearestLabel -> erodedLabelsCursor.get().get()
                            }
                        }

                        gaussian >= threshold && expand && !wasLabel -> {
                            dilatedLabelsCursor.get().get()
                        }

                        wasLabel -> {
                            dilatedLabelsCursor.get().get()
                        }

                        else -> initialLabelsCursor.get().get()
                    }
                    smoothedLabel.set(smoothFillLabel)
                }
            }

            val initStrategy = infillStrategy()
            val replaceLabel = replacementLabel()
            val initThreshold = gaussianThreshold()

            return SmoothedCellImage(
                initialLabels,
                gaussianSmoothingMask,
                erodedCellImage,
                dilatedCellImage,
                smoothedImg,
                labelsToSmooth,
                intervalsToSmooth,
                initKernelSize,
                kernelSizeInPixels,
                initThreshold,
                initStrategy,
                replaceLabel
            )
        }
    }
}