package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.HashMap;

import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculate and assign surface normals of a triangle mesh.
 *
 * @author Philipp Hanslovsky
 */
public class AverageNormals
{
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger( AverageNormals.class );

	public static void averagedNormals( final float[] triangles, final float[] normals ) {

		final HashMap< HashWrapper< float[] >, double[] > averages = new HashMap<>();

		assert triangles.length % 9 == 0;
		assert triangles.length == normals.length;

		final double[] diff1 = new double[ 3 ];
		final double[] diff2 = new double[ 3 ];
		final double[] normal = new double[ 4 ];
		normal[ 3 ] = 1.0;
		for ( int triangle = 0; triangle < triangles.length; triangle += 9 )
		{

			final double v11 = triangles[ triangle + 0 ], v12 = triangles[ triangle + 1 ], v13 = triangles[ triangle + 2 ];
			final double v21 = triangles[ triangle + 3 ], v22 = triangles[ triangle + 4 ], v23 = triangles[ triangle + 5 ];
			final double v31 = triangles[ triangle + 6 ], v32 = triangles[ triangle + 7 ], v33 = triangles[ triangle + 8 ];

			diff1[ 0 ] = v21 - v11;
			diff1[ 1 ] = v22 - v12;
			diff1[ 2 ] = v23 - v13;

			diff2[ 0 ] = v31 - v11;
			diff2[ 1 ] = v32 - v12;
			diff2[ 2 ] = v33 - v13;

			double n1 = diff1[1] * diff2[2] - diff1[2] * diff2[1];
			double n2 = diff1[2] * diff2[0] - diff1[0] * diff2[2];
			double n3 = diff1[0] * diff2[1] - diff1[1] * diff2[0];
			final double norm = Math.sqrt( n1 * n1 + n2 * n2 + n3 * n3 );
			n1 /= norm;
			n2 /= norm;
			n3 /= norm;

			final float[][] keys = new float[][] {
				{ ( float ) v11, ( float ) v12, ( float ) v13 },
				{ ( float ) v21, ( float ) v22, ( float ) v23 },
				{ ( float ) v31, ( float ) v32, ( float ) v33 }
			};

			for ( final float[] key : keys ) {
				final HashWrapper< float[] > wrappedKey = new HashWrapper<>( key, Arrays::hashCode, Arrays::equals );
				if ( averages.containsKey( wrappedKey ) ) {
					final double[] n = averages.get( wrappedKey );
					n[ 0 ] += n1;
					n[ 1 ] += n2;
					n[ 2 ] += n3;
					n[ 3 ] += 1.0;
			}
				else
					averages.put( wrappedKey, new double[] { n1, n2, n3, 1.0 } );
			}
		}

		for ( final double[] n : averages.values() ) {
			final double sum = n[3];
			n[ 0 ] /= sum;
			n[ 1 ] /= sum;
			n[ 2 ] /= sum;
		}

		for ( int vertex = 0; vertex < triangles.length; vertex += 3 )
		{

			final HashWrapper< float[] > key = new HashWrapper< >(
					new float[] { triangles[ vertex + 0 ], triangles[ vertex + 1 ], triangles[ vertex + 2 ] },
					Arrays::hashCode,
					Arrays::equals );

			final double[] n = averages.get( key );

			normals[ vertex + 0 ] = ( float ) n[ 0 ];
			normals[ vertex + 1 ] = ( float ) n[ 1 ];
			normals[ vertex + 2 ] = ( float ) n[ 2 ];
		}
	}
}
