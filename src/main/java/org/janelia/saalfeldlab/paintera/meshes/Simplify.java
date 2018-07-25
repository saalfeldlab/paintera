package org.janelia.saalfeldlab.paintera.meshes;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculate and assign surface normals of a triangle mesh.
 *
 * @author Philipp Hanslovsky
 */
public class Simplify
{
	/**
	 * logger
	 */
	private static final Logger LOG = LoggerFactory.getLogger(Simplify.class);

	public static Pair<float[], float[]> simplify(final float[] vertices, final float[] normals)
	{

		LOG.debug("Simplifying {} vertices and {} normals", vertices.length, normals.length);
		return new ValuePair<>(vertices, normals);
	}
}
