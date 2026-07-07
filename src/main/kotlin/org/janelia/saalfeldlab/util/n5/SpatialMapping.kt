
package org.janelia.saalfeldlab.util.n5

import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.util.addDimension
import org.janelia.saalfeldlab.util.hyperSlice
import org.janelia.saalfeldlab.util.moveAxis
import java.util.Locale

/**
 * Maps an nD source onto a canonical 3D (x, y, z) view, and back. Paintera is inherently 3D, so a source with more
 * than three dimensions is sliced (every non-spatial axis slice at a constant position) and one with fewer than three
 * spatial dimensions is embedded (a singleton axis added at the missing canonical slot) until exactly x, y, z remain.
 *
 * Spatial axes are addressed by canonical slot: [xyzSourceAxes]`[0]`, `[1]`, `[2]` give the source axis that supplies
 * x, y, z respectively, or `-1` when that dimension is absent and must be synthesized. So `[0, -1, 2]` means x comes
 * from source axis 0, z from source axis 2, and y is an inserted singleton - the result is still canonical `[x, y, z]`.
 *
 * The read direction ([to3D]) is pure view composition - `hyperSlice` to slice non-spatial axes, `moveAxis` to order the
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
    val xyzSourceAxes: IntArray,
    val slicePositions: LongArray
) {
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

    /** True when [to3D] is the identity (already canonical 3D, x/y/z = 0/1/2); the source is not reduced/permuted/embedded. */
    val isIdentity: Boolean
        get() = numDimensions == 3 && xyzSourceAxes.contentEquals(intArrayOf(0, 1, 2))

    /** Derive the canonical 3D (x, y, z) view of [source]: slice non-spatial axes, order the spatial axes, add missing singleton dimensions. */
    fun <T> to3D(source: RandomAccessibleInterval<T>): RandomAccessibleInterval<T> {
        if (isIdentity)
            return source

        var view = source
        /* labels[i] = source axis currently at view position i (-1 marks a synthesized singleton) */
        val labels = (0 until numDimensions).toMutableList()
        /* slice every non-spatial axis; slice the highest current position first so lower positions stay put */
        for (position in labels.reversed()) {
            if (position in actualSourceAxes)
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

    /** Project an nD shape array (block size, dimensions, ...) to an [x,y,z] shape array using the
     * this [SpatialMapping]. drops non-spatial dimensions, reorders to [x,y,z], adds single-position dimension if < 3 spatial dims */
    fun spatialProjection(shape: IntArray): IntArray = IntArray(3) { if (xyzSourceAxes[it] >= 0) shape[xyzSourceAxes[it]] else 1 }
    fun spatialProjection(shape: LongArray): LongArray = LongArray(3) { if (xyzSourceAxes[it] >= 0) shape[xyzSourceAxes[it]] else 1L }

    /**
     * Map a canonical 3D (x, y, z) view back to the full nD source view: drop the embedded singletons for absent
     * dimensions, send the present spatial axes to their source positions, and make every non-spatial axis a singleton
     * at its slice position. The inverse of [to3D]; used to write a committed 3D block back into the nD dataset.
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
        val blockSize = IntArray(numDimensions) { 1 }
        for (slot in 0..2) if (xyzSourceAxes[slot] >= 0) blockSize[xyzSourceAxes[slot]] = blockSize3D[slot]
        return blockSize
    }

    companion object {

        private val identityXyzAxes = intArrayOf(0, 1, 2)

        /** The identity mapping: [to3D] returns the source unchanged (already-canonical 3D, or channels kept nD). */
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
