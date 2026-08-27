package org.janelia.saalfeldlab.paintera.data

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.cell.CellGrid
import net.imglib2.util.Intervals
import net.imglib2.view.Views
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

	val regionProperty: ObjectProperty<Interval> = object : SimpleObjectProperty<Interval>(fullInterval) {
		override fun set(newValue: Interval?) {
			val clamped = coerceInFullExtent(newValue ?: fullInterval)
			if (Intervals.equals(get(), clamped))
				return
			super.set(clamped)
		}
	}

	/**
	 * Active interval over the [fullInterval] of a source. May be smaller or equal to [fullInterval].
	 * larger intervals are clamped. dimensionality must match [numDimensions]. If [fullInterval] is greater
	 * than 3D, the min positions of [activeInterval] will be used to slice the non-spatial dimensions.
	 */
	var activeInterval: Interval
		get() = regionProperty.get()
		set(value) {
			regionProperty.set(value)
		}

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

	/** [activeInterval] in XYZ space. Setting it crops [activeInterval], leaving the sliced axes where they are. */
	var xyzInterval: Interval
		get() = spatialMapping().toXyzInterval(activeInterval)
		set(value) {
			activeInterval = spatialMapping().toSourceInterval(value)
		}

	/** True when [xyzInterval] is a subset of the source's spatial extent. */
	val isCropped: Boolean
		get() = spatialMapping().run {
			return !Intervals.equals(toXyzInterval(activeInterval), toXyzInterval(fullInterval))
		}

	/** Collapse [axis] at [position]. */
	fun sliceAt(axis: Int, position: Long) {
		require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

		val min = activeInterval.minAsLongArray()
		val max = activeInterval.maxAsLongArray()
		min[axis] = position
		max[axis] = position
		activeInterval = FinalInterval(min, max)
	}

	/** Restore the full dataset extent: uncropped and unsliced. */
	fun reset() {
		activeInterval = fullInterval
	}

	/**
	 * A Canonical XYZ view of [source] over [interval]. default `interval` is [activeInterval].
	 *
	 * [interval] is in [source]'s own coordinates, and its min slices the non-spatial axes; for a scale level below 0
	 * the caller rescales with the level transforms it holds.
	 */
	fun <T> toXyz(source: RandomAccessibleInterval<T>, interval: Interval = this.activeInterval): RandomAccessibleInterval<T> {
		val restricted = if (Intervals.equals(source, interval)) source else Views.interval(source, interval)
		return spatialMapping(interval).toXyz(restricted)
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
