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
	protected void save(final String path, final String id, final float[] vertices, final float[] normals, int[] indices, final boolean append) throws IOException {

		save(path + ".vertices", vertices);
		save(path + ".normals", normals);
		save(path + ".indices", indices);
	}

	private void save(final String path, final float[] info) throws IOException {

		try (final DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
			for (float v : info)
				stream.writeFloat(v);
		}
	}

	private void save(final String path, final int[] info) throws IOException {

		try (final DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
			for (int j : info)
				stream.writeInt(j);
		}
	}
}
