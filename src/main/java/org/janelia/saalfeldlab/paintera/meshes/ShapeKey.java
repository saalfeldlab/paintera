package org.janelia.saalfeldlab.paintera.meshes;

import net.imglib2.FinalInterval;

public record ShapeKey<T>(
		T shapeId,
		int scaleIndex,
		int simplificationIterations,
		double smoothingLambda,
		int smoothingIterations,
		double minLabelRatio,
		boolean overlap,
		/* the block in source coordinates; nD, so the same spatial block at another slice is a different key */
		FinalInterval sourceInterval,
		/* the same block as the renderer sees it: always 3D */
		FinalInterval interval) {
}
