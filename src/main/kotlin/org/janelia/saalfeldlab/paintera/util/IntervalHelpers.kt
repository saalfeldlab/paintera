package org.janelia.saalfeldlab.paintera.util

import net.imglib2.FinalInterval
import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.realtransform.RealTransform
import net.imglib2.util.Intervals
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

        fun RealInterval.extendBy(extension: Double): RealInterval =
            FinalRealInterval(DoubleArray(nDim) { realMin(it) - extension }, DoubleArray(nDim) { realMax(it) + extension })

        fun RealInterval.extendBy(vararg extensions: Double): RealInterval {
            assert(extensions.size == numDimensions())
            val extendedMin = minAsDoubleArray().apply { forEachIndexed { idx, min -> this[idx] = min - extensions[idx] } }
            val extendedMax = maxAsDoubleArray().apply { forEachIndexed { idx, max -> this[idx] = max + extensions[idx] } }
            return FinalRealInterval(extendedMin, extendedMax)
        }

        internal fun Interval.scaleBy(scaleFactor: Int, scaleMin: Boolean = false): Interval {
            val newMin = minAsLongArray().also {
                if (scaleMin) {
                    it.forEachIndexed { idx, min -> it[idx] = min * scaleFactor }
                }
            }
            return FinalInterval(newMin, newMin.copyOf().apply { forEachIndexed { idx, min -> this[idx] = min - 1 + (dimension(idx) * scaleFactor) } })
        }

        internal fun Interval.scaleBy(vararg scaleFactors: Int, scaleMin: Boolean = false): Interval {
            assert(scaleFactors.size == nDim)
            val newMin = minAsLongArray().also {
                if (scaleMin) {
                    it.forEachIndexed { idx, min -> it[idx] = min * scaleFactors[idx] }
                }
            }
            return FinalInterval(newMin, newMin.copyOf().apply { forEachIndexed { idx, min -> this[idx] = min - 1 + dimension(idx) * scaleFactors[idx] } })
        }

        internal fun RealInterval.scaleBy(scaleFactor: Double, scaleMin: Boolean = false): RealInterval {
            val newMin = minAsDoubleArray().also {
                if (scaleMin) {
                    it.forEachIndexed { idx, min -> it[idx] = min * scaleFactor }
                }
            }
            return FinalRealInterval(newMin, newMin.copyOf().apply { forEachIndexed { idx, min -> this[idx] = min - 1 + ((min - realMin(idx)) * scaleFactor) } })
        }

        internal fun RealInterval.scaleBy(vararg scaleFactors: Double, scaleMin: Boolean = false): RealInterval {
            assert(scaleFactors.size == nDim)
            val newMin = minAsDoubleArray().also {
                if (scaleMin) {
                    it.forEachIndexed { idx, min -> it[idx] = min * scaleFactors[idx] }
                }
            }
            return FinalRealInterval(newMin, newMin.copyOf().apply { forEachIndexed { idx, min -> this[idx] = min - 1 + ((min - realMin(idx)) * scaleFactors[idx]) } })
        }


        fun RealInterval.shrinkBy(toShrinkBy: Double): RealInterval =
            FinalRealInterval(DoubleArray(nDim) { realMin(it) + toShrinkBy }, DoubleArray(nDim) { realMax(it) - toShrinkBy })

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
