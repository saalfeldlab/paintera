package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.HashMap;

import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Average precalculated vertex normals of a triangle mesh, i.e. make the mesh appear smooth.
 *
 * @author Philipp Hanslovsky
 * @author Stephan Saalfeld
 */
public class AverageNormals
{
	/**
	 * logger
	 */
	private static final Logger LOG = LoggerFactory.getLogger(AverageNormals.class);

	public static void averagedNormals(final float[] triangles, final float[] normals)
	{

		LOG.debug("Averaging normals for {} triangles and {} normals", triangles.length, normals.length);

		final HashMap<HashWrapper<float[]>, double[]> averages = new HashMap<>();

		assert triangles.length % 9 == 0;
		assert triangles.length == normals.length;

		final double[] normal = new double[4];
		normal[3] = 1.0;
		final double[][] n = new double[3][3];
		for (int triangle = 0; triangle < triangles.length; triangle += 9)
		{
			final float v11 = triangles[triangle + 0], v12 = triangles[triangle + 1], v13 = triangles[triangle + 2];
			final float v21 = triangles[triangle + 3], v22 = triangles[triangle + 4], v23 = triangles[triangle + 5];
			final float v31 = triangles[triangle + 6], v32 = triangles[triangle + 7], v33 = triangles[triangle + 8];

			n[0][0] = normals[triangle + 0];
			n[0][1] = normals[triangle + 1];
			n[0][2] = normals[triangle + 2];

			n[1][0] = normals[triangle + 3];
			n[1][1] = normals[triangle + 4];
			n[1][2] = normals[triangle + 5];

			n[2][0] = normals[triangle + 6];
			n[2][1] = normals[triangle + 7];
			n[2][2] = normals[triangle + 8];

			final float[][] keys = new float[][] {
					{v11, v12, v13},
					{v21, v22, v23},
					{v31, v32, v33}
			};

			for (int i = 0; i < keys.length; ++i)
			{
				final HashWrapper<float[]> wrappedKey = new HashWrapper<>(keys[i], Arrays::hashCode, Arrays::equals);
				if (averages.containsKey(wrappedKey))
				{
					final double[] nn = averages.get(wrappedKey);
					nn[0] += n[i][0];
					nn[1] += n[i][1];
					nn[2] += n[i][2];
					nn[3] += 1.0;
				}
				else
					averages.put(wrappedKey, new double[] {
							n[i][0],
							n[i][1],
							n[i][2], 1.0});
			}
		}

		for (final double[] nn : averages.values())
		{
			final double sum = nn[3];
			nn[0] /= sum;
			nn[1] /= sum;
			nn[2] /= sum;
		}

		for (int vertex = 0; vertex < triangles.length; vertex += 3)
		{
			final HashWrapper<float[]> key = new HashWrapper<>(
					new float[] {triangles[vertex + 0], triangles[vertex + 1], triangles[vertex + 2]},
					Arrays::hashCode,
					Arrays::equals
			);

			final double[] nn = averages.get(key);

			normals[vertex + 0] = (float) nn[0];
			normals[vertex + 1] = (float) nn[1];
			normals[vertex + 2] = (float) nn[2];
		}
	}
}
