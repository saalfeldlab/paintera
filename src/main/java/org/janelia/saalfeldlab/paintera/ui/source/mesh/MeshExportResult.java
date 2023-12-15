package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import org.janelia.saalfeldlab.paintera.meshes.MeshExporter;

import java.util.List;

public class MeshExportResult<T> {

	private final MeshExporter<T> meshExporter;
	private final List<T> meshKeys;

	private final String filePath;

	private final int scale;

	public MeshExportResult(
			final MeshExporter<T> meshExporter,
			final String filePath,
			final int scale,
			final T meshKey) {
		this(meshExporter, filePath, scale, List.of(meshKey));
	}

	public MeshExportResult(
			final MeshExporter<T> meshExporter,
			final String filePath,
			final int scale,
			final List<T> meshKeys) {

		this.meshExporter = meshExporter;
		this.meshKeys = meshKeys;
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

	public List<T> getMeshKeys() {
		return meshKeys;
	}
}
