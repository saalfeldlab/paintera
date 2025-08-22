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
		FinalInterval interval) {

	public FinalInterval interval() {

		return interval;
	}
}
