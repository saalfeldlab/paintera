package org.janelia.saalfeldlab.paintera.util

import net.imglib2.*
import net.imglib2.algorithm.util.Grids
import net.imglib2.iterator.IntervalIterator
import net.imglib2.iterator.LocalizingIntervalIterator
import net.imglib2.realtransform.RealTransform
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.scale
import org.janelia.saalfeldlab.util.shape
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min

//TODO Look into using ntakt instead
class IntervalHelpers {
	companion object {

		@JvmStatic
		fun transformBoundingBox(boundingBox: RealInterval, transform: RealTransform): RealInterval {
			val nDim = boundingBox.numDimensions()
			val tl = DoubleArray(nDim) { Double.POSITIVE_INFINITY }
			val br = DoubleArray(nDim) { Double.NEGATIVE_INFINITY }
			val corner = DoubleArray(nDim)
			Grids.forEachOffset(LongArray(nDim) { 0 }, LongArray(nDim) { 1 }, IntArray(nDim) { 1 }) { offset ->
				Arrays.setAll(corner) { boundingBox.realCorner(it, offset[it]) }
				transform.apply(corner, corner)
				Arrays.setAll(tl) { min(tl[it], corner[it]) }
				Arrays.setAll(br) { max(br[it], corner[it]) }
			}

			return FinalRealInterval(tl, br)
		}

		@JvmStatic
		fun extendAndTransformBoundingBox(
			boundingBox: RealInterval,
			transform: RealTransform,
			extension: Double
		): RealInterval = transformBoundingBox(boundingBox.extendBy(extension), transform)

		val RealInterval.smallestContainingInterval: Interval
			get() = Intervals.smallestContainingInterval(this)

		val RealInterval.nDim: Int
			get() = this.numDimensions()

		val Interval.asRealInterval: RealInterval
			get() = FinalRealInterval(DoubleArray(nDim) { realMin(it) }, DoubleArray(nDim) { realMax(it) })

		@JvmStatic
		fun RealInterval.extendBy(extension: Double): RealInterval =
			FinalRealInterval(DoubleArray(nDim) { realMin(it) - extension }, DoubleArray(nDim) { realMax(it) + extension })

		fun RealInterval.extendBy(vararg extensions: Double): RealInterval {
			assert(extensions.size == numDimensions())
			val extendedMin = minAsDoubleArray().apply { forEachIndexed { idx, min -> this[idx] = min - extensions[idx] } }
			val extendedMax = maxAsDoubleArray().apply { forEachIndexed { idx, max -> this[idx] = max + extensions[idx] } }
			return FinalRealInterval(extendedMin, extendedMax)
		}

		@JvmStatic
		@JvmOverloads
		fun Interval.scale(scaleFactor: Int, scaleMin: Boolean = false): Interval {
			val newMin = minAsLongArray().also {
				if (scaleMin) {
					it.forEachIndexed { idx, min -> it[idx] = min * scaleFactor }
				}
			}
			return FinalInterval(newMin, newMin.copyOf().apply { forEachIndexed { idx, min -> this[idx] = min - 1 + (dimension(idx) * scaleFactor) } })
		}

		@JvmStatic
		@JvmOverloads
		fun Interval.scale(vararg scaleFactors: Int, scaleMin: Boolean = false): Interval {
			/* FIXME:  Look at the modified [RealInterval.scale] below for reference on how this should behave */
			assert(scaleFactors.size == nDim)
			val newMin = minAsLongArray().also {
				if (scaleMin) {
					it.forEachIndexed { idx, min -> it[idx] = min * scaleFactors[idx] }
				}
			}
			return FinalInterval(newMin, newMin.copyOf().apply { forEachIndexed { idx, min -> this[idx] = min - 1 + dimension(idx) * scaleFactors[idx] } })
		}

		/**
		 * Scale the real interval by the provided scale factors.
		 *
		 * If [scaleMin] is true, the size of the resulting interval will be the initial size * [scaleFactors]
		 * per dimension, and the min position of the resulting interval will also be scaled based on [scaleFactors].
		 *
		 * If [scaleMin] is false, the size will still be the result of the size * [scaleFactors] per dimension,
		 * but the min position will be the same as the original.
		 *
		 * @param scaleFactors to scale the interval by
		 * @param scaleMin whether to scale the min position, or just the size.
		 * @return the scaled RealInterval
		 */
		@JvmStatic
		@JvmOverloads
		fun RealInterval.scale(vararg scaleFactors: Double, scaleMin: Boolean = true): RealInterval {
			/* TODO: WRITE TESTS */
			var scales = when(scaleFactors.size) {
				nDim -> scaleFactors
				1 -> DoubleArray(nDim) {scaleFactors[0]}
				else -> throw IndexOutOfBoundsException("Provided scales of size ${scaleFactors.size} cannot be used to scale interval with nDim of $nDim")
			}

			val newMin = minAsDoubleArray()
			val newMax = maxAsDoubleArray()
			if (scaleMin) {
				newMin.also {
					it.forEachIndexed { idx, min -> it[idx] = min * scales[idx] }
				}
				newMax.also {
					it.forEachIndexed { idx, max -> it[idx] = max * scales[idx] }
				}
			} else {
				shape().forEachIndexed {idx, dimLen ->
					val scaledDimLen = dimLen * scales[idx]
					newMax[idx] = newMin[idx] + scaledDimLen - 1
				}
			}
			return FinalRealInterval( newMin, newMax)
		}


		@JvmStatic
		fun RealInterval.shrinkBy(toShrinkBy: Double): RealInterval =
			FinalRealInterval(DoubleArray(nDim) { realMin(it) + toShrinkBy }, DoubleArray(nDim) { realMax(it) - toShrinkBy })

		@JvmStatic
		fun RealInterval.shrinkBy(vararg toShrinkBy: Double): RealInterval {
			assert(toShrinkBy.size == numDimensions())
			val extendedMin = DoubleArray(nDim).apply { forEachIndexed { idx, _ -> this[idx] = realMin(idx) + toShrinkBy[idx] } }
			val extendedMax = DoubleArray(nDim).apply { forEachIndexed { idx, _ -> this[idx] = realMax(idx) - toShrinkBy[idx] } }
			return FinalRealInterval(extendedMin, extendedMax)
		}


		fun RealInterval.realCorner(d: Int, corner: Int) = if (corner == 0) realMin(d) else realMax(d)

		fun RealInterval.realCorner(d: Int, corner: Long) = if (corner == 0L) realMin(d) else realMax(d)
	}
}

class IntervalIterable(private val iterator: IntervalIterator) : Iterable<LongArray> {

	private val pos: LongArray = iterator.positionAsLongArray()

	override fun iterator(): Iterator<LongArray> {
		return IntervalPositionIterator(iterator, pos)
	}

	private class IntervalPositionIterator(
		private val interval: IntervalIterator,
		private val pos: LongArray
	) : Iterator<LongArray> {

		override fun hasNext(): Boolean {
			return interval.hasNext()
		}

		override fun next(): LongArray {
			interval.fwd()
			interval.localize(pos)
			return pos
		}
	}
}

open class ReusableIntervalIterator(interval: Interval) : LocalizingIntervalIterator(interval) {

	protected var reusableLastIndex: Long = lastIndex

	fun resetInterval(interval: Interval) {
		interval.min(min)
		interval.max(max)

		val m = n - 1
		var k = 1L.also { steps[0] = it }
		for (d in 0 until m) {
			val dimd = interval.dimension(d)
			dimensions[d] = dimd
			k *= dimd
			steps[d + 1] = k
		}
		val dimm = interval.dimension(m)
		dimensions[m] = dimm
		reusableLastIndex = k * dimm - 1
		reset()
	}

	override fun hasNext(): Boolean {
		return index < reusableLastIndex
	}
}


fun main() {

	val it = LocalizingIntervalIterator(Intervals.createMinSize(0,0,4,4))

	IntervalIterable(it).forEach { println(it.joinToString()) }



//	//TODO Caleb: Move to test
//	val zeroMin = Intervals.createMinMaxReal(0.0, 0.0, 99.0, 99.0)
//	zeroMin.shape().contentEquals(doubleArrayOf(100.0, 100.0))
//
//
//	val zeroMinDoubleTrue = zeroMin.scale(2.0, scaleMin = true)
//	zeroMinDoubleTrue.shape().contentEquals(doubleArrayOf(200.0, 200.0))
//
//	val zeroMinDoubleFalse = zeroMin.scale(2.0, scaleMin = false)
//	zeroMinDoubleFalse.shape().contentEquals(doubleArrayOf(200.0, 200.0))
//
//	val zeroMinDoubleFalse5 = zeroMin.scale(5.0, scaleMin = false)
//	zeroMinDoubleFalse5.shape().contentEquals(doubleArrayOf(500.0, 500.0))
//
//	val zeroMinHalfTrue: RealInterval = zeroMin.scale(.5, scaleMin = true)
//	zeroMinHalfTrue.shape().contentEquals(doubleArrayOf(50.0, 50.0))
//
//	val zeroMinHalfFalse = zeroMin.scale(.5, scaleMin = false)
//	zeroMinHalfFalse.shape().contentEquals(doubleArrayOf(50.0, 50.0))
//
//	val min = Intervals.createMinMaxReal(50.0, 50.0, 99.0, 99.0)
//	zeroMinDoubleTrue.shape().contentEquals(doubleArrayOf(200.0, 200.0))
//
//	val minDoubleTrue = min.scale(2.0, scaleMin = true)
//	minDoubleTrue.shape().contentEquals(doubleArrayOf(100.0, 100.0))
//
//	val minDoubleFalse = min.scale(2.0, scaleMin = false)
//	minDoubleFalse.shape().contentEquals(doubleArrayOf(100.0, 100.0))
//
//	val minHalfTrue = min.scale(.5, scaleMin = true)
//	minHalfTrue.shape().contentEquals(doubleArrayOf(25.0, 25.0))
//
//	val minHalfFalse = min.scale(.5, scaleMin = false)
//	minHalfFalse.shape().contentEquals(doubleArrayOf(25.0, 25.0))
}
