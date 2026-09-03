package org.janelia.saalfeldlab.util.fx

import bdv.util.Affine3DHelpers.extractScale
import com.google.common.math.DoubleMath.fuzzyEquals
import javafx.scene.transform.Affine
import javafx.scene.transform.Transform
import net.imglib2.realtransform.AffineTransform3D
import kotlin.math.round

object Transforms {

	@JvmStatic
	fun Transform.toAffineTransform3D() = AffineTransform3D().apply {
        this.set(
            this@toAffineTransform3D.mxx, this@toAffineTransform3D.mxy, this@toAffineTransform3D.mxz, this@toAffineTransform3D.tx,
            this@toAffineTransform3D.myx, this@toAffineTransform3D.myy, this@toAffineTransform3D.myz, this@toAffineTransform3D.ty,
            this@toAffineTransform3D.mzx, this@toAffineTransform3D.mzy, this@toAffineTransform3D.mzz, this@toAffineTransform3D.tz
        )
	}

	@JvmStatic
	fun AffineTransform3D.toTransformFX() = Affine(
		this[0, 0], this[0, 1], this[0, 2], this[0, 3],
		this[1, 0], this[1, 1], this[1, 2], this[1, 3],
		this[2, 0], this[2, 1], this[2, 2], this[2, 3]
	)

	/**
	 * Per-axis scale of [to] relative to [from].
     * Scale Factors within [tolerance] of an integer are rounded to the integer.
	 */
	@JvmStatic
	@JvmOverloads
	fun relativeScale(from: AffineTransform3D, to: AffineTransform3D, tolerance: Double = 1e-6) = DoubleArray(3) { axis ->
		val ratio = extractScale(to, axis) / extractScale(from, axis)
		round(ratio).takeIf { fuzzyEquals(ratio, it, tolerance) } ?: ratio
	}
}
