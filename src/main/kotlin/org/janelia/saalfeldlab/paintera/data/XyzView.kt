package org.janelia.saalfeldlab.paintera.data

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.util.n5.SpatialMapping

/**
 * Canonical Paintera View (3D, XYZ) over an nD source, and the part of that source it presents.
 *
 * [fullInterval] is the source order interval over the entire backing source.
 *
 * 3 Main use cases:
 *  1. Backing data is 3D but spatial axes need to be reordered to match XYZ ([SpatialMapping.xyzSourceAxes])
 *  2. Backing data is >3D or <3D and needs to be sliced/padded to 3D ([SpatialMapping.slicePositions])
 *  3. A cropped view over the backing data is desired ([XyzView.activeInterval])
 *
 *
 * @param xyzSourceAxes array of len 3; the value at index 0,1,2 corresponds to the source indices for the XYZ dimension
 * @param fullInterval the uncropped, unsliced interval covering the entire backing source
 */
class XyzView(
    xyzSourceAxes: IntArray,
    val fullInterval: Interval
) {

    constructor(xyzSourceAxes: IntArray, dimensions: LongArray) :
            this(xyzSourceAxes, FinalInterval(LongArray(dimensions.size), LongArray(dimensions.size) { dimensions[it] - 1 }))

    val xyzSourceAxes = xyzSourceAxes.copyOf()

    val numDimensions: Int = fullInterval.numDimensions()

    private var cachedMapping: SpatialMapping? = null

    private val mutableActiveInterval = object : ReadOnlyObjectWrapper<Interval>(fullInterval) {
        override fun set(newValue: Interval?) {
            val clamped = coerceInFullExtent(newValue ?: fullInterval)
            if (Intervals.equals(get(), clamped))
                return
            cachedMapping = null
            super.set(clamped)
        }
    }

    /** Observable [activeInterval]. Only [sliceAt], [setCropInterval] and [reset] change it. */
    val activeIntervalProperty: ReadOnlyObjectProperty<Interval> = mutableActiveInterval.readOnlyProperty

    /**
     * Source space interval presented by this XyzView. sliced axes are dropped and may be a spatial subset of [fullInterval],
     * which indicates this is a cropped view.
     */
    private val activeInterval: Interval by activeIntervalProperty.nonnullVal()

    /** The spatial part of [activeInterval] as a 3D xyz interval, or null when every the spatial axes are not cropped */
    val xyzCrop: Interval?
        get() {
            val cropped = xyzSourceAxes.any { axis ->
                axis >= 0
                        && (activeInterval.min(axis) != fullInterval.min(axis)
                        || activeInterval.max(axis) != fullInterval.max(axis))
            }
            return if (cropped) mapping.toSpatial(activeInterval, 0L) else null
        }

    /** set the spatial crop interval for the current XyzView. */
    fun setCropInterval(xyzCrop: Interval?) {
        val min = activeInterval.minAsLongArray()
        val max = activeInterval.maxAsLongArray()
        for (slot in 0..2) {
            val axis = xyzSourceAxes[slot]
            if (axis < 0)
                continue
            min[axis] = xyzCrop?.min(slot) ?: fullInterval.min(axis)
            max[axis] = xyzCrop?.max(slot) ?: fullInterval.max(axis)
        }
        mutableActiveInterval.set(FinalInterval(min, max))
    }

    /** The mapping from the source to the current XYZ slice, based on the activeInterval min for slicePositions.  */
    val mapping: SpatialMapping
        get() = cachedMapping ?: SpatialMapping(numDimensions, xyzSourceAxes, slicePositions = activeInterval.minAsLongArray()).also { cachedMapping = it }

    fun spatialMapping(): SpatialMapping = mapping

    /** Non-XYZ axes, e.g. (channel, time, ...), in source order. */
    val nonSpatialAxes: List<Int>
        get() = mapping.nonSpatialAxes

    /** True when this view reduces the source's dimensionality
     *
     * NOTE: a crop over a 3D datasource is not considered sliced. */
    val isSliced: Boolean
        get() = !mapping.isIdentity

    /** Get the position this view is sliced at for the given [axis] index. */
    fun slicePosition(axis: Int): Long {
        require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

        return activeInterval.min(axis)
    }

    /** Set the slice position for the view to [position] at the given [axis] index.  */
    fun sliceAt(axis: Int, position: Long) {
        require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

        val min = activeInterval.minAsLongArray()
        val max = activeInterval.maxAsLongArray()
        min[axis] = position
        max[axis] = position
        mutableActiveInterval.set(FinalInterval(min, max))
    }

    /** Restore [fullInterval]: the crop is removed and every non-spatial axis is sliced at [fullInterval.min] positions */
    fun reset() {
        mutableActiveInterval.set(fullInterval)
    }

    /**
     * A Canonical XYZ view of [source]. Axes may be reordered or sliced according to [mapping] to transform to the canonical XYZ view.
     * The crop is not applied: it is at s0, while [source] may be any scale level; see [xyzCrop].
     */
    fun <T> toXyz(source: RandomAccessibleInterval<T>): RandomAccessibleInterval<T> = mapping.toXyz(source)

    /**
     * convert nD source [blocks] to 3D canonical XYZ intervals at the current slice
     *
     * @param blocks intervals in source space; a 3D interval is already canonical and kept as is
     * @return the canonical 3D intervals at the current slice
     */
    fun toXyzBlocks(blocks: Iterable<Interval>): List<Interval> {
        val mapping = mapping
        return blocks.mapNotNull { block ->
            when {
                mapping.isIdentity -> block
                block.numDimensions() != numDimensions -> block
                else -> mapping.toSpatialOrNull(block, 0L)
            }
        }
    }

    private fun coerceInFullExtent(interval: Interval): Interval {
        require(interval.numDimensions() == numDimensions) { "interval must have $numDimensions dimensions, got ${interval.numDimensions()}" }

        val min = LongArray(numDimensions)
        val max = LongArray(numDimensions)
        for (axis in 0 until numDimensions) {
            min[axis] = interval.min(axis).coerceIn(fullInterval.min(axis), fullInterval.max(axis))
            max[axis] = interval.max(axis).coerceIn(min[axis], fullInterval.max(axis))
        }
        return FinalInterval(min, max)
    }

    override fun toString() = "XyzView(region=${Intervals.toString(activeInterval)}, fullExtent=${Intervals.toString(fullInterval)})"

    companion object {

        /** A view over [axes], presenting all of [fullExtent]. */
        @JvmStatic
        fun of(axes: Array<Axis>, fullExtent: Interval) = XyzView(SpatialMapping.xyzSourceAxes(axes), fullExtent)

        /** A view over [axes], presenting a whole dataset of [dimensions]. */
        @JvmStatic
        fun of(axes: Array<Axis>, dimensions: LongArray) = XyzView(SpatialMapping.xyzSourceAxes(axes), dimensions)
    }
}

/** the [XyzView] a source presents its backing through, or null for a source without one */
val DataSource<*, *>.xyzViewOrNull: XyzView?
    get() = when (this) {
        is MaskedSource<*, *> -> canvasXyzView
        is RandomAccessibleIntervalDataSource<*, *> -> xyzView
        else -> null
    }

/**
 * convert nD source blocks to 3D canonical XYZ intervals at the slice [source] presents
 *
 * @param source the source the blocks are over
 * @return the canonical 3D intervals over the current slice
 */
fun Iterable<Interval>.toXyzBlocks(source: DataSource<*, *>): List<Interval> = source.xyzViewOrNull?.toXyzBlocks(this) ?: toList()
