package org.janelia.saalfeldlab.util.math

import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.util.forEachIndexed
import kotlin.math.round

internal class HashableTransform(affineTransform3D: AffineTransform3D, val tolerance : Double = 1e-6) : AffineTransform3D() {

	init {
		set(affineTransform3D)
	}

	override fun hashCode(): Int {
		val constant = 37
		var total = 17
		forEachIndexed { _, _, value ->
			total = total * constant + value.roundToTolerance(tolerance).hashCode()
		}
		return total
	}

	override fun equals(other: Any?): Boolean {
		return similarTo(other)
	}

	companion object {

		internal fun AffineTransform3D.hashable() = (this as? HashableTransform) ?: HashableTransform(this)
	}
}