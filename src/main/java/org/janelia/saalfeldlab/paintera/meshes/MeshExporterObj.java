package org.janelia.saalfeldlab.paintera.meshes;

import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

public class MeshExporterObj<T> extends MeshExporter<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private long numVertices = 0;

	public void exportMaterial(String path, final long[] ids, final Color[] colors) {
		final String materialPath = path + ".mtl";
		try (final FileWriter writer = new FileWriter(materialPath, false)) {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < ids.length; i++) {
				final long id = ids[i];
				final Color specularColor = colors[i];
				final double red = specularColor.getRed();
				final double green = specularColor.getGreen();
				final double blue = specularColor.getBlue();
				sb.append("newmtl ").append(id).append("\n");
				sb.append("Ns ").append(50).append("\n");
				/* Ka Color.WHITE */
				sb.append("Ka ").append(1).append(" ").append(1).append(" ").append(1).append(" ").append("\n");
				/* Ks Color.WHITE */
				sb.append("Ks ").append(1).append(" ").append(1).append(" ").append(1).append(" ").append("\n");
				/* Kd color by id */
				sb.append("Kd ").append(red).append(" ").append(green).append(" ").append(blue).append(" ").append("\n");
				sb.append("d ").append(specularColor.getOpacity()).append("\n");
				sb.append("illum ").append(2).append("\n");
			}
			writer.append(sb.toString());
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void save(final String path, final String id, final float[] vertices, final float[] normals, int[] indices, final boolean append) throws IOException {

		final String materialPath = path + ".mtl";
		final File materialFile = new File(materialPath);
		final String materialName = materialFile.getName();

		final boolean hasMaterial = materialFile.exists();

		final String objPath = path + ".obj";

		try (final FileWriter writer = new FileWriter(objPath, append)) {
			final float[] texCoords = new float[]{0.0f, 0.0f};
			final StringBuilder sb = new StringBuilder();

			if (append)
				sb.append("\n");
			else if (hasMaterial)
				sb.append("mtllib ").append(materialName).append("\n").append("\n");

			sb.append("o ").append(id).append("\n");

			for (int k = 0; k < vertices.length; k += 3) {
				sb.append("v ").append(vertices[k]).append(" ").append(vertices[k + 1]).append(" ").append(vertices[k + 2]).append("\n");
			}

			for (int k = 0; k < normals.length; k += 3) {
				sb.append("vn ").append(-normals[k]).append(" ").append(-normals[k + 1]).append(" ").append(-normals[k + 2]).append("\n");
			}

			for (int k = 0; k < texCoords.length; k += 2) {
				sb.append("vt ").append(texCoords[k]).append(" ").append(texCoords[k + 1]).append("\n");
			}

			if (hasMaterial)
				sb.append("usemtl ").append(id).append("\n");
			for (int k = 0; k < indices.length; k += 3) {
				sb.append("f ")
						.append(numVertices + indices[k + 0]).append("/").append(1).append("/").append(numVertices + indices[k + 0]).append(" ")
						.append(numVertices + indices[k + 1]).append("/").append(1).append("/").append(numVertices + indices[k + 1]).append(" ")
						.append(numVertices + indices[k + 2]).append("/").append(1).append("/").append(numVertices + indices[k + 2]).append("\n");
			}

			writer.append(sb.toString());
			numVertices += vertices.length / 3;
		}
	}
}
