package org.janelia.saalfeldlab.paintera.meshes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

public class MeshExporterObj<T> extends MeshExporter<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Override
	protected void save(String path, final String id, final float[] vertices, final float[] normals, int[] indices, final boolean append) throws IOException {

		path = path + ".obj";

		try (final FileWriter writer = new FileWriter(path, append)) {
			final float[] texCoords = new float[]{0.0f, 0.0f};
			final StringBuilder sb = new StringBuilder();

			if (!append) {
				sb.append("# id: ").append(id).append("\n");
			}

			final int numVertices = vertices.length;
			for (int k = 0; k < numVertices; k += 3) {
				sb.append("\nv ").append(vertices[k]).append(" ").append(vertices[k + 1]).append(" ").append(vertices[k + 2]);
			}

			sb.append("\n");
			final int numNormals = normals.length;
			for (int k = 0; k < numNormals; k += 3) {
				sb.append("\nvn ").append(normals[k]).append(" ").append(normals[k + 1]).append(" ").append(normals[k + 2]);
			}

			sb.append("\n");
			final int numTexCoords = texCoords.length;
			for (int k = 0; k < numTexCoords; k += 2) {
				sb.append("\nvt ").append(texCoords[k]).append(" ").append(texCoords[k + 1]);
			}

			sb.append("\n");
			for (int k = 0; k < indices.length; k += 3) {
				sb.append("\nf ")
						.append(indices[k + 0]).append("/").append(1).append("/").append(indices[k + 0]).append(" ")
						.append(indices[k + 1]).append("/").append(1).append("/").append(indices[k + 1]).append(" ")
						.append(indices[k + 2]).append("/").append(1).append("/").append(indices[k + 2]);
			}

			writer.append(sb.toString());
		}
	}
}
