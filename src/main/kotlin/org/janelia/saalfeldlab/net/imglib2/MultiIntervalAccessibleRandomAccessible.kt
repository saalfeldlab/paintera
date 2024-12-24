package org.janelia.saalfeldlab.net.imglib2

import net.imglib2.*
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.util.interval


class MultiIntervalAccessibleRandomAccessible<T>(
	val rais : List<RandomAccessibleInterval<T>>,
	val outOfBounds: T,
	val filter : (T) -> Boolean,
) : RandomAccessible<T> {

	override fun numDimensions() = rais[0].numDimensions()

	override fun randomAccess() = MultiRandomAccess(
		numDimensions(),
		rais.map { it to it.randomAccess() },
		outOfBounds,
		filter
	)

	override fun randomAccess(interval: Interval): RandomAccess<T> = interval(interval).randomAccess()

	override fun getType() = outOfBounds

	class MultiRandomAccess<T>(
		numDimensions: Int,
		val ras: List<Pair<Interval, RandomAccess<T>>>,
		val outOfBounds: T,
		val filter: (T) -> Boolean = { true }
	) : Point(numDimensions), RandomAccess<T> {

		override fun get(): T {
			for ((interval, access) in ras) {
				if (Intervals.contains(interval, this)) {
					val at = access.setPositionAndGet(this)
					if (filter(at))
						return at
				}
			}
			return outOfBounds
		}

		override fun copy() = MultiRandomAccess(numDimensions(), ras, outOfBounds, filter)
	}
}