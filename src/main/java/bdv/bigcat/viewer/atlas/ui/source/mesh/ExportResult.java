package bdv.bigcat.viewer.atlas.ui.source.mesh;

import bdv.bigcat.viewer.meshes.MeshExporter;

public class ExportResult
{
	private final MeshExporter meshExporter;

	private final long[] segmentId;

	private final String[] filePaths;

	private final int scale;

	// TODO: change scale parameter when the interface allows to export
	// different scales for different meshes at the same time
	public ExportResult( final MeshExporter meshExporter, final long[] segmentId, final int scale, final String[] filePaths )
	{
		this.meshExporter = meshExporter;
		this.segmentId = segmentId;
		this.filePaths = filePaths;
		this.scale = scale;
	}

	public MeshExporter getMeshExporter()
	{
		return meshExporter;
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
