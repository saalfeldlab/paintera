package org.janelia.saalfeldlab.paintera.meshes;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshExporterBinary<T> extends MeshExporter<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Override
	protected void save(final String path, final String id, final float[] vertices, final float[] normals, final
	boolean append)
	{
		save(path + ".vertices", vertices, append);
		save(path + ".normals", normals, append);
	}

	private void save(final String path, final float[] info, final boolean append)
	{
		try
		{
			final DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(
					path,
					append
			)));
			try
			{
				for (int i = 0; i < info.length; i++)
				{
					stream.writeFloat(info[i]);
				}
				stream.close();
			} catch (final IOException e)
			{
				LOG.warn("Couldn't write data to the file {}: {}", path, e.getMessage());
			}
		} catch (final FileNotFoundException e)
		{
			LOG.warn("Couldn't find file {}: {}", path, e.getMessage());
		}
	}

}
