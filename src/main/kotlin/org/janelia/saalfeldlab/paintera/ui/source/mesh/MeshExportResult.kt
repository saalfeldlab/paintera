package org.janelia.saalfeldlab.paintera.ui.source.mesh

import org.janelia.saalfeldlab.paintera.meshes.MeshExporter

data class MeshExportResult<T>(
	val meshExporter: MeshExporter<T>,
	val filePath: String,
	val scale: Int,
	val meshKeys: List<T>
)