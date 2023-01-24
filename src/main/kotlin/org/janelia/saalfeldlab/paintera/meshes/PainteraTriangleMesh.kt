package org.janelia.saalfeldlab.paintera.meshes

data class PainteraTriangleMesh @JvmOverloads constructor(
	val vertices: FloatArray,
	val normals: FloatArray,
	val indices: IntArray,
	val textureCoordinates: FloatArray? = null
) {

	val isEmpty: Boolean = vertices.isEmpty() && normals.isEmpty() && indices.isEmpty()
	val isNotEmpty: Boolean = !isEmpty
}
