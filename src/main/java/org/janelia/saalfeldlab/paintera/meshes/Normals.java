package org.janelia.saalfeldlab.paintera.meshes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculate and assign orthogonal surface normals to of a triangle mesh.
 *
 * @author Philipp Hanslovsky
 * @author Stephan Saalfeld
 */
public class Normals
{
	/**
	 * logger
	 */
	private static final Logger LOG = LoggerFactory.getLogger(Normals.class);

	public static void normals(final float[] triangles, final float[] normals)
	{

		LOG.debug("Calculating normals for {} triangles and {} normals", triangles.length, normals.length);

		assert triangles.length % 9 == 0;
		assert triangles.length == normals.length;

		final double[] diff1 = new double[3];
		final double[] diff2 = new double[3];
		for (int triangle = 0; triangle < triangles.length; triangle += 9)
		{

			final double v11 = triangles[triangle + 0], v12 = triangles[triangle + 1], v13 = triangles[triangle + 2];
			final double v21 = triangles[triangle + 3], v22 = triangles[triangle + 4], v23 = triangles[triangle + 5];
			final double v31 = triangles[triangle + 6], v32 = triangles[triangle + 7], v33 = triangles[triangle + 8];

			diff1[0] = v21 - v11;
			diff1[1] = v22 - v12;
			diff1[2] = v23 - v13;

			diff2[0] = v31 - v11;
			diff2[1] = v32 - v12;
			diff2[2] = v33 - v13;

			double       n1   = diff1[1] * diff2[2] - diff1[2] * diff2[1];
			double       n2   = diff1[2] * diff2[0] - diff1[0] * diff2[2];
			double       n3   = diff1[0] * diff2[1] - diff1[1] * diff2[0];
			final double norm = Math.sqrt(n1 * n1 + n2 * n2 + n3 * n3);
			n1 /= norm;
			n2 /= norm;
			n3 /= norm;

			normals[triangle + 0] = (float) n1;
			normals[triangle + 1] = (float) n2;
			normals[triangle + 2] = (float) n3;

			normals[triangle + 3] = (float) n1;
			normals[triangle + 4] = (float) n2;
			normals[triangle + 5] = (float) n3;

			normals[triangle + 6] = (float) n1;
			normals[triangle + 7] = (float) n2;
			normals[triangle + 8] = (float) n3;
		}
	}
}
