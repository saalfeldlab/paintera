package org.janelia.saalfeldlab.paintera.meshes.io.obj;

import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import javafx.scene.shape.TriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;

public class ObjWriter implements TriangleMeshWriter {

	@Override
	public void writeMesh(
			final TriangleMesh mesh,
			final Path path) throws IOException {

		final float[] texCoords = new float[mesh.getTexCoords().size()];
		final float[] vertices = new float[mesh.getPoints().size()];
		final float[] normals = new float[mesh.getNormals().size()];
		final int[] faces = new int[mesh.getFaces().size()];
		mesh.getTexCoords().toArray(texCoords);
		mesh.getPoints().toArray(vertices);
		mesh.getNormals().toArray(normals);
		mesh.getFaces().toArray(faces);

		final Obj obj = Objs.createFromIndexedTriangleData(
				IntBuffer.wrap(faces),
				FloatBuffer.wrap(vertices),
				FloatBuffer.wrap(texCoords),
				FloatBuffer.wrap(normals));

		try (final OutputStream fos = new FileOutputStream(path.toFile())) {
			de.javagl.obj.ObjWriter.write(obj, fos);
		}
	}
}
