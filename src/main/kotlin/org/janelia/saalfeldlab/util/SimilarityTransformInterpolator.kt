package org.janelia.saalfeldlab.util

import bdv.viewer.animate.SimilarityTransformAnimator
import net.imglib2.realtransform.AffineTransform3D

class SimilarityTransformInterpolator(start: AffineTransform3D, end: AffineTransform3D ) : SimilarityTransformAnimator(start, end, 0.0, 0.0, 0) {
	override operator fun get(t: Double): AffineTransform3D {
		return super.get(t)
	}
}

infix fun AffineTransform3D.interpolate(other : AffineTransform3D) = SimilarityTransformInterpolator(this, other)