package org.janelia.saalfeldlab.util.math

import net.imglib2.realtransform.AffineGet
import org.janelia.saalfeldlab.util.allIndexed
import kotlin.math.abs
import kotlin.math.round

internal fun Double.roundToTolerance(tolerance: Double = 1e-6) : Double {
    return round(this / tolerance) * tolerance
}

fun Double.similarTo(other: Double, tolerance: Double = 1e-6) = abs(this - other) <= tolerance

fun AffineGet.similarTo(other: Any?, tolerance : Double = 1e-6) : Boolean {
    return other is AffineGet && allIndexed { i, j, v -> other[i,j].similarTo(v, tolerance) }
}