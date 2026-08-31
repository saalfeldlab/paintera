package org.janelia.saalfeldlab.paintera.data

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.CellGrid
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.util.n5.SpatialMapping

/**
 * Canonical Paintera View (3D, XYZ) over an nD source, and the part of that source it presents.
 *
 * [activeInterval] is an nD interval in source-space that defines the active region. Must be either equal to or a strict
 * subset of [fullInterval].
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

	/** The source axes supplying none of x, y, z: the slice axes (channel, time, ...), in source order. */
	val nonSpatialAxes: List<Int> = (0 until numDimensions).filterNot { it in this.xyzSourceAxes }

	private val mutableActiveInterval = object : ReadOnlyObjectWrapper<Interval>(fullInterval) {
		override fun set(newValue: Interval?) {
			val clamped = coerceInFullExtent(newValue ?: fullInterval)
			if (Intervals.equals(get(), clamped))
				return
			super.set(clamped)
		}
	}

	/** Observable [activeInterval]. Only [sliceAt] and [reset] change it. */
	val activeIntervalProperty: ReadOnlyObjectProperty<Interval> = mutableActiveInterval.readOnlyProperty

	/**
	 * [fullInterval] with each axis this view drops collapsed to the position it is sliced at.
	 */
	private val activeInterval: Interval
		get() = mutableActiveInterval.get()

	/** The position [axis] is sliced at. */
	fun slicePosition(axis: Int): Long {
		require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

		return activeInterval.min(axis)
	}

	/** For any sliced axis `i`, the result of  slicePositions()[i] is the position in that axis that the
	 * view is sliced at. For any axis that is not slice, the value of the resulting array is meaningless.  */
	fun slicePositions(): LongArray = activeInterval.minAsLongArray()

	/** The mapping over [interval], whose min slices the non-spatial axes. Defaults to [activeInterval]. */
	@JvmOverloads
	fun spatialMapping(interval: Interval = activeInterval) = SpatialMapping(numDimensions, xyzSourceAxes, interval.minAsLongArray())

	/** [activeInterval] in [grid]'s block coordinates. Returns the active blocks in the CellGrid space */
	fun blockInterval(grid: CellGrid): Interval {
		require(grid.numDimensions() == numDimensions) { "grid must have $numDimensions dimensions, got ${grid.numDimensions()}" }

		val min = LongArray(numDimensions)
		val max = LongArray(numDimensions)
		for (axis in 0 until numDimensions) {
			min[axis] = activeInterval.min(axis) / grid.cellDimension(axis)
			max[axis] = activeInterval.max(axis) / grid.cellDimension(axis)
		}
		return FinalInterval(min, max)
	}

	/** Collapse [axis] at [position]. */
	fun sliceAt(axis: Int, position: Long) {
		require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

		val min = activeInterval.minAsLongArray()
		val max = activeInterval.maxAsLongArray()
		min[axis] = position
		max[axis] = position
		mutableActiveInterval.set(FinalInterval(min, max))
	}

	/** Restore the full dataset extent: unsliced. */
	fun reset() {
		mutableActiveInterval.set(fullInterval)
	}

	/** True when this view reduces the source's dimensionality; a crop alone does not make it sliced. */
	val isSliced: Boolean
		get() = !spatialMapping().isIdentity

	/**
	 * A Canonical XYZ view of [source]. Axes may be reordered or sliced according to [spatialMapping] to transform to the canonical XYZ view.
	 */
	fun <T> toXyz(source: RandomAccessibleInterval<T>): RandomAccessibleInterval<T> = spatialMapping().toXyz(source)

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
