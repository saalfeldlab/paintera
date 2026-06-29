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
 * @param cell the core cell (writes here go to the actual cell storage)
 * @param paddedInterval the full padded interval (must contain cellInterval)
 * @param type the type of the PaddedCell
 */
class PaddedCell<T : NativeType<T>>(
    private val cell: RandomAccessibleInterval<T>,
    private val paddedInterval: Interval,
    type: T,
) : AbstractInterval(paddedInterval), RandomAccessibleInterval<T> {

    /* per dimension padding size before and after cell interval */
    private val paddingBefore = LongArray(numDimensions()) { d -> cell.min(d) - paddedInterval.min(d) }
    private val paddingAfter = LongArray(numDimensions()) { d -> paddedInterval.max(d) - cell.max(d) }

    /* cell bounds cached for the random-access fast path */
    private val cellMin = LongArray(numDimensions()) { cell.min(it) }
    private val cellMax = LongArray(numDimensions()) { cell.max(it) }

    /* per-dimension index into borderSlabs for the before/after padding slab, or -1 if absent */
    private val slabBeforeIndex = IntArray(numDimensions()) { -1 }
    private val slabAfterIndex = IntArray(numDimensions()) { -1 }

    /* allocate min/max bounds once for each borderSlab for the random-access fast path */
    private val slabBoundsMin = mutableListOf<LongArray>()
    private val slabBoundsMax = mutableListOf<LongArray>()

    private val factory = ArrayImgFactory(type)

    /**
     * For each dimension, we create up to 2 slabs:
     * - "before" slab: covers [paddedMin, cellMin-1]
     * - "after" slab: covers [cellMax+1, paddedMax]
     *
     * Each slab spans the full padded extent in dimensions < d, and the cell extent in dimensions > d.
     * This ensures slabs don't overlap.
     *
     * Note: Slabs are allocated but not initialized - callers should use fillPaddingFrom() or
     * fillAllFrom() to initialize the values from a source.
     */
    private val borderSlabs: List<RandomAccessibleInterval<T>> = buildList {
        for (d in 0 until numDimensions()) {

            /* build the before slab for this dimension */
            if (paddingBefore[d] > 0) {
                val slab = newSlab(d, paddedInterval.min(d), cell.min(d) - 1)
                slabBeforeIndex[d] = size
                slabBoundsMin.add(slab.minAsLongArray());
                slabBoundsMax.add(slab.maxAsLongArray());
                add(slab)
            }

            /* build the after slab for this dimension */
            if (paddingAfter[d] > 0) {
                val slab = newSlab(d, cell.max(d) + 1, paddedInterval.max(d))
                slabAfterIndex[d] = size
                slabBoundsMin.add(slab.minAsLongArray());
                slabBoundsMax.add(slab.maxAsLongArray());
                add(slab)
            }
        }
    }

    init {
        require(Intervals.contains(paddedInterval, cell)) {
            "Padded interval must contain the cell interval"
        }
    }

    /**
     * Creates a new slab along dimension [d] with the same shape as the cell in
     * non [d] dimenesions. Slab covers the volume between [min, max], directly adjacent
     * to the cell. Resulting slab has "depth" [max - min + 1] along the [d] dimension.
     *
     * @param d to extend a slab along
     * @param min position of the slab in dimension [d]
     * @param max position of the slab in dimension [d]
     * @return the array img of the slab
     */
    private fun newSlab(d: Int, min: Long, max: Long): RandomAccessibleInterval<T> {
        val min = LongArray(numDimensions()) { dim ->
            when {
                dim < d -> paddedInterval.min(dim)  // Full padded extent for earlier dims
                dim == d -> min                         // Padding region in this dim
                else -> cell.min(dim)               // Cell extent for later dims
            }
        }
        val max = LongArray(numDimensions()) { dim ->
            when {
                dim < d -> paddedInterval.max(dim)
                dim == d -> max                         // Up to cell (exclusive)
                else -> cell.max(dim)
            }
        }
        val slabDims = LongArray(numDimensions()) { dim -> max[dim] - min[dim] + 1 }
        return factory
            .create(*slabDims)
            .translate(*min)
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

        /* fill the cell region */
        val cellSourceView = extendedSource.interval(cell)
        val cellCursor = Views.flatIterable(cell).cursor()
        val cellSourceCursor = Views.flatIterable(cellSourceView).cursor()
        while (cellCursor.hasNext()) {
            cellCursor.next().set(cellSourceCursor.next())
        }

        /* Fill the padding slabs */
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

        /** the backing access that is valid for the current position */
        private var currentAccess: RandomAccess<T> = cellAccess

        /** min valid position for the current access. useful for bounds checking optimizations */
        private val curMin = LongArray(n)
        /** max valid position for the current access. useful for bounds checking optimizations */
        private val curMax = LongArray(n)

        init {
            for (d in 0 until n) {
                position[d] = paddedInterval.min(d)
            }
            updateCurrentAccess()
        }

        /**
         * Resolve which region the current position falls in. A padding position belongs to the
         * slab of its highest out-of-cell dimension; slabs span the full padded extent in lower
         * dimensions and only the cell extent in higher ones, so each padding position lands in
         * exactly one slab and this resolves it without scanning the slab list. Returns -1 for the
         * cell.
         *  ┌────────────────────┐
         *  │         0          │ 0 = top band; full PADDED width
         *  │───┬────────────┬───│
         *  │   │            │   │ 1 = left strip
         *  │ 1 │     -1     │ 2 │ 2 = right strip
         *  │   │            │   │ only the CELL's row range
         *  │───┴────────────┴───│
         *  │         3          │ 3 = bottom band; full PADDED width
         *  └────────────────────┘
         */
        private fun getRegionIndex(): Int {
            for (d in n - 1 downTo 0) {
                val pos = position[d]
                if (pos < cellMin[d]) return slabBeforeIndex[d]
                if (pos > cellMax[d]) return slabAfterIndex[d]
            }
            return -1
        }

        /* update active access for the current position, cache its bounds, and seed it to the current position */
        private fun updateCurrentAccess() {
            currentAccessIndex = getRegionIndex()
            if (currentAccessIndex < 0) {
                currentAccess = cellAccess
                cellMin.copyInto(curMin)
                cellMax.copyInto(curMax)
            } else {
                currentAccess = slabAccesses[currentAccessIndex]
                slabBoundsMin[currentAccessIndex].copyInto(curMin)
                slabBoundsMax[currentAccessIndex].copyInto(curMax)
            }
            currentAccess.setPosition(this)
        }

        override fun get(): T = currentAccess.get()

        override fun fwd(d: Int) {
            position[d]++
            if (position[d] <= curMax[d])
                currentAccess.fwd(d)
            else
                updateCurrentAccess()
        }

        override fun bck(d: Int) {
            position[d]--
            if (position[d] >= curMin[d])
                currentAccess.bck(d)
            else
                updateCurrentAccess()
        }

        override fun move(distance: Int, d: Int) {
            position[d] += distance
            val pos = position[d]
            if (pos in curMin[d]..curMax[d])
                currentAccess.move(distance, d)
            else
                updateCurrentAccess()
        }

        override fun move(distance: Long, d: Int) {
            position[d] += distance
            val pos = position[d]
            if (pos in curMin[d]..curMax[d])
                currentAccess.move(distance, d)
            else
                updateCurrentAccess()
        }

        override fun move(distance: Localizable) {
            for (d in 0 until n)
                position[d] += distance.getLongPosition(d)
            if (getRegionIndex() == currentAccessIndex)
                currentAccess.move(distance)
            else
                updateCurrentAccess()
        }

        override fun move(distance: IntArray) {
            for (d in 0 until n)
                position[d] += distance[d]
            if (getRegionIndex() == currentAccessIndex)
                currentAccess.move(distance)
            else
                updateCurrentAccess()
        }

        override fun move(distance: LongArray) {
            for (d in 0 until n)
                position[d] += distance[d]
            if (getRegionIndex() == currentAccessIndex)
                currentAccess.move(distance)
            else
                updateCurrentAccess()
        }

        override fun setPosition(pos: Localizable) {
            for (d in 0 until n)
                position[d] = pos.getLongPosition(d)
            updateCurrentAccess()
        }

        override fun setPosition(pos: IntArray) {
            for (d in 0 until n)
                position[d] = pos[d].toLong()
            updateCurrentAccess()
        }

        override fun setPosition(pos: LongArray) {
            for (d in 0 until n)
                position[d] = pos[d]
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