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
		// NOTE: do not consider scale level at this time. Currently order only by distance, where closer blocks come first.
		return Double.compare(distanceFromCamera, other.distanceFromCamera);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj instanceof MeshWorkerPriority)
		{
			final MeshWorkerPriority other = (MeshWorkerPriority) obj;
			return
					scaleLevel == other.scaleLevel &&
					Util.isApproxEqual(distanceFromCamera, other.distanceFromCamera, EQUAL_DISTANCE_THRESHOLD);
		}
		return super.equals(obj);
	}

	@Override
	public String toString()
	{
		return String.format("[distanceFromCamera=%.2f, scaleLevel=%d]", distanceFromCamera, scaleLevel);
	}
}
