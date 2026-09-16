
package org.janelia.saalfeldlab.util.n5

import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.RealType
import net.imglib2.view.Views
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.util.addDimension
import org.janelia.saalfeldlab.util.hyperSlice
import org.janelia.saalfeldlab.util.moveAxis
import java.util.Locale

/**
 * Maps an nD source onto a canonical 3D (x, y, z) view, and back. Rendering and Annotating in Paintera is inherently 3D,
 * so a source with more than three dimensions should be sliced (every non-spatial axis slice at a constant position)
 * and one with fewer than three spatial dimensions must be embedded (a singleton axis added at the missing canonical slot)
 *
 * Spatial axes are addressed by canonical slot: [xyzSourceAxes]`[0]`, `[1]`, `[2]` give the source axis that supplies
 * x, y, z respectively, or `-1` when that dimension is absent and must be synthesized. So `[0, -1, 2]` means x comes
 * from source axis 0, z from source axis 2, and y is an inserted singleton - the result is still canonical `[x, y, z]`.
 *
 * The read direction ([toXyz]) is pure view composition - `hyperSlice` to slice non-spatial axes, `moveAxis` to order the
 * spatial axes, `addDimension` to insert a missing one. No copy; the view writes through to the backing image and stays
 * lazy. The write direction ([toSourceView] / [toSourcePosition] / ...) reverses that, so a 3D edit can be committed
 * back into the right slab of the nD dataset.
 *
 * @param numDimensions dimensionality of the backing source
 * @param xyzSourceAxes source axis supplying each canonical dimension x, y, z (size 3); `-1` for an absent dimension
 * @param slicePositions position to slice each non-spatial axis at; entries for spatial axes are ignored
 */
class SpatialMapping(
    val numDimensions: Int,
    xyzSourceAxes: IntArray,
    slicePositions: LongArray
) {
    val xyzSourceAxes = xyzSourceAxes.copyOf()
    val slicePositions = slicePositions.copyOf()

    init {
        require(xyzSourceAxes.size == 3) { "xyzSourceAxes should have 3 dimensions, got ${xyzSourceAxes.size}" }
        require(slicePositions.size == numDimensions) { "slicePositions must cover all $numDimensions dimensions, got ${slicePositions.size}" }

        val hasSpatialAxis = xyzSourceAxes.filter { it >= 0 }
        require(hasSpatialAxis.isNotEmpty()) { "must have at least one spatial axis" }
        require(hasSpatialAxis.all { it < numDimensions } && hasSpatialAxis.distinct().size == hasSpatialAxis.size) { "invalid spatial axes ${xyzSourceAxes.toList()} for $numDimensions dimensions" }
    }

    /**
     * Source Axes that are actually represented in the source dataset. Fewer than 3D spatial dimensions
     * will be added with singleton dimensions, and represented here with `-1`.
     */
    private val actualSourceAxes = xyzSourceAxes.filter { it >= 0 }.toHashSet()

    /** True when [toXyz] is the identity (already canonical 3D, x/y/z = 0/1/2); the source is not reduced/permuted/embedded. */
    val isIdentity: Boolean
        get() = numDimensions == 3 && xyzSourceAxes.contentEquals(intArrayOf(0, 1, 2))

    /** Derive the canonical 3D (x, y, z) view of [source]: slice non-spatial axes, order the spatial axes, add missing singleton dimensions. */
    fun <T> toXyz(source: RandomAccessibleInterval<T>): RandomAccessibleInterval<T> {
        if (isIdentity)
            return source
        return toXyz(source, -1)
    }

    /**
     * The canonical 3D (x, y, z) view of [source] with the non-spatial [axis] collapsed into a composite: component `c`
     * of a pixel is the source at position `c` along [axis]
     */
    fun <T : RealType<T>> collapse(source: RandomAccessibleInterval<T>, axis: Int): RandomAccessibleInterval<RealComposite<T>> {
        require(axis in 0 until numDimensions && axis !in actualSourceAxes) { "axis $axis is not a non-spatial axis" }
        return Views.collapseReal(toXyz(source, axis))
    }

    /* the xyz view; [additionalAxis] is not sliced and ends up as the fourth dimension */
    private fun <T> toXyz(source: RandomAccessibleInterval<T>, additionalAxis: Int): RandomAccessibleInterval<T> {
        var view = source
        /* labels[i] = source axis currently at view position i (-1 marks a synthesized singleton) */
        val labels = (0 until numDimensions).toMutableList()
        /* slice every non-spatial axis; slice the highest current position first so lower positions stay put */
        for (position in labels.reversed()) {
            if (position in actualSourceAxes || position == additionalAxis)
                continue

            view = view.hyperSlice(position, slicePositions[position])
            labels.removeAt(position)
        }
        /* place each canonical slot: move the actual source axis there, or insert a singleton */
        for (slot in 0..2) {
            val sourceAxis = xyzSourceAxes[slot]
            if (sourceAxis >= 0) {
                val current = labels.indexOf(sourceAxis)
                if (current != slot) {
                    view = view.moveAxis(current, slot)
                    labels.add(slot, labels.removeAt(current))
                }
            } else {
                view = view.addDimension(0, 0)
                val last = view.numDimensions() - 1
                view = view.moveAxis(last, slot)
                labels.add(slot, -1)
            }
        }
        return view
    }

    /** mapping a canonical 3D (x, y, z) position back to the full nD source position. */
    fun toSourcePosition(x: Long, y: Long, z: Long): LongArray {
        val position = slicePositions.copyOf()
        val canonical = longArrayOf(x, y, z)
        for (slot in 0..2) if (xyzSourceAxes[slot] >= 0) position[xyzSourceAxes[slot]] = canonical[slot]
        return position
    }

    /** Project an nD source position onto the canonical 3D (x, y, z) position; an absent dimension is 0. */
    fun toXyzPosition(sourcePosition: LongArray): LongArray {
        requireSourceShape(sourcePosition.size)

        return LongArray(3) { if (xyzSourceAxes[it] >= 0) sourcePosition[xyzSourceAxes[it]] else 0L }
    }

    /** Map a canonical 3D interval back to the full nD source interval at the slice positions. */
    fun toSourceInterval(xyzInterval: Interval): Interval {

        require(xyzInterval.numDimensions() == 3) { "xyzInterval must have 3 dimensions, got ${xyzInterval.numDimensions()}" }

        val min = slicePositions.copyOf()
        val max = slicePositions.copyOf()
        for (slot in 0..2) if (xyzSourceAxes[slot] >= 0) {
            min[xyzSourceAxes[slot]] = xyzInterval.min(slot)
            max[xyzSourceAxes[slot]] = xyzInterval.max(slot)
        }
        return FinalInterval(min, max)
    }

    /**
     * Project an nD source interval onto the canonical 3D (x, y, z) interval: drop the non-spatial axes, order the
     * spatial ones, and give an absent spatial dimension the singleton `[0, 0]`.
     *
     * The inverse of [toSourceInterval] for any interval whose non-spatial axes sit at [slicePositions].
     */
    fun toXyzInterval(sourceInterval: Interval): Interval {

        require(sourceInterval.numDimensions() == numDimensions) { "sourceInterval must have $numDimensions dimensions, got ${sourceInterval.numDimensions()}" }

        val min = LongArray(3)
        val max = LongArray(3)
        for (slot in 0..2) {
            val axis = xyzSourceAxes[slot]
            if (axis < 0)
                continue
            min[slot] = sourceInterval.min(axis)
            max[slot] = sourceInterval.max(axis)
        }
        return FinalInterval(min, max)
    }

    /**
     * convert an nD [sourceInterval] to a 3D canonical XYZ interval based on current [slicePositions]
     *
     * @param sourceInterval an interval over all [numDimensions] source dimensions
     * @return the canonical 3D interval, or null when [sourceInterval] lies outside the sliced positions
     */
    fun toXyzIntervalOrNull(sourceInterval: Interval): Interval? {

        for (axis in 0 until numDimensions) {
            if (axis in actualSourceAxes)
                continue
            if (slicePositions[axis] < sourceInterval.min(axis) || slicePositions[axis] > sourceInterval.max(axis))
                return null
        }
        return toXyzInterval(sourceInterval)
    }

    /**
     * convert nD source [blocks] to 3D canonical XYZ intervals based on current [slicePositions]
     *
     * @param blocks intervals over all [numDimensions] source dimensions; a 3D interval is already canonical and kept as is
     * @return the canonical 3D intervals, without the blocks that lie outside the sliced positions
     */
    fun toXyzBlocks(blocks: Iterable<Interval>): List<Interval> = blocks.mapNotNull { block ->
        when {
            isIdentity -> block
            block.numDimensions() != numDimensions -> block
            else -> toXyzIntervalOrNull(block)
        }
    }

    /** This mapping with [axis] sliced at [position] instead */
    fun withSlicePosition(axis: Int, position: Long): SpatialMapping {
        require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

        return SpatialMapping(numDimensions, xyzSourceAxes, slicePositions.copyOf().also { it[axis] = position })
    }

    /**
     * convert this mapping from voxel to block coordinates over [grid]
     *
     * @param grid a cell grid over all [numDimensions] source dimensions
     * @return the mapping whose slice positions are the [grid] blocks containing [slicePositions]
     */
    fun toBlockMapping(grid: CellGrid): SpatialMapping {
        require(grid.numDimensions() == numDimensions) { "grid must have $numDimensions dimensions, got ${grid.numDimensions()}" }

        return SpatialMapping(numDimensions, xyzSourceAxes, LongArray(numDimensions) { slicePositions[it] / grid.cellDimension(it) })
    }

    /** Project an nD shape array (block size, dimensions, ...) to an [x,y,z] shape array using the
     * this [SpatialMapping]. drops non-spatial dimensions, reorders to [x,y,z], adds single-position dimension if < 3 spatial dims */
    fun spatialProjection(shape: IntArray): IntArray {
        requireSourceShape(shape.size)

        return IntArray(3) { if (xyzSourceAxes[it] >= 0) shape[xyzSourceAxes[it]] else 1 }
    }

    fun spatialProjection(shape: LongArray): LongArray {
        requireSourceShape(shape.size)

        return LongArray(3) { if (xyzSourceAxes[it] >= 0) shape[xyzSourceAxes[it]] else 1L }
    }

    private fun requireSourceShape(size: Int) =
        require(size == numDimensions) { "shape must cover all $numDimensions source dimensions, got $size" }

    /**
     * Map a canonical 3D (x, y, z) view back to the full nD source view: drop the embedded singletons for absent
     * dimensions, send the present spatial axes to their source positions, and make every non-spatial axis a singleton
     * at its slice position. The inverse of [toXyz]
     */
    fun <T> toSourceView(slice3D: RandomAccessibleInterval<T>): RandomAccessibleInterval<T> {
        if (isIdentity) return slice3D
        var view = slice3D
        /* labels[i] = source axis at view position i; -1 marks a slot that was synthesized for an absent dimension */
        val labels = xyzSourceAxes.toMutableList()
        /* drop the synthesized singletons (highest position first) so only the present spatial axes remain */
        while (labels.contains(-1)) {
            val position = labels.lastIndexOf(-1)
            view = view.hyperSlice(position, view.min(position))
            labels.removeAt(position)
        }
        /* append a singleton dimension for each non-spatial axis, slice at its position */
        for (axis in (0 until numDimensions).filter { it !in actualSourceAxes }) {
            view = view.addDimension(slicePositions[axis], slicePositions[axis])
            labels.add(axis)
        }
        /* move each axis to its source index */
        for (target in 0 until numDimensions) {
            val current = labels.indexOf(target)
            if (current != target) {
                view = view.moveAxis(current, target)
                labels.add(target, labels.removeAt(current))
            }
        }
        return view
    }

    /** Widen a 3D block size to the nD source block size, putting 1 at every non-spatial (and embedded) axis. */
    fun toSourceBlockSize(blockSize3D: IntArray): IntArray {
        require(blockSize3D.size == 3) { "blockSize3D must have 3 dimensions, got ${blockSize3D.size}" }

        val blockSize = IntArray(numDimensions) { 1 }
        for (slot in 0..2) if (xyzSourceAxes[slot] >= 0) blockSize[xyzSourceAxes[slot]] = blockSize3D[slot]
        return blockSize
    }

    /* canonical slot -> slot among the spatial source axes in source order, the order a source-space 3D transform uses */
    private val spatialSlots: IntArray by lazy {
        val spatialSourceAxes = xyzSourceAxes.filter { it >= 0 }.sorted()
        IntArray(3) { slot -> xyzSourceAxes[slot].takeIf { it >= 0 }?.let { spatialSourceAxes.indexOf(it) } ?: -1 }
    }

    /**
     * [transform] over the spatial source axes in source order, seen from the canonical x, y, z view: `P · transform · P⁻¹`
     * for the permutation `P` [toXyz] applies to the data. An absent dimension keeps the identity row and column
     */
    @JvmOverloads
    fun toXyz(transform: AffineTransform3D, target: AffineTransform3D = AffineTransform3D()): AffineTransform3D {
        target.set(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        for (row in 0..2) {
            val sourceRow = spatialSlots[row]
            if (sourceRow < 0)
                continue
            for (col in 0..2) {
                val sourceCol = spatialSlots[col]
                if (sourceCol >= 0)
                    target.set(transform.get(sourceRow, sourceCol), row, col)
            }
            target.set(transform.get(sourceRow, 3), row, 3)
        }
        return target
    }

    /** The inverse of [toXyz]: [xyzTransform] written into [target] in source order */
    fun fromXyz(xyzTransform: AffineTransform3D, target: AffineTransform3D): AffineTransform3D {
        for (row in 0..2) {
            val sourceRow = spatialSlots[row]
            if (sourceRow < 0)
                continue
            for (col in 0..2) {
                val sourceCol = spatialSlots[col]
                if (sourceCol >= 0)
                    target.set(xyzTransform.get(row, col), sourceRow, sourceCol)
            }
            target.set(xyzTransform.get(row, 3), sourceRow, 3)
        }
        return target
    }

    companion object {

        private val identityXyzAxes = intArrayOf(0, 1, 2)

        /** The mapping of [axes] with every non-spatial axis sliced at 0 */
        @JvmStatic
        fun of(axes: Array<Axis>) = sliceAtZero(axes.size, xyzSourceAxes(axes))

        /** The identity mapping: [toXyz] returns the source unchanged (already-canonical 3D, or channels kept nD). */
        @JvmStatic
        fun identity() = SpatialMapping(3, identityXyzAxes.copyOf(), LongArray(3))

        /** A mapping over [numDimensions] axes [xyzSourceAxes] that slice every non-spatial axis at 0. */
        @JvmStatic
        fun sliceAtZero(numDimensions: Int, xyzSourceAxes: IntArray) = SpatialMapping(numDimensions, xyzSourceAxes, LongArray(numDimensions))

        /** The source axis supplying each canonical dimension x, y, z from [axes] (`-1` when absent); at least one required. */
        @JvmStatic
        fun xyzSourceAxes(axes: Array<Axis>): IntArray {
            val xyz = intArrayOf(-1, -1, -1)
            for (idx in axes.indices) {
                when (axes[idx].name.lowercase(Locale.getDefault())) {
                    "x" -> xyz[0] = idx
                    "y" -> xyz[1] = idx
                    "z" -> xyz[2] = idx
                }
            }
            require(xyz.any { it >= 0 }) { "need at least one spatial (x, y, z) axis, got ${axes.map { it.name }}" }
            return xyz
        }

        /** Source-axis indices that are not spatial (not x/y/z): the slice/scrub axes (channel, time, ...), in source order. */
        @JvmStatic
        fun nonSpatialAxes(axes: Array<Axis>, numDimensions: Int): List<Int> {
            val spatial = xyzSourceAxes(axes).filter { it >= 0 }.toSet()
            return (0 until numDimensions).filterNot { it in spatial }
        }
    }
}
