package org.janelia.saalfeldlab.paintera.meshes.io;

import javafx.scene.shape.TriangleMesh;
import org.scijava.plugin.SciJavaPlugin;

import java.io.IOException;
import java.nio.file.Path;

public interface TriangleMeshWriter extends SciJavaPlugin {

	void writeMesh(TriangleMesh mesh, Path path) throws IOException;
}
