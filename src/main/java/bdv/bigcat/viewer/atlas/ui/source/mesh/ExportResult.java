package bdv.bigcat.viewer.atlas.ui.source.mesh;

import bdv.bigcat.viewer.meshes.MeshExporter;

public class ExportResult
{
	private final MeshExporter meshExporter;

	private final long segmentId;

	private final String filePath;

	private final int scale;

	public ExportResult( final MeshExporter meshExporter, final long segmentId, final int scale, final String filePath )
	{
		this.meshExporter = meshExporter;
		this.segmentId = segmentId;
		this.filePath = filePath;
		this.scale = scale;
	}

	public MeshExporter getMeshExporter()
	{
		return meshExporter;
	}

	public long getSegmentId()
	{
		return segmentId;
	}

	public String getFilePath()
	{
		return filePath;
	}

	public int getScale()
	{
		return scale;
	}

}
