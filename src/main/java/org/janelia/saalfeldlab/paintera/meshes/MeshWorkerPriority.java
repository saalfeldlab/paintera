package org.janelia.saalfeldlab.paintera.meshes;

import net.imglib2.util.Util;

public record MeshWorkerPriority(double distanceFromCamera, int scaleLevel) implements Comparable<MeshWorkerPriority> {

	private static final double EQUAL_DISTANCE_THRESHOLD = 1e-8;

	@Override
	public int compareTo(final MeshWorkerPriority other) {
		// Order by distance from the camera such that closer blocks come first.
		// In case the distances are equal, give priority to lower-resolution blocks.
		if (equals(other))
			return 0;

		final boolean distancesEqual = areDistancesEqual(distanceFromCamera, other.distanceFromCamera);
		if (distancesEqual)
			return -Integer.compare(scaleLevel, other.scaleLevel);

		return Double.compare(distanceFromCamera, other.distanceFromCamera);
	}

	@Override
	public boolean equals(final Object obj) {

		if (this == obj)
			return true;

		if (obj instanceof MeshWorkerPriority(double objDist, int objLevel))
			return scaleLevel == objLevel && areDistancesEqual(distanceFromCamera, objDist);

		return false;
	}

	private static boolean areDistancesEqual(final double d1, final double d2) {

		return Util.isApproxEqual(d1, d2, EQUAL_DISTANCE_THRESHOLD);
	}
}
