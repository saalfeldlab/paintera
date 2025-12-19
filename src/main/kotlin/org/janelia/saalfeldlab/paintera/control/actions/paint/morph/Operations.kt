package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import net.imglib2.*
import net.imglib2.algorithm.convolution.Convolution
import net.imglib2.algorithm.convolution.fast_gauss.FastGauss
import net.imglib2.algorithm.morphology.distance.DistanceTransform
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.img.array.ArrayImgFactory
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.type.NativeType
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.interval
import org.janelia.saalfeldlab.util.translate
import org.janelia.saalfeldlab.util.zeroMin

object MorphOperations {

    data class VoronoiDistanceTransformImgs(
        val paddedVoronoiLabels: RandomAccessibleInterval<UnsignedLongType>,
        val paddedDistances: RandomAccessibleInterval<DoubleType>,
    )

    data class GaussianSmoothingMask(
        val img: RandomAccessibleInterval<DoubleType>,
    )

    fun paddedCellVoronoiDistanceTransform(
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

        val zeroMinLabelsImg = labelsImg.zeroMin()

        val extendedLabelsImg = Views.extendValue(zeroMinLabelsImg, Label.INVALID)

        val distances = DiskCachedCellImgFactory(DoubleType(), options).create(zeroMinLabelsImg)
        val voronoiLabels =
            DiskCachedCellImgFactory(UnsignedLongType(Label.INVALID), options).create(zeroMinLabelsImg) { cell ->
                val validCellInterval = cell intersect zeroMinLabelsImg
                val paddedInterval = cell.extendBy(*padding)
                val validPaddedInterval = paddedInterval intersect zeroMinLabelsImg

                // Create padded cells for both labels and distances using the valid (clipped) intervals
                val validCell = cell.interval(validCellInterval)

                val paddedLabelsImg = PaddedCell(
                    validCell,
                    validPaddedInterval,
                    UnsignedLongType(Label.INVALID)
                )
                paddedLabelsImg.fillAllFrom(extendedLabelsImg)

                // Distances cell - writes go directly to the distances output
                val distancesCell = distances.interval(validCellInterval)
                val paddedDistancesCell = PaddedCell(
                    distancesCell,
                    validPaddedInterval,
                    DoubleType(0.0)
                )

                // Initialize distances based on labels
                // For positions in the padded region, we need to read from the padded labels
                val labelsCursor = Views.flatIterable(paddedLabelsImg).cursor()
                val distancesCursor = Views.flatIterable(paddedDistancesCell).cursor()
                while (labelsCursor.hasNext()) {
                    val label = labelsCursor.next().get()
                    val distance = distancesCursor.next()
                    if (invert xor (label in labels)) {
                        distance.set(Double.MAX_VALUE)
                    }
                }

                DistanceTransform.voronoiDistanceTransform(paddedLabelsImg, paddedDistancesCell, *sqWeights)
            }
        val translation = labelsImg.minAsLongArray()
        val alignedVoronoiLabels = voronoiLabels.translate(*translation)
        return VoronoiDistanceTransformImgs(alignedVoronoiLabels, distances.translate(*translation))
    }

    fun paddedCellGaussianSmoothing(
        cellDimensions: IntArray,
        kernelSizePx: IntArray,
        labelsMask: RandomAccessibleInterval<DoubleType>,
        sigma: DoubleArray,
    ): GaussianSmoothingMask {

        val options = DiskCachedCellImgOptions.options()
            .accessFlags(setOf(AccessFlags.VOLATILE))
            .cellDimensions(*cellDimensions)

        val extendedLabelsMask = Views.extendValue(labelsMask, 0.0).interval(labelsMask)

        val gaussianImg = DiskCachedCellImgFactory(DoubleType(), options)
            .create(labelsMask) { cell ->
                paddedGaussianSmoothing(extendedLabelsMask, cell, kernelSizePx, sigma)
            }
        return GaussianSmoothingMask(gaussianImg)
    }

    /**
     * Fill the output [RandomAccessibleInterval] with the result of
     * applying a Gaussian convolution to the input. The Interval of the output
     * is padded with valid input from the input image, based on the
     * kernel size. This results in the output being valid without issues
     * along the edges. It does rquire the that [input] image is valid
     * outside the [output] interval up the padding required for the given kernel size.
     *
     * @param input image with data to apply gaussian smoothing to
     * @param output image with the valid results of a gaussian smoothing operation
     * @param kernelSizePx padding to add outside the [output] Interval in each direction
     * @param sigma for the gaussian kernel
     * @return the same image provided as the output, after the gaussian smoothing
     */
    fun paddedGaussianSmoothing(
        input: RandomAccessibleInterval<DoubleType>,
        output: RandomAccessibleInterval<DoubleType>,
        kernelSizePx: IntArray,
        sigma: DoubleArray,
    ): RandomAccessibleInterval<DoubleType> {

        val padding = kernelSizePx.map { it.toLong() }.toLongArray()

        val paddedOutputInterval = output.extendBy(*padding)
        val validPaddedInterval = paddedOutputInterval intersect input

        val paddedOutput = PaddedCell(output, validPaddedInterval, DoubleType(0.0))

        val convolution: Convolution<RealType<*>> = FastGauss.convolution(sigma)
        convolution.process(input, paddedOutput)

        return output
    }
}

/**
 * A RandomAccessibleInterval that composites a core cell with a padding buffer.
 *
 * The core region (cellInterval) is backed by the actual cell data - writes go directly
 * to the underlying cell with no copying required. The padding region is backed by
 * separately allocated buffers that only cover the border slabs (not the full padded volume).
 *
 * For a 3D cell with padding, this allocates 6 slabs (2 per dimension) rather than the
 * full padded volume, significantly reducing memory usage when padding is small relative
 * to cell size.
 *
 * This is useful for algorithms that need to process with padding (like convolutions
 * or distance transforms) but want to avoid:
 * 1. Allocating a full padded buffer for every cell
 * 2. Copying the cell data back after processing
 *
 * @param T the pixel type
 * @param cell the core cell (writes here go to the actual cell storage)
 * @param paddedInterval the full padded interval (must contain cellInterval)
 * @param type the type of the PaddedCell
 */
class PaddedCell<T : NativeType<T>>(
    private val cell: RandomAccessibleInterval<T>,
    private val paddedInterval: Interval,
    type: T,
) : AbstractInterval(paddedInterval), RandomAccessibleInterval<T> {

    // Calculate padding amounts
    private val paddingBefore = LongArray(numDimensions()) { d -> cell.min(d) - paddedInterval.min(d) }
    private val paddingAfter = LongArray(numDimensions()) { d -> paddedInterval.max(d) - cell.max(d) }

    /**
     * Border slabs - only allocate memory for the actual padding regions.
     *
     * For each dimension d, we create up to 2 slabs:
     * - "before" slab: covers [paddedMin, cellMin-1] in dimension d
     * - "after" slab: covers [cellMax+1, paddedMax] in dimension d
     *
     * Each slab spans the full padded extent in dimensions < d, and the cell extent in dimensions > d.
     * This ensures slabs don't overlap.
     *
     * Note: Slabs are allocated but not initialized - callers should use fillPaddingFrom() or
     * fillAllFrom() to initialize the values from a source.
     */
    private val borderSlabs: List<RandomAccessibleInterval<T>> = buildList {
        val factory = ArrayImgFactory(type)

        for (d in 0 until numDimensions()) {
            // "Before" slab in dimension d
            if (paddingBefore[d] > 0) {
                val slabMin = LongArray(numDimensions()) { dim ->
                    when {
                        dim < d -> paddedInterval.min(dim)  // Full padded extent for earlier dims
                        dim == d -> paddedInterval.min(d)   // Padding region in this dim
                        else -> cell.min(dim)       // Cell extent for later dims
                    }
                }
                val slabMax = LongArray(numDimensions()) { dim ->
                    when {
                        dim < d -> paddedInterval.max(dim)
                        dim == d -> cell.min(d) - 1  // Up to (but not including) cell
                        else -> cell.max(dim)
                    }
                }
                val slabDims = LongArray(numDimensions()) { dim -> slabMax[dim] - slabMin[dim] + 1 }
                val slab = factory.create(*slabDims)
                add(Views.translate(slab, *slabMin))
            }

            // "After" slab in dimension d
            if (paddingAfter[d] > 0) {
                val slabMin = LongArray(numDimensions()) { dim ->
                    when {
                        dim < d -> paddedInterval.min(dim)
                        dim == d -> cell.max(d) + 1  // Start after cell
                        else -> cell.min(dim)
                    }
                }
                val slabMax = LongArray(numDimensions()) { dim ->
                    when {
                        dim < d -> paddedInterval.max(dim)
                        dim == d -> paddedInterval.max(d)
                        else -> cell.max(dim)
                    }
                }
                val slabDims = LongArray(numDimensions()) { dim -> slabMax[dim] - slabMin[dim] + 1 }
                val slab = factory.create(*slabDims)
                add(Views.translate(slab, *slabMin))
            }
        }
    }

    init {
        require(Intervals.contains(paddedInterval, cell)) {
            "Padded interval must contain the cell interval"
        }
    }

    /**
     * Check if a position is within the core cell interval.
     */
    private fun isInCell(position: Localizable): Boolean {
        for (d in 0 until numDimensions()) {
            val pos = position.getLongPosition(d)
            if (pos < cell.min(d) || pos > cell.max(d)) {
                return false
            }
        }
        return true
    }

    override fun randomAccess(): RandomAccess<T> = PaddedCellRandomAccess()

    override fun randomAccess(interval: Interval): RandomAccess<T> = randomAccess()

    /**
     * Fill the padding region from an extended source.
     * This copies data from the source into the border slabs.
     *
     * @param extendedSource a RandomAccessible that provides values for positions outside the cell
     */
    fun fillPaddingFrom(extendedSource: RandomAccessible<T>) {
        for (slab in borderSlabs) {
            val sourceView = extendedSource.interval(slab)
            val slabCursor = Views.flatIterable(slab).cursor()
            val sourceCursor = Views.flatIterable(sourceView).cursor()

            while (slabCursor.hasNext()) {
                slabCursor.next().set(sourceCursor.next())
            }
        }
    }

    /**
     * Fill the entire padded region (both cell and padding) from an extended source.
     * This copies data from the source into both the cell and the border slabs.
     *
     * @param extendedSource a RandomAccessible that provides values for all positions
     */
    fun fillAllFrom(extendedSource: RandomAccessible<T>) {
        // Fill the cell region
        val cellSourceView = extendedSource.interval(cell)
        val cellCursor = Views.flatIterable(cell).cursor()
        val cellSourceCursor = Views.flatIterable(cellSourceView).cursor()
        while (cellCursor.hasNext()) {
            cellCursor.next().set(cellSourceCursor.next())
        }

        // Fill the padding slabs
        fillPaddingFrom(extendedSource)
    }

    /**
     * RandomAccess that delegates to the cell for core positions and the appropriate
     * border slab for padding positions.
     */
    private inner class PaddedCellRandomAccess : AbstractLocalizable(numDimensions()), RandomAccess<T> {

        private val cellAccess: RandomAccess<T> = cell.randomAccess()
        private val slabAccesses: List<RandomAccess<T>> = borderSlabs.map { it.randomAccess() }
        private var currentAccessIndex: Int = -1  // -1 means cell, >= 0 means slab index

        init {
            // Initialize position to the min of the padded interval
            for (d in 0 until n) {
                position[d] = paddedInterval.min(d)
            }
            updateCurrentAccess()
        }

        private fun updateCurrentAccess() {
            currentAccessIndex = if (isInCell(this)) {
                -1
            } else {
                // Find the slab containing this position
                borderSlabs.indexOfFirst { Intervals.contains(it, this) }
            }
        }

        private fun currentAccess(): RandomAccess<T> =
            if (currentAccessIndex < 0) cellAccess else slabAccesses[currentAccessIndex]

        override fun get(): T {
            val access = currentAccess()
            access.setPosition(this)
            return access.get()
        }

        override fun fwd(d: Int) {
            position[d] = position[d] + 1
            updateCurrentAccess()
        }

        override fun bck(d: Int) {
            position[d] = position[d] - 1
            updateCurrentAccess()
        }

        override fun move(distance: Int, d: Int) {
            position[d] = position[d] + distance
            updateCurrentAccess()
        }

        override fun move(distance: Long, d: Int) {
            position[d] = position[d] + distance
            updateCurrentAccess()
        }

        override fun move(distance: Localizable) {
            for (d in 0 until n) {
                position[d] = position[d] + distance.getLongPosition(d)
            }
            updateCurrentAccess()
        }

        override fun move(distance: IntArray) {
            for (d in 0 until n) {
                position[d] = position[d] + distance[d]
            }
            updateCurrentAccess()
        }

        override fun move(distance: LongArray) {
            for (d in 0 until n) {
                position[d] = position[d] + distance[d]
            }
            updateCurrentAccess()
        }

        override fun setPosition(pos: Localizable) {
            for (d in 0 until n) {
                position[d] = pos.getLongPosition(d)
            }
            updateCurrentAccess()
        }

        override fun setPosition(pos: IntArray) {
            for (d in 0 until n) {
                position[d] = pos[d].toLong()
            }
            updateCurrentAccess()
        }

        override fun setPosition(pos: LongArray) {
            for (d in 0 until n) {
                position[d] = pos[d]
            }
            updateCurrentAccess()
        }

        override fun setPosition(pos: Int, d: Int) {
            position[d] = pos.toLong()
            updateCurrentAccess()
        }

        override fun setPosition(pos: Long, d: Int) {
            position[d] = pos
            updateCurrentAccess()
        }

        override fun copy(): PaddedCellRandomAccess {
            val copy = PaddedCellRandomAccess()
            copy.setPosition(this)
            return copy
        }
    }

    companion object {
        /**
         * Create a PaddedCellImg from a cell with symmetric padding.
         *
         * @param cell the core cell
         * @param padding the padding amount in each dimension
         * @param type of the padded cell
         * @return a PaddedCellImg wrapping the cell
         */
        fun <T : NativeType<T>> create(
            cell: RandomAccessibleInterval<T>,
            padding: LongArray,
            type: T,
        ): PaddedCell<T> {
            val paddedInterval = cell.extendBy(*padding)
            return PaddedCell(cell, paddedInterval, type)
        }

        /**
         * Create a PaddedCellImg and fill both the cell and padding from an extended source.
         *
         * @param cell the core cell
         * @param padding the padding amount in each dimension
         * @param extendedSource a RandomAccessible providing values for all positions (e.g., Views.extendValue(source, defaultVal))
         * @param type of the padded cell
         * @return a PaddedCellImg with both cell and padding filled from the source
         */
        fun <T : NativeType<T>> createAndFill(
            cell: RandomAccessibleInterval<T>,
            padding: LongArray,
            extendedSource: RandomAccessible<T>,
            type: T,
        ): PaddedCell<T> {
            val padded = create(cell, padding, type)
            padded.fillAllFrom(extendedSource)
            return padded
        }
    }
}