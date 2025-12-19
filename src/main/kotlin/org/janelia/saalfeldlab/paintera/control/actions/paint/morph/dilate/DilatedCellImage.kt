package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.dilate

import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.DiskCachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.type.label.Label
import net.imglib2.type.label.Label.BACKGROUND
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.InfillStrategy
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphOperations
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.isEmpty
import kotlin.math.ceil

data class DilatedCellImage(
	val initLabelsImg: RandomAccessibleInterval<UnsignedLongType>,
	val img: DiskCachedCellImg<UnsignedLongType, *>,
	val dilatedLabels: LongArray,
	val intervalsToDilate: Set<Interval>,
	val kernelSize: Double,
	val kernelSizePadding: IntArray,
	var infillStrategy: InfillStrategy,
	var replacementLabel: Long,
) {

	/**
	 * Invalidate if possible. See [canInvalidate] for valid states.
	 *
	 * @param kernelSize desired after invalidation
	 * @param labelsToDilate desired after invalidation
	 * @param infillStrategy desired after invalidation
	 * @param replacementLabel desired after invalidation
	 * @return true if the image was invalidated, else false.
	 */
	fun invalidatedImageOrNull(
		labelsToDilate: LongArray,
		intervalsToDilate: Set<Interval>,
		kernelSize: Double,
		infillStrategy: InfillStrategy,
		replacementLabel: Long,
	): DilatedCellImage? {
		val canInvalidate = canInvalidate(kernelSize, labelsToDilate, intervalsToDilate, infillStrategy, replacementLabel)
		if (canInvalidate) {
			invalidate(infillStrategy, replacementLabel)
			return this
		}
		return null
	}

	/**
	 * Can invalidate if the new kernel size is smaller or the same as the initial kernel size, and
	 * at least one of the other properties is different.
	 * - If the kernel size is larger, a new image needs to be generated, and invalidation is not possible
	 * - If the dilated labels changes, a new image needs to be generated
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
		labelsToDilate: LongArray,
		intervalsToDilate: Set<Interval>,
		infillStrategy: InfillStrategy,
		replacementLabel: Long,
	): Boolean = when {
		kernelSize > this.kernelSize -> false
		!dilatedLabels.contentEquals(labelsToDilate) -> false
		intervalsToDilate != this.intervalsToDilate -> false
		kernelSize < this.kernelSize -> true
		infillStrategy != this.infillStrategy -> true
		infillStrategy == InfillStrategy.Replace && replacementLabel != this.replacementLabel -> true
		else -> false
	}


	@Synchronized
	private fun invalidate(infill: InfillStrategy, replaceLabel: Long) {
		infillStrategy = infill
		replacementLabel = replaceLabel
		img.cache.invalidateAll()
	}

	companion object {
		fun createDilatedCellImage(
			initialLabels: RandomAccessibleInterval<UnsignedLongType>,
			labelsToDilate: LongArray,
			kernelSize: () -> Double, //Kernel size to dilate with in the same physical units
			resolution: DoubleArray,
			infillStrategy: () -> InfillStrategy,
			replacementLabel: () -> Long,
			intervalsToDilate: Set<Interval> = emptySet(), //if not empty, cells that don't intersect with any of these intervals will be left unprocessed.
			cellDimensions: IntArray? = null, //optional override
		): DilatedCellImage {

			val initKernelSize = kernelSize()
			val kernelSizeInPixels = IntArray(resolution.size) {
				ceil(initKernelSize / resolution[it]).coerceAtLeast(1.0).toInt()
			}
			val cellDimensions = cellDimensions ?: IntArray(resolution.size) { (kernelSizeInPixels[it] * 4).coerceAtLeast(32) }

			val sqWeights = resolution
				.map { weight -> weight * weight }
				.toDoubleArray()


			val (paddedVoronoiLabels, paddedDistanceLabels) = MorphOperations.paddedCellVoronoiDistanceTransform(
				cellDimensions,
				kernelSizeInPixels,
				initialLabels,
				true,
				labelsToDilate,
				sqWeights
			)

			val dilateExtent = intervalsToDilate.map { it.extendBy(*kernelSizeInPixels) }

			/**
			 * Process the block if it intersects the blocks with label (plus padding) OR the blocksWithLabel is empty.
			 *
			 * @param interval to test for intersection
			 * @return if the block should be processed or not
			 */
			fun blockOutOfRange(interval: Interval): Boolean {
				if (dilateExtent.isEmpty())
					return false
				return dilateExtent.all { (it intersect interval.asRealInterval).isEmpty() }
			}
			val extendedInitialLabels = Views.extendValue(initialLabels, Label.INVALID)

			val dilatedImg = DiskCachedCellImgFactory(
				UnsignedLongType(Label.TRANSPARENT),
				DiskCachedCellImgOptions.options().cellDimensions(*cellDimensions)
			).create(initialLabels) { target ->

				val sqKernelSize = kernelSize().let { it * it }


				if (blockOutOfRange(target)) {
					target.forEach { it.set(Label.TRANSPARENT) }
					return@create
				}

				val paddedLabelsCursor = paddedVoronoiLabels.interval(target).cursor()
				val paddedDistancesCursor = paddedDistanceLabels.interval(target).cursor()

				val resultCursor = target.cursor()


				val initialCursor = extendedInitialLabels.interval(target).cursor()
				val strategy = infillStrategy()
				val replacement = replacementLabel()
				while (resultCursor.hasNext()) {
					val resultLabel = resultCursor.next()
					paddedLabelsCursor.fwd()
					paddedDistancesCursor.fwd()

					/* Small optimization; no need to avoid infilling the existing with nearest, it should always be the same */
					val initialValue = initialCursor.next().get()
					if (strategy != InfillStrategy.NearestLabel) {
						if (initialValue in labelsToDilate) {
							resultLabel.set(initialValue)
							continue
						}
					}
					/* We need to do this here to compute the distances even if we we are not expanding with nearestLabel */
					val nearestLabel = paddedLabelsCursor.get()
					val distances = paddedDistancesCursor.get()
					val label = if (distances.get() <= sqKernelSize) {
						when (strategy) {
							InfillStrategy.Replace -> replacement
							InfillStrategy.Background -> BACKGROUND
							InfillStrategy.NearestLabel -> nearestLabel.get()
						}
					} else initialValue

					resultLabel.set(label)
				}
			}

			val initStrategy = infillStrategy()
			val replaceLabel = replacementLabel()

			return DilatedCellImage(
				initialLabels,
				dilatedImg,
				labelsToDilate,
				intervalsToDilate,
				initKernelSize,
				kernelSizeInPixels,
				initStrategy,
				replaceLabel
			)
		}
	}
}