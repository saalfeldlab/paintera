package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.erode

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

data class ErodedCellImage(
	val initLabelsImg: RandomAccessibleInterval<UnsignedLongType>,
	val img: DiskCachedCellImg<UnsignedLongType, *>,
	val erodedLabels: LongArray,
	val intervalsToErode: Set<Interval>,
	val kernelSize: Double,
	val kernelSizePadding: IntArray,
	var infillStrategy: InfillStrategy,
	var replacementLabel: Long,
) {

	/**
	 * Invalidate if possible. See [canInvalidate] for valid states.
	 *
	 * @param kernelSize desired after invalidation
	 * @param labelsToErode desired after invalidation
	 * @param infillStrategy desired after invalidation
	 * @param replacementLabel desired after invalidation
	 * @return true if the image was invalidated, else false.
	 */
	fun invalidatedImageOrNull(
		labelsToErode: LongArray,
		intervalsToErode: Set<Interval>,
		kernelSize: Double,
		infillStrategy: InfillStrategy,
		replacementLabel: Long,
	): ErodedCellImage? {
		val canInvalidate = canInvalidate(kernelSize, labelsToErode, intervalsToErode, infillStrategy, replacementLabel)
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
	 * - If the eroded labels changes, a new image needs to be generated
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
		labelsToErode: LongArray,
		intervalsToErode: Set<Interval>,
		infillStrategy: InfillStrategy,
		replacementLabel: Long,
	): Boolean = when {
		kernelSize > this.kernelSize -> false
		!erodedLabels.contentEquals(labelsToErode) -> false
		intervalsToErode != this.intervalsToErode -> false
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
		fun createErodedCellImage(
			initialLabels: RandomAccessibleInterval<UnsignedLongType>,
			labelsToErode: LongArray,
			kernelSize: () -> Double, //Kernel size to erode with in the same physical units
			resolution: DoubleArray,
			infillStrategy: () -> InfillStrategy,
			replacementLabel: () -> Long,
			intervalsToErode: Set<Interval> = emptySet(), //if not empty, cells that don't intersect with any of these intervals will be left unprocessed.
			cellDimensions: IntArray? = null, //optional override
		): ErodedCellImage {

			val initKernelSize = kernelSize()
			val kernelSizeInPixels = IntArray(resolution.size) {
				ceil(initKernelSize / resolution[it]).coerceAtLeast(1.0).toInt()
			}
			val cellDimensions = cellDimensions ?: IntArray(resolution.size) { (kernelSizeInPixels[it] * 4).coerceAtLeast(32) }

			val sqWeights = resolution
				.mapIndexed { idx, weight -> weight * weight }
				.toDoubleArray()


			val (paddedVoronoiLabels, paddedDistanceLabels) = MorphOperations.paddedCellVoronoiDistanceTransform(
				cellDimensions,
				kernelSizeInPixels,
				initialLabels,
				false,
				labelsToErode,
				sqWeights
			)

			val erodeExtent = intervalsToErode.map { it.extendBy(*kernelSizeInPixels) }

			/**
			 * Process the block if it intersects the blocks with label (plus padding) OR the blocksWithLabel is empty.
			 *
			 * @param interval to test for intersection
			 * @return if the block should be processed or not
			 */
			fun blockOutOfRange(interval: Interval): Boolean {
				if (erodeExtent.isEmpty())
					return false
				return erodeExtent.all { (it intersect interval.asRealInterval).isEmpty() }
			}
			val extendedInitialLabels = Views.extendValue(initialLabels, Label.INVALID)

			val erodedImg = DiskCachedCellImgFactory(
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

					val initialLabel = initialCursor.next()
					/* If the initial label was not in a target label, then we aren't changing anything */
					if (initialLabel.get() !in labelsToErode) {
						resultLabel.set(Label.TRANSPARENT)
						continue
					}

					val distances = paddedDistancesCursor.get()
					val label = if (distances.get() <= sqKernelSize) {
						when (strategy) {
							InfillStrategy.Replace -> replacement
							InfillStrategy.Background -> BACKGROUND
							InfillStrategy.NearestLabel -> paddedLabelsCursor.get().get()
						}
					} else Label.TRANSPARENT

					resultLabel.set(label)
				}
			}

			val initStrategy = infillStrategy()
			val replaceLabel = replacementLabel()

			return ErodedCellImage(
				initialLabels,
				erodedImg,
				labelsToErode,
				intervalsToErode,
				initKernelSize,
				kernelSizeInPixels,
				initStrategy,
				replaceLabel
			)
		}
	}
}