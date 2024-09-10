package org.janelia.saalfeldlab.net.imglib2

import net.imglib2.*
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.util.realInterval


class MultiRealIntervalAccessibleRealRandomAccessible<T>(
	val rais : List<RealRandomAccessibleRealInterval<T>>,
	val outOfBounds: T,
	val filter : (T) -> Boolean,
) : RealRandomAccessible<T> {

	override fun numDimensions() = rais[0].numDimensions()

	override fun realRandomAccess() = MultiRealRaiRealRandomAccess(
		numDimensions(),
		rais.map { it to it.realRandomAccess() },
		outOfBounds,
		filter
	)

	override fun realRandomAccess(interval: RealInterval) = realInterval(interval).realRandomAccess()


	class MultiRealRaiRealRandomAccess<T>(
		numDimensions: Int,
		val rrais: List<Pair<RealInterval, RealRandomAccess<T>>>,
		val outOfBounds: T,
		val filter: (T) -> Boolean = { true }
	) : RealPoint(numDimensions), RealRandomAccess<T> {

		override fun get(): T {
			for ((interval, access) in rrais) {
				if (Intervals.contains(interval, this)) {
					val at = access.setPositionAndGet(this)
					if (filter(at))
						return at
				}
			}
			return outOfBounds
		}

		override fun copy() = MultiRealRaiRealRandomAccess(numDimensions(), rrais, outOfBounds, filter)

	}


}