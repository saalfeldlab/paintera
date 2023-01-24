package org.janelia.saalfeldlab.paintera.meshes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

public class MeshExporterBinary<T> extends MeshExporter<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Override
	protected void save(final String path, final String id, final float[] vertices, final float[] normals, final boolean append) throws IOException {

		save(path + ".vertices", vertices, append);
		save(path + ".normals", normals, append);
	}

	private void save(final String path, final float[] info, final boolean append) throws IOException {

		try (final DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path, append)))) {
			for (int i = 0; i < info.length; i++) {
				stream.writeFloat(info[i]);
			}
		}
	}
}
