package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import net.imglib2.RandomAccessibleInterval
import net.imglib2.algorithm.convolution.Convolution
import net.imglib2.algorithm.convolution.fast_gauss.FastGauss
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.img.array.ArrayImgs
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.zeroMin
import kotlin.collections.toLongArray

object MorphOperations {

	internal data class VoronoiDistanceTransformImgs(
		val offsetVoronoiLabels: RandomAccessibleInterval<UnsignedLongType>,
		val offsetDistances: RandomAccessibleInterval<DoubleType>,
	)

	internal data class GaussianSmoothingMask(
		val img: RandomAccessibleInterval<DoubleType>
	)

	internal fun paddedCellVoronoiDistanceTransform(
		cellDimensions: IntArray,
		kernelSizePx: IntArray,
		labelsImg: RandomAccessibleInterval<UnsignedLongType>,
		invert: Boolean,
		labels: LongArray,
		sqWeights: DoubleArray,
	): VoronoiDistanceTransformImgs {

		val options = DiskCachedCellImgOptions.options()
			.accessFlags(setOf(AccessFlags.VOLATILE))
			.cellDimensions(*cellDimensions)

		val padding = kernelSizePx.map { it.toLong() }.toLongArray()
		val coreInterval = Intervals.createMinSize(*padding, *cellDimensions.map { it.toLong() }.toLongArray())

		val extendedLabelsImg = Views.extendValue(labelsImg, Label.INVALID)

		val distances = DiskCachedCellImgFactory(DoubleType(), options).create(labelsImg)
		val voronoiLabels = DiskCachedCellImgFactory(UnsignedLongType(Label.INVALID), options).create(labelsImg) { cell ->

			val paddedInterval = cell.extendBy(*padding)
			val paddedDimensions = LongArray(paddedInterval.numDimensions()) { paddedInterval.dimension(it) }
			val paddedLabelsImg = ArrayImgs.unsignedLongs(*paddedDimensions)
			val paddedDistancesImg = ArrayImgs.doubles(*paddedDimensions)

			val initLabels = extendedLabelsImg.interval(paddedInterval).cursor()
			val paddedLabels = paddedLabelsImg.cursor()
			val paddedDistances = paddedDistancesImg.cursor()
			while (initLabels.hasNext()) {
				val initLabel = initLabels.next().get()
				paddedLabels.next().set(initLabel)

				val distance = paddedDistances.next()
				if (invert xor (initLabel in labels))
					distance.set(Double.MAX_VALUE)
			}

			DistanceTransform.voronoiDistanceTransform(paddedLabelsImg, paddedDistancesImg, *sqWeights)

			paddedLabelsImg.interval(coreInterval).cursor()

			val paddedCellLabelsCursor = paddedLabelsImg.interval(coreInterval).cursor()
			val paddedCellDistancesCursor = paddedDistancesImg.interval(coreInterval).cursor()
			val cellCursor = cell.cursor()
			val distancesCursor = distances.interval(cell).cursor()
			while (cellCursor.hasNext()) {
				cellCursor.next().set(paddedCellLabelsCursor.next())
				distancesCursor.next().set(paddedCellDistancesCursor.next())
			}
		}
		return VoronoiDistanceTransformImgs(voronoiLabels, distances)
	}

	internal fun paddedCellGaussianSmoothing(
		cellDimensions: IntArray,
		kernelSizePx: IntArray,
		labelsMask: RandomAccessibleInterval<DoubleType>,
		sigma: DoubleArray,
	): GaussianSmoothingMask {

		val options = DiskCachedCellImgOptions.options()
			.accessFlags(setOf(AccessFlags.VOLATILE))
			.cellDimensions(*cellDimensions)

		val padding = kernelSizePx.map { it.toLong() }.toLongArray()
		val coreInterval = Intervals.createMinSize(*padding, *cellDimensions.map { it.toLong() }.toLongArray())

		val extendedLabelsImg = Views.extendValue(labelsMask, 0.0)
		val normalizeFastGauss: Convolution<RealType<*>> = FastGauss.convolution(sigma)

		val gaussianImg = DiskCachedCellImgFactory(DoubleType(), options).create(labelsMask) { cell ->
			val paddedInterval = cell.extendBy(*padding)
			val paddedDimensions = LongArray(paddedInterval.numDimensions()) { paddedInterval.dimension(it) }
			val paddedGaussianImg = ArrayImgs.doubles(*paddedDimensions)

			val paddedLabelsMask = extendedLabelsImg.interval(paddedInterval)

			normalizeFastGauss.process(paddedLabelsMask.zeroMin(), paddedGaussianImg)

			val gaussianResult = cell.cursor()
			val paddedGaussian = paddedGaussianImg.interval(coreInterval).cursor()
			while (gaussianResult.hasNext()) {
				gaussianResult.next().set(paddedGaussian.next())
			}
		}
		return GaussianSmoothingMask(gaussianImg)
	}
}