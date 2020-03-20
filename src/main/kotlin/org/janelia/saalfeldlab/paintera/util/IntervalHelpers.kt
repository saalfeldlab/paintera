package org.janelia.saalfeldlab.paintera.util

import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.algorithm.util.Grids
import net.imglib2.iterator.IntervalIterator
import net.imglib2.realtransform.RealTransform
import net.imglib2.util.Intervals
import java.util.*
import kotlin.math.max
import kotlin.math.min

class IntervalHelpers {

    companion object {
        @JvmStatic
        fun transformBoundingBox(boundingBox: RealInterval, transform: RealTransform): RealInterval {
            val nDim = boundingBox.numDimensions()
            val tl = DoubleArray(nDim) { Double.POSITIVE_INFINITY }
            val br = DoubleArray(nDim) { Double.NEGATIVE_INFINITY }
            val corner = DoubleArray(nDim)
            Grids.forEachOffset(LongArray(nDim) { 0 }, LongArray(nDim) { 1 }, IntArray(nDim) { 1 } ) { offset ->
                Arrays.setAll(corner) { boundingBox.realCorner(it, offset[it]) }
                transform.apply(corner, corner)
                Arrays.setAll(tl) { min(tl[it], corner[it]) }
                Arrays.setAll(br) { max(br[it], corner[it]) }
            }

            return FinalRealInterval(tl, br)
        }

        @JvmStatic
        fun extendAndTransformBoundingBox(
            boundingBox: Interval,
            transform: RealTransform,
            extension: Double): RealInterval = transformBoundingBox(boundingBox.extendBy(extension), transform)

        val RealInterval.smallestContainingInterval: Interval
            get() = Intervals.smallestContainingInterval(this)

        val RealInterval.nDim: Int
            get() = this.numDimensions()

        val Interval.asRealInterval: RealInterval
            get() = FinalRealInterval(DoubleArray(nDim) { realMin(it) }, DoubleArray(nDim) { realMax(it) })

        fun RealInterval.extendBy(extension: Double): RealInterval = FinalRealInterval(DoubleArray(nDim) { realMin(it) - extension }, DoubleArray(nDim) { realMax(it) + extension })

        fun RealInterval.realCorner(d: Int, corner: Int) = if (corner == 0) realMin(d) else realMax(d)

        fun RealInterval.realCorner(d: Int, corner: Long) = if (corner == 0L) realMin(d) else realMax(d)
    }

}
