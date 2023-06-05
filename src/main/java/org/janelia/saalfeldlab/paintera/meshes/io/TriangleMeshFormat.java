package org.janelia.saalfeldlab.paintera.meshes.io;

import org.scijava.plugin.SciJavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TriangleMeshFormat extends SciJavaPlugin {

	String formatName();

	Set<String> knownExtensions();

	TriangleMeshWriter getWriter();

	TriangleMeshLoader getLoader();

	static List<TriangleMeshFormat> availableFormats(final TriangleMeshFormatService triangleMeshFormat) {

		return triangleMeshFormat.getMeshFormats();
	}

	static Map<String, List<TriangleMeshFormat>> extensionsToFormatMapping(final TriangleMeshFormatService triangleMeshFormat) {

		return triangleMeshFormat.getExtensionToFormatMapping();
	}
}
