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
 * Maps an nD source onto a canonical 3D (x, y, z) view, and back. > 3D dimensions are sliced at `slicePositions`
 * and <3D are widened to be a single position in the additional spatial dimension(s).
 *
 * Spatial axes are addressed by canonical index: [xyzSourceAxes]`[0, 1, 2]` is exactly the canonical.
 * `-1` at any position means it is a non-existent dimension that is added as a singleton.
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

    /* the slice axes, in source order */
    val nonSpatialAxes: List<Int> = (0 until numDimensions).filterNot { it in xyzSourceAxes }

    /** True when [toXyz] is the identity (already canonical 3D, x/y/z = 0/1/2); the source is not reduced/permuted/embedded. */
    val isIdentity: Boolean
        get() = numDimensions == 3 && xyzSourceAxes.contentEquals(intArrayOf(0, 1, 2))

    /** given [sourceValues] in source space, project it based on the spatial mapping.
    * If a dimensions is missing, it will be filled with [outOfBounds] */
    fun toSpatial(sourceValues: LongArray, outOfBounds: Long): LongArray {
        requireSourceShape(sourceValues.size)

        return LongArray(3) { slot ->
            xyzSourceAxes[slot].takeIf { it >= 0 }?.let { sourceValues[it] } ?: outOfBounds }
    }

    fun toSpatial(sourceValues: IntArray, outOfBounds: Int): IntArray {
        val sourceValuesLong = sourceValues.map { it.toLong() }.toLongArray()
        return toSpatial(sourceValuesLong, outOfBounds.toLong()).map { it.toInt() }.toIntArray()
    }

    fun toSpatial(sourceInterval: Interval, outOfBounds: Long): Interval =
        FinalInterval(toSpatial(sourceInterval.minAsLongArray(), outOfBounds), toSpatial(sourceInterval.maxAsLongArray(), outOfBounds))

    /* null when [sourceInterval] does not contain the slice */
    fun toSpatialOrNull(sourceInterval: Interval, outOfBounds: Long): Interval? {
        requireSourceShape(sourceInterval.numDimensions())

        for (axis in nonSpatialAxes)
            if (slicePositions[axis] < sourceInterval.min(axis) || slicePositions[axis] > sourceInterval.max(axis))
                return null
        return toSpatial(sourceInterval, outOfBounds)
    }

    /* without the intervals that do not contain the slice */
    fun toSpatial(sourceIntervals: Iterable<Interval>, outOfBounds: Long): List<Interval> = sourceIntervals.mapNotNull { toSpatialOrNull(it, outOfBounds) }

    /* the entries off the spatial axes come from [fill] */
    fun toSource(xyzValues: LongArray, fill: LongArray = slicePositions): LongArray {
        require(xyzValues.size == 3) { "xyzValues must have 3 dimensions, got ${xyzValues.size}" }
        requireSourceShape(fill.size)

        return fill.copyOf().also { widened ->
            for (slot in 0..2)
                xyzSourceAxes[slot].takeIf { it >= 0 }?.let { widened[it] = xyzValues[slot] }
        }
    }

    fun toSource(xyzValues: IntArray, fill: IntArray): IntArray {
        val xyzValuesLong = xyzValues.map { it.toLong() }.toLongArray()
        val fillLong = fill.map { it.toLong() }.toLongArray()
        return toSource(xyzValuesLong, fillLong).map { it.toInt() }.toIntArray()
    }

    fun toSource(xyzInterval: Interval, fill: LongArray = slicePositions): Interval =
        FinalInterval(toSource(xyzInterval.minAsLongArray(), fill), toSource(xyzInterval.maxAsLongArray(), fill))

    private fun requireSourceShape(size: Int) =
        require(size == numDimensions) { "shape must cover all $numDimensions source dimensions, got $size" }

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
        require(axis in nonSpatialAxes) { "axis $axis is not a non-spatial axis" }
        return Views.collapseReal(toXyz(source, axis))
    }

    /* the xyz view; [additionalAxis] is not sliced and ends up as the fourth dimension */
    private fun <T> toXyz(source: RandomAccessibleInterval<T>, additionalAxis: Int): RandomAccessibleInterval<T> {
        var view = source
        /* labels[i] = source axis currently at view position i (-1 marks a synthesized singleton) */
        val labels = (0 until numDimensions).toMutableList()
        /* slice every non-spatial axis; slice the highest current position first so lower positions stay put */
        for (position in labels.reversed()) {
            if (position in xyzSourceAxes || position == additionalAxis)
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

    private val spatialSlots: IntArray by lazy {
        val spatialSourceAxes = xyzSourceAxes.filter { it >= 0 }.sorted()
        IntArray(3) { slot -> xyzSourceAxes[slot].takeIf { it >= 0 }?.let { spatialSourceAxes.indexOf(it) } ?: -1 }
    }

    /**
     * [sourceToWorld] over the spatial source axes in source order
     */
    fun toXyz(sourceToWorld: AffineTransform3D): AffineTransform3D {
        val xyzToWorld = AffineTransform3D()
        mappedPositions().forEach { (mapped, unmapped) ->
            xyzToWorld.set(sourceToWorld.get(unmapped.row, unmapped.col), mapped.row, mapped.col)
        }
        return xyzToWorld
    }

    /* the inverse of [toXyz] */
    fun fromXyz(xyzToWorld: AffineTransform3D): AffineTransform3D {
        val sourceToWorld = AffineTransform3D()
        mappedPositions().forEach { (mapped, unmapped) ->
            sourceToWorld.set(xyzToWorld.get(mapped.row, mapped.col), unmapped.row, unmapped.col)
        }
        return sourceToWorld
    }

    private data class Position(val row: Int, val col: Int)

    /* mapped positions where each row/col cell of the mapped corresponds to the row/col cell of the unmapped */
    private fun mappedPositions(): Sequence<Pair<Position, Position>> = sequence {
        for (xyzRow in 0..2) {
            val sourceRow = spatialSlots[xyzRow]
            if (sourceRow < 0)
                continue
            for (xyzCol in 0..2) {
                val sourceCol = spatialSlots[xyzCol]
                if (sourceCol >= 0)
                    yield(Position(xyzRow, xyzCol) to Position(sourceRow, sourceCol))
            }
            yield(Position(xyzRow, TRANSLATION_COLUMN) to Position(sourceRow, TRANSLATION_COLUMN))
        }
    }

    fun withSlicePositions(positions: LongArray): SpatialMapping = SpatialMapping(numDimensions, xyzSourceAxes, positions)

    /** This mapping with [axis] sliced at [position] instead */
    fun withSlicePosition(axis: Int, position: Long): SpatialMapping {
        require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

        return withSlicePositions(slicePositions.copyOf().also { it[axis] = position })
    }

    /**
     * convert this mapping from voxel to block coordinates over [grid]
     *
     * @param grid a cell grid over all [numDimensions] source dimensions
     * @return the mapping whose slice positions are the [grid] blocks containing [slicePositions]
     */
    fun toBlockMapping(grid: CellGrid): SpatialMapping {
        require(grid.numDimensions() == numDimensions) { "grid must have $numDimensions dimensions, got ${grid.numDimensions()}" }

        return withSlicePositions(LongArray(numDimensions) { slicePositions[it] / grid.cellDimension(it) })
    }

    companion object {

        private const val TRANSLATION_COLUMN = 3

        private val identityXyzAxes = intArrayOf(0, 1, 2)

        /** The mapping of [axes] with every non-spatial axis sliced at 0 */
        @JvmStatic
        fun of(axes: Array<Axis>) = SpatialMapping(axes.size, xyzSourceAxes(axes), LongArray(axes.size))

        /** The identity mapping: [toXyz] returns the source unchanged (already-canonical 3D, or channels kept nD). */
        @JvmStatic
        fun identity() = SpatialMapping(3, identityXyzAxes.copyOf(), LongArray(3))

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
    }
}
