package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import org.janelia.saalfeldlab.paintera.meshes.MeshExporter;

public class MeshExportResult<T> {

	private final MeshExporter<T> meshExporter;

	private final String filePath;

	private final int scale;

	public MeshExportResult(
			final MeshExporter<T> meshExporter,
			final String filePath,
			final int scale) {

		this.meshExporter = meshExporter;
		this.filePath = filePath;
		this.scale = scale;
	}

	public MeshExporter<T> getMeshExporter() {

		return meshExporter;
	}

	public String getFilePath() {

		return filePath;
	}

	public int getScale() {

		return scale;
	}
}
