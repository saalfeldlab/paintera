package org.janelia.saalfeldlab.paintera.meshes;

import net.imglib2.util.Util;

public class MeshWorkerPriority implements Comparable<MeshWorkerPriority>
{
	private static double EQUAL_DISTANCE_THRESHOLD = 1e-8;

	public final double distanceFromCamera;
	public final int scaleLevel;

	public MeshWorkerPriority(final double distanceFromCamera, final int scaleLevel)
	{
		this.distanceFromCamera = distanceFromCamera;
		this.scaleLevel = scaleLevel;
	}

	@Override
	public int compareTo(final MeshWorkerPriority other)
	{
		// Order by distance from the camera such that closer blocks come first.
		// In case the distances are equal, give priority to lower-resolution blocks.
		if (equals(other))
			return 0;
		else if (areDistancesEqual(distanceFromCamera, other.distanceFromCamera))
			return -Integer.compare(scaleLevel, other.scaleLevel);
		else
			return Double.compare(distanceFromCamera, other.distanceFromCamera);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (super.equals(obj))
			return true;

		if (obj instanceof MeshWorkerPriority)
		{
			final MeshWorkerPriority other = (MeshWorkerPriority) obj;
			return scaleLevel == other.scaleLevel && areDistancesEqual(distanceFromCamera, other.distanceFromCamera);
		}

		return false;
	}

	@Override
	public String toString()
	{
		return String.format("[distanceFromCamera=%.2f, scaleLevel=%d]", distanceFromCamera, scaleLevel);
	}

	private static boolean areDistancesEqual(final double d1, final double d2)
	{
		return Util.isApproxEqual(d1, d2, EQUAL_DISTANCE_THRESHOLD);
	}
}
