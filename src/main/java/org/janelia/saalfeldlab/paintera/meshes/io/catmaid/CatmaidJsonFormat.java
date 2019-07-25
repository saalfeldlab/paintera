package org.janelia.saalfeldlab.paintera.meshes.io.catmaid;

import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormat;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshLoader;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshWriter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import java.util.Collections;
import java.util.Set;

@Plugin(type = TriangleMeshFormat.class)
public class CatmaidJsonFormat implements TriangleMeshFormat, SciJavaPlugin {

	@Override
	public String formatName() {
		return "CATMAID JSON";
	}

	@Override
	public Set<String> knownExtensions() {
		return Collections.singleton("json");
	}

	@Override
	public TriangleMeshWriter getWriter() {
		return new CatmaidJsonWriter();
	}

	@Override
	public TriangleMeshLoader getLoader() {
		return new CatmaidJsonLoader();
	}

}
