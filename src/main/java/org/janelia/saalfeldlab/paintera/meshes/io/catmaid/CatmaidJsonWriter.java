package org.janelia.saalfeldlab.paintera.meshes.io.catmaid;

import javafx.scene.shape.TriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshWriter;

import java.io.IOException;
import java.nio.file.Path;

public class CatmaidJsonWriter implements TriangleMeshWriter {


	@Override
	public void writeMesh(
			final TriangleMesh mesh,
			final Path path) throws IOException {

		throw new UnsupportedOperationException("Not implemented yet!");
	}
}
