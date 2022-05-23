package org.janelia.saalfeldlab.util

import net.imglib2.*
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory
import net.imglib2.util.Intervals
import net.imglib2.view.IntervalView
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval

/* Interval extensions */
infix fun Interval.union(other: Interval?): Interval = other?.let { Intervals.union(this, other) } ?: this
infix fun Interval.intersect(other: Interval?): Interval = other?.let { Intervals.intersect(this, other) } ?: this

/* RealInterval extensions */
infix fun RealInterval.union(other: RealInterval?): RealInterval = other?.let { Intervals.union(this, other) } ?: this
infix fun RealInterval.intersect(other: RealInterval?): RealInterval = other?.let { Intervals.intersect(this, other) } ?: this
internal fun RealInterval.shape() = maxAsDoubleArray().zip(minAsDoubleArray()).map { (max, min) -> max - min + 1 }.toDoubleArray()

/* RealRandomAccessible Extensions*/
fun <T> RealRandomAccessible<T>.interval(interval: RealInterval): IntervalView<T> = this.raster().interval(interval.smallestContainingInterval)
fun <T> RealRandomAccessible<T>.raster(): RandomAccessibleOnRealRandomAccessible<T> = Views.raster(this)
operator fun <T> RealRandomAccessible<T>.get(vararg pos: Double): T = getAt(*pos)
operator fun <T> RealRandomAccessible<T>.get(vararg pos: Float): T = getAt(*pos)
operator fun <T> RealRandomAccessible<T>.get(pos: RealLocalizable): T = getAt(pos)

/* RandomAccessible Extensions */
fun <T> RandomAccessible<T>.interpolateNearestNeighbor(): RealRandomAccessible<T> = Views.interpolate(this, NearestNeighborInterpolatorFactory())
fun <T> RandomAccessible<T>.interval(interval: Interval): IntervalView<T> = Views.interval(this, interval)
operator fun <T> RandomAccessible<T>.get(vararg pos: Long): T = getAt(*pos)
operator fun <T> RandomAccessible<T>.get(vararg pos: Int): T = getAt(*pos)
operator fun <T> RandomAccessible<T>.get(pos: Localizable): T = getAt(pos)

/* RealPoint Extensions */

fun RealPoint.floor(): Point {
    val pointVals = LongArray(this.numDimensions())
    for (i in 0 until this.numDimensions()) {
        pointVals[i] = kotlin.math.floor(getDoublePosition(i)).toLong()
    }
    return Point(*pointVals)
}

fun RealPoint.ceil(): Point {
    val pointVals = LongArray(this.numDimensions())
    for (i in 0 until this.numDimensions()) {
        pointVals[i] = kotlin.math.ceil(getDoublePosition(i)).toLong()
    }
    return Point(*pointVals)
}

fun RealPoint.toPoint(): Point {
    val pointVals = LongArray(this.numDimensions())
    for (i in 0 until this.numDimensions()) {
        pointVals[i] = getDoublePosition(i).toLong()
    }
    return Point(*pointVals)
}
