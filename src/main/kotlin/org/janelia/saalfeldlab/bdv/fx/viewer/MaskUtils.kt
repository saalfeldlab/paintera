package org.janelia.saalfeldlab.bdv.fx.viewer

import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.RealPoint
import net.imglib2.RealRandomAccess
import net.imglib2.RealRandomAccessible
import net.imglib2.type.mask.Masked

object MaskUtils {

	/**
	 * [bdv.viewer.MaskUtils.extendAndInterpolateMasked] for types it cannot interpolate, such as composites: [interpolated]
	 * is already extended and interpolated, and the mask is 1 over the pixels of [source] and 0 past them
	 */
	@JvmStatic
	fun <V> maskedInside(interpolated: RealRandomAccessible<V>, source: Interval): RealRandomAccessible<Masked<V>> {
		val n = source.numDimensions()
		val bounds = FinalRealInterval(DoubleArray(n) { source.realMin(it) - 0.5 }, DoubleArray(n) { source.realMax(it) + 0.5 })
		return MaskedInside(interpolated, bounds)
	}

	private class MaskedInside<V>(private val values: RealRandomAccessible<V>, private val bounds: RealInterval) : RealRandomAccessible<Masked<V>> {

		override fun numDimensions() = bounds.numDimensions()

		override fun getType(): Masked<V> = Access()

		override fun realRandomAccess(): RealRandomAccess<Masked<V>> = Access()

		override fun realRandomAccess(interval: RealInterval): RealRandomAccess<Masked<V>> = Access()

		private inner class Access : RealPoint(bounds.numDimensions()), RealRandomAccess<Masked<V>>, Masked<V> {

			private val access = values.realRandomAccess()

			override fun get(): Masked<V> = this

			override fun getType(): Masked<V> = this

			override fun value(): V {
				access.setPosition(this)
				return access.get()
			}

			override fun mask(): Double {
				for (d in 0 until numDimensions()) {
					val position = getDoublePosition(d)
					if (position < bounds.realMin(d) || position > bounds.realMax(d))
						return 0.0
				}
				return 1.0
			}

			override fun setValue(value: V) = throw UnsupportedOperationException()

			override fun setMask(mask: Double) = throw UnsupportedOperationException()

			override fun copy(): RealRandomAccess<Masked<V>> = Access().also { it.setPosition(this) }
		}
	}
}
