package org.janelia.saalfeldlab.paintera.meshes;

public class MeshWorkerPriority implements Comparable<MeshWorkerPriority>
{
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
		return Double.compare(other.distanceFromCamera, this.distanceFromCamera);
	}
}
