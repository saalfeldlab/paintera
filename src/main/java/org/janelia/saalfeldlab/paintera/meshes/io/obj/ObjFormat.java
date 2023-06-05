package org.janelia.saalfeldlab.paintera.meshes.io.obj;

import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormat;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshLoader;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshWriter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import java.util.Collections;
import java.util.Set;

@Plugin(type = TriangleMeshFormat.class)
public class ObjFormat implements TriangleMeshFormat, SciJavaPlugin {

	@Override
	public String formatName() {

		return "OBJ";
	}

	@Override
	public Set<String> knownExtensions() {

		return Collections.singleton("obj");
	}

	@Override
	public TriangleMeshWriter getWriter() {

		return new ObjWriter();
	}

	@Override
	public TriangleMeshLoader getLoader() {

		return new ObjLoader();
	}

}
