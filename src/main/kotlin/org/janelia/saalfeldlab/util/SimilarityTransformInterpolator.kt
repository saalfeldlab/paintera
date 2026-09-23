package org.janelia.saalfeldlab.util

import bdv.util.Affine3DHelpers
import bdv.viewer.animate.SimilarityTransformAnimator
import net.imglib2.realtransform.AffineTransform3D
import kotlin.math.abs
import kotlin.math.ln

class SimilarityTransformInterpolator(start: AffineTransform3D, end: AffineTransform3D ) : SimilarityTransformAnimator(start, end, 0.0, 0.0, 0) {

	private val scaleStart = Affine3DHelpers.extractScale(start, 0)
	private val scaleEnd = Affine3DHelpers.extractScale(end, 0)

	override operator fun get(t: Double): AffineTransform3D {
		return super.get(t)
	}

	/**
	 * The transform [fraction] of the way between the two transforms, invariant of their scales.
	 *
	 * [get]`(t)` will bias the interpolant toward the transform that has a larger scale.
	 */
	fun scaleInvariantGet(fraction: Double): AffineTransform3D {
		val scaleRate = scaleEnd / scaleStart
		if (abs(scaleRate - 1.0) < 0.0001)
			return get(fraction)
		val t = -ln(1.0 - fraction * (scaleEnd - scaleStart) / scaleEnd) / ln(scaleRate)
		return get(t)
	}
}

infix fun AffineTransform3D.interpolate(other : AffineTransform3D) = SimilarityTransformInterpolator(this, other)
