package org.janelia.saalfeldlab.paintera.meshes

import net.imglib2.util.Pair

data class PainteraTriangleMesh @JvmOverloads constructor(
    val vertices: FloatArray,
    val normals: FloatArray,
    val indices: IntArray? = null,
    val textureCoordinates: FloatArray? = null
) {

    val isEmpty: Boolean = vertices.isEmpty() && normals.isEmpty()
    val isNotEmpty: Boolean = !isEmpty

    companion object {
        @JvmStatic
        fun fromVerticesAndNormals(vertices: FloatArray?, normals: FloatArray?): PainteraTriangleMesh? {
            return vertices?.let { v -> normals?.let { n -> PainteraTriangleMesh(v, n) } }
        }

        @JvmStatic
        fun fromVerticesAndNormals(verticesAndNormals: Pair<FloatArray?, FloatArray?>?) = fromVerticesAndNormals(verticesAndNormals?.a, verticesAndNormals?.b)
    }

}
