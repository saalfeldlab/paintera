@file:JvmName("VirtualCrop")

package org.janelia.saalfeldlab.paintera.state

import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.util.Intervals

/** [s0Crop] over scale [level], rounded outward to whole voxels */
fun cropAtLevel(s0Crop: Interval, sourceToXyzTransforms: Array<AffineTransform3D>, level: Int): Interval {
	if (level == 0)
		return s0Crop

	return Intervals.smallestContainingInterval(s0ToSLevel(sourceToXyzTransforms, level).estimateBounds(s0Crop))
}

/** [s0Crop] as voxel extents over scale [level]; a coarse voxel straddling the crop edge is only inside up to it */
fun cropBoundsAtLevel(s0Crop: Interval, sourceToXyzTransforms: Array<AffineTransform3D>, level: Int): RealInterval {
	val s0Extents = FinalRealInterval(
		DoubleArray(s0Crop.numDimensions()) { s0Crop.min(it) - 0.5 },
		DoubleArray(s0Crop.numDimensions()) { s0Crop.max(it) + 0.5 }
	)
	if (level == 0)
		return s0Extents

	return s0ToSLevel(sourceToXyzTransforms, level).estimateBounds(s0Extents)
}

private fun s0ToSLevel(sourceToXyzTransforms: Array<AffineTransform3D>, level: Int): AffineTransform3D {
	val s0ToXyz = sourceToXyzTransforms[0]
	val xyzToSLevel = sourceToXyzTransforms[level].inverse()
	return xyzToSLevel.copy().concatenate(s0ToXyz)
}
