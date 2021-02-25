package org.janelia.saalfeldlab.paintera.meshes.io;

import javafx.scene.shape.TriangleMesh;

import java.io.IOException;
import java.nio.file.Path;

public interface TriangleMeshLoader {

  TriangleMesh loadMesh(Path path) throws IOException;

}
