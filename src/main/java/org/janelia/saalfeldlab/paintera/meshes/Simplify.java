package org.janelia.saalfeldlab.paintera.meshes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

/**
 * Calculate and assign surface normals of a triangle mesh.
 *
 * @author Philipp Hanslovsky
 */
public class Simplify
{
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger( Simplify.class );

	public static Pair< float[], float[] > simplify( final float[] vertices, final float[] normals ) {

		return new ValuePair<>( vertices, normals );
	}
}
