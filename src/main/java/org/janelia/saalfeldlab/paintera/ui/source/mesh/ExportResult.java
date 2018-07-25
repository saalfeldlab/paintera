package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import org.janelia.saalfeldlab.paintera.meshes.MeshExporter;

public class ExportResult<T>
{
	private final MeshExporter<T> meshExporter;

	private final long[][] fragmentIds;

	private final long[] segmentId;

	private final String[] filePaths;

	private final int scale;

	// TODO: change scale parameter when the interface allows to export
	// different scales for different meshes at the same time
	public ExportResult(final MeshExporter<T> meshExporter, final long[][] fragmentIds, final long[] segmentId, final
	int scale, final String[] filePaths)
	{
		this.meshExporter = meshExporter;
		this.fragmentIds = fragmentIds;
		this.segmentId = segmentId;
		this.filePaths = filePaths;
		this.scale = scale;
	}

	public MeshExporter<T> getMeshExporter()
	{
		return meshExporter;
	}

	public long[][] getFragmentIds()
	{
		return fragmentIds;
	}

	public long[] getSegmentId()
	{
		return segmentId;
	}

	public String[] getFilePaths()
	{
		return filePaths;
	}

	public int getScale()
	{
		return scale;
	}

}
