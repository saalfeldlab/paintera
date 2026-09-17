package org.janelia.saalfeldlab.paintera.data

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
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

	/** Observable [activeInterval]. Only [sliceAt] and [reset] change it. */
	val activeIntervalProperty: ReadOnlyObjectProperty<Interval> = mutableActiveInterval.readOnlyProperty

	/**
	 * [fullInterval] with each axis this view drops collapsed to the position it is sliced at.
	 */
	private val activeInterval: Interval
		get() = mutableActiveInterval.get()

	/** The mapping at [activeInterval]; a mapping read before a slice change keeps the slice it was read for */
	val mapping: SpatialMapping
		get() = cachedMapping ?: SpatialMapping(numDimensions, xyzSourceAxes, activeInterval.minAsLongArray()).also { cachedMapping = it }

	fun spatialMapping(): SpatialMapping = mapping

	/** The source axes supplying none of x, y, z: the slice axes (channel, time, ...), in source order. */
	val nonSpatialAxes: List<Int>
		get() = mapping.nonSpatialAxes

	/** True when this view reduces the source's dimensionality; a crop alone does not make it sliced. */
	val isSliced: Boolean
		get() = !mapping.isIdentity

	/** The position [axis] is sliced at. */
	fun slicePosition(axis: Int): Long {
		require(axis in 0 until numDimensions) { "axis $axis out of bounds for $numDimensions dimensions" }

		return activeInterval.min(axis)
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

	/**
	 * A Canonical XYZ view of [source]. Axes may be reordered or sliced according to [mapping] to transform to the canonical XYZ view.
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

/** the [XyzView] an nD source presents its backing through, or null for a plain 3D source */
val DataSource<*, *>.xyzViewOrNull: XyzView?
	get() = when (this) {
		is MaskedSource<*, *> -> canvasXyzView
		is N5DataSource<*, *> -> metadataState.xyzView
		else -> null
	}

/**
 * convert nD source blocks to 3D canonical XYZ intervals at the slice [source] presents
 *
 * @param source the source the blocks are over
 * @return the canonical 3D intervals over the current slice
 */
fun Iterable<Interval>.toXyzBlocks(source: DataSource<*, *>): List<Interval> = source.xyzViewOrNull?.toXyzBlocks(this) ?: toList()
