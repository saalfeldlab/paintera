package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.Arrays;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.viewer3d.util.HashWrapper;
import bdv.bigcat.viewer.viewer3d.util.SimpleMesh;
import gnu.trove.list.array.TFloatArrayList;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation;
import net.imglib2.util.Intervals;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;

/**
 * This class implements the marching cubes algorithm. Based on
 * http://paulbourke.net/geometry/polygonise/
 *
 * @author Vanessa Leite
 * @author Philipp Hanslovsky
 * @param <T>
 */
public class MarchingCubes< T >
{
	/** logger */
	private static final Logger LOGGER = LoggerFactory.getLogger( MarchingCubes.class );

	/** the mesh that represents the surface. */
	private final SimpleMesh mesh;

	private final RandomAccessible< T > input;

	private final Interval interval;

	private final AffineTransform3D transform;

	/** size of the cube */
	private final int[] cubeSize;

	/**
	 * Enum of the available criteria. These criteria are used to evaluate if
	 * the vertex is part of the mesh or not.
	 */
	public enum ForegroundCriterion
	{
		EQUAL,
		GREATER_EQUAL
	}

	/**
	 * Initialize the class parameters with default values
	 */
	public MarchingCubes( final RandomAccessible< T > input, final Interval interval, final AffineTransform3D transform, final int[] cubeSize )
	{
		this.mesh = new SimpleMesh();
		this.input = input;
		this.interval = interval;
		this.cubeSize = cubeSize;
		this.transform = transform;
	}

	/**
	 * Creates the mesh using the information directly from the RAI structure
	 *
	 * @param input
	 *            RAI with the label (segmentation) information
	 * @param cubeSize
	 *            size of the cube to walk in the volume
	 * @param nextValuesVertex
	 *            generic interface to access the information on RAI
	 * @return SimpleMesh, basically an array with the vertices
	 */
	public float[] generateMesh( final ForegroundCheck< T > foregroundCheck )
	{
		final long[] stride = Arrays.stream( cubeSize ).mapToLong( i -> i ).toArray();
		final FinalInterval expandedInterval = Intervals.expand( interval, Arrays.stream( stride ).map( s -> s + 1 ).toArray() );
		final SubsampleIntervalView< T > subsampled = Views.subsample( Views.interval( input, expandedInterval ), stride );
		final Cursor< T >[] cursors = new Cursor[ 8 ];
		cursors[ 0 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 0, 0 ), subsampled ) ).localizingCursor();
		cursors[ 1 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 0, 0 ), subsampled ) ).cursor();
		cursors[ 2 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 1, 0 ), subsampled ) ).cursor();
		cursors[ 3 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 1, 0 ), subsampled ) ).cursor();
		cursors[ 4 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 0, 1 ), subsampled ) ).cursor();
		cursors[ 5 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 0, 1 ), subsampled ) ).cursor();
		cursors[ 6 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 1, 1 ), subsampled ) ).cursor();
		cursors[ 7 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 1, 1 ), subsampled ) ).cursor();
		final Translation translation = new Translation( Arrays.stream( Intervals.minAsLongArray( expandedInterval ) ).mapToDouble( l -> l ).toArray() );

		final TFloatArrayList vertices = new TFloatArrayList();
		final RealPoint p = new RealPoint( interval.numDimensions() );

		while ( cursors[ 0 ].hasNext() )
		{

			// Remap the vertices of the cube (8 positions) obtained from a RAI
			// to match the expected order for this implementation
			// @formatter:off
			// the values from the cube are given first in z, then y, then x
			// this way, the vertex_values (from getCube) are positioned in this
			// way:
			//
			//
			//  4------6
			// /|     /|
			// 0-----2 |
			// |5----|-7
			// |/    |/
			// 1-----3
			//
			// this algorithm (based on
			// http://paulbourke.net/geometry/polygonise/)
			// considers the vertices of the cube in this order:
			//
			//  4------5
			// /|     /|
			// 7-----6 |
			// |0----|-1
			// |/    |/
			// 3-----2
			//
			// This way, we need to remap the cube vertices:
			// @formatter:on
			final int vertexValues =
					( foregroundCheck.test( cursors[ 5 ].next() ) & 1 ) << 0 |
							( foregroundCheck.test( cursors[ 7 ].next() ) & 1 ) << 1 |
							( foregroundCheck.test( cursors[ 3 ].next() ) & 1 ) << 2 |
							( foregroundCheck.test( cursors[ 1 ].next() ) & 1 ) << 3 |
							( foregroundCheck.test( cursors[ 4 ].next() ) & 1 ) << 4 |
							( foregroundCheck.test( cursors[ 6 ].next() ) & 1 ) << 5 |
							( foregroundCheck.test( cursors[ 2 ].next() ) & 1 ) << 6 |
							( foregroundCheck.test( cursors[ 0 ].next() ) & 1 ) << 7;
//			}

//			p.setPosition( cursors[ 0 ] );
//			transform.apply( p, p );

			triangulation(
					vertexValues,
					cursors[ 0 ].getLongPosition( 0 ),
					cursors[ 0 ].getLongPosition( 1 ),
					cursors[ 0 ].getLongPosition( 2 ),
					vertices );

		}

		final float[] vertexArray = new float[ vertices.size() ];

		for ( int i = 0; i < vertexArray.length; i += 3 )
		{
			p.setPosition( vertices.get( i + 0 ), 0 );
			p.setPosition( vertices.get( i + 1 ), 1 );
			p.setPosition( vertices.get( i + 2 ), 2 );
			translation.apply( p, p );
			transform.apply( p, p );
			vertexArray[ i + 0 ] = p.getFloatPosition( 0 );
			vertexArray[ i + 1 ] = p.getFloatPosition( 1 );
			vertexArray[ i + 2 ] = p.getFloatPosition( 2 );
		}

		return vertexArray;
	}

	/**
	 * Given the values of the vertices (in a specific order) identifies which
	 * of them are inside the mesh. For each one of the points that form the
	 * mesh, a triangulation is calculated.
	 *
	 * @param vertexValues
	 *            the values of the eight vertices of the cube
	 * @param cursorX
	 *            position on x
	 * @param cursorY
	 *            position on y
	 * @param cursorZ
	 *            position on z
	 */
	private void triangulation(
			final int vertexValues,
			final long cursorX,
			final long cursorY,
			final long cursorZ,
			final TFloatArrayList vertices )
	{
		// @formatter:off
		// this algorithm (based on http://paulbourke.net/geometry/polygonise/)
		// considers the vertices of the cube in this order:
		//
		//  4------5
		// /|     /|
		// 7-----6 |
		// |0----|-1
		// |/    |/
		// 3-----2
		// @formatter:on

		// Calculate table lookup index from those vertices which
		// are below the isolevel.
		final int tableIndex = vertexValues;
//		for ( int i = 0; i < 8; i++ )
//			if ( vertexValues[ i ] == foregroundValue )
//				tableIndex |= 1 << i;

		// edge indexes:
		// @formatter:off
		//        4-----*4*----5
		//       /|           /|
		//      /*8*         / |
		//    *7* |        *5* |
		//    /   |        /  *9*
		//   7-----*6*----6    |
		//   |    0----*0*-----1
		// *11*  /       *10* /
		//   |  /         | *1*
		//   |*3*         | /
		//   |/           |/
		//   3-----*2*----2
		// @formatter: on

		// Now create a triangulation of the isosurface in this cell.
		final float[][] interpolationPoints = new float[ 12 ][];
		if (MarchingCubesTables.MC_EDGE_TABLE[tableIndex] != 0)
		{
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 1) != 0)
				interpolationPoints[ 0 ] = calculateIntersection(cursorX, cursorY, cursorZ, 0);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 2) != 0)
				interpolationPoints[ 1 ] = calculateIntersection(cursorX, cursorY, cursorZ, 1);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 4) != 0)
				interpolationPoints[ 2 ] = calculateIntersection(cursorX, cursorY, cursorZ, 2);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 8) != 0)
				interpolationPoints[ 3 ] = calculateIntersection(cursorX, cursorY, cursorZ, 3);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 16) != 0)
				interpolationPoints[ 4 ] = calculateIntersection(cursorX, cursorY, cursorZ, 4);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 32) != 0)
				interpolationPoints[ 5 ] = calculateIntersection(cursorX, cursorY, cursorZ, 5);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 64) != 0)
				interpolationPoints[ 6 ] = calculateIntersection(cursorX, cursorY, cursorZ, 6);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 128) != 0)
				interpolationPoints[ 7 ] = calculateIntersection(cursorX, cursorY, cursorZ, 7);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 256) != 0)
				interpolationPoints[ 8 ] = calculateIntersection(cursorX, cursorY, cursorZ, 8);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 512) != 0)
				interpolationPoints[ 9 ] = calculateIntersection(cursorX, cursorY, cursorZ, 9);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 1024) != 0)
				interpolationPoints[ 10 ] = calculateIntersection(cursorX, cursorY, cursorZ, 10);

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 2048) != 0)
				interpolationPoints[ 11 ] = calculateIntersection(cursorX, cursorY, cursorZ, 11);

			for ( int i = 0; MarchingCubesTables.MC_TRI_TABLE[tableIndex][i] != MarchingCubesTables.Invalid; i += 3 )
			{

				final float[] v1 = interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i ] ];
				final float[] v2 = interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 1 ] ];
				final float[] v3 = interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 2 ] ];

				vertices.add( v1[ 0 ] );
				vertices.add( v1[ 1 ] );
				vertices.add( v1[ 2 ] );

				vertices.add( v2[ 0 ] );
				vertices.add( v2[ 1 ] );
				vertices.add( v2[ 2 ] );

				vertices.add( v3[ 0 ] );
				vertices.add( v3[ 1 ] );
				vertices.add( v3[ 2 ] );

			}
		}
	}

	/**
	 * Given the position on the volume and the intersected edge, calculates
	 * the intersection point. The intersection point is going to be in the middle
	 * of the intersected edge. In this method also the offset is applied.
	 * @param cursorX
	 *            position on x
	 * @param cursorY
	 *            position on y
	 * @param cursorZ
	 *            position on z
	 * @param intersectedEdge
	 *            intersected edge
	 * @return
	 *            intersected point in world coordinates
	 */
	private float[] calculateIntersection( final long cursorX, final long cursorY, final long cursorZ, final int intersectedEdge )
	{
		LOGGER.trace("cursor position: " + cursorX + " " + cursorY + " " + cursorZ);
		long v1x = cursorX, v1y = cursorY, v1z = cursorZ;
		long v2x = cursorX, v2y = cursorY, v2z = cursorZ;

		switch (intersectedEdge)
		{
		case 0:
			// edge 0 -> from p0 to p1
			// p0 = { 1 + cursorX, 0 + cursorY, 1 + cursorZ }
			v1x += 1;
			v1z += 1;

			// p1 = { 1 + cursorX, 1 + cursorY, 1 + cursorZ }
			v2x += 1;
			v2y += 1;
			v2z += 1;

			break;
		case 1:
			// edge 0 -> from p1 to p2
			// p1 = { 1 + cursorX, 1 + cursorY, 1 + cursorZ }
			v1x += 1;
			v1y += 1;
			v1z += 1;

			// p2 = { 1 + cursorX, 1 + cursorY, 0 + cursorZ }
			v2x += 1;
			v2y += 1;

			break;
		case 2:
			// edge 2 -> from p2 to p3
			// p2 = { 1 + cursorX, 1 + cursorY, 0 + cursorZ }
			v1x += 1;
			v1y += 1;

			// p3 = { 1 + cursorX, 0 + cursorY, 0 + cursorZ }
			v2x += 1;

			break;
		case 3:
			// edge 0 -> from p3 to p0
			// p3 = { 1 + cursorX, 0 + cursorY, 0 + cursorZ }
			v1x += 1;

			// p0 = { 1 + cursorX, 0 + cursorY, 1 + cursorZ }
			v2x += 1;
			v2z += 1;

			break;
		case 4:
			// edge 4 -> from p4 to p5
			// p4 = { 0 + cursorX, 0 + cursorY, 1 + cursorZ }
			v1z += 1;

			// p5 = { 0 + cursorX, 1 + cursorY, 1 + cursorZ }
			v2y += 1;
			v2z += 1;

			break;
		case 5:
			// edge 5 -> from p5 to p6
			// p5 = { 0 + cursorX, 1 + cursorY, 1 + cursorZ }
			v1y += 1;
			v1z += 1;

			// p6 = { 0 + cursorX, 1 + cursorY, 0 + cursorZ }
			v2y += 1;

			break;
		case 6:
			// edge 6 -> from p6 to p7
			// p6 = { 0 + cursorX, 1 + cursorY, 0 + cursorZ }
			v1y += 1;

			// p7 = { 0 + cursorX, 0 + cursorY, 0 + cursorZ } -> the actual point

			break;
		case 7:
			// edge 7 -> from p7 to p4
			// p7 = { 0 + cursorX, 0 + cursorY, 0 + cursorZ } -> the actual point
			// p4 = { 0 + cursorX, 0 + cursorY, 1 + cursorZ }
			v2z += 1;

			break;
		case 8:
			// edge 8 -> from p0 to p4
			// p0 = { 1 + cursorX, 0 + cursorY, 1 + cursorZ }
			v1x += 1;
			v1z += 1;

			// p4 = { 0 + cursorX, 0 + cursorY, 1 + cursorZ }
			v2z += 1;

			break;
		case 9:
			// edge 9 -> from p1 to p5
			// p1 = { 1 + cursorX, 1 + cursorY, 1 + cursorZ }
			v1x += 1;
			v1y += 1;
			v1z += 1;

			// p5 = { 0 + cursorX, 1 + cursorY, 1 + cursorZ }
			v2y += 1;
			v2z += 1;

			break;
		case 10:
			// edge 10 -> from p2 to p6
			// p2 = { 1 + cursorX, 1 + cursorY, 0 + cursorZ }
			v1x += 1;
			v1y += 1;

			// p6 = { 0 + cursorX, 1 + cursorY, 0 + cursorZ }
			v2y += 1;

			break;
		case 11:
			// edge 11 -> from p3 to p7
			// p3 = { 1 + cursorX, 0 + cursorY, 0 + cursorZ }
			v1x += 1;

			// p7 = { 0 + cursorX, 0 + cursorY, 0 + cursorZ } -> the actual point

			break;
		}

//		v1x = v1x * cubeSize[ 0 ];
//		v1y = v1y * cubeSize[ 1 ];
//		v1z = v1z * cubeSize[ 2 ];
//
//		v2x = v2x * cubeSize[ 0 ];
//		v2y = v2y * cubeSize[ 1 ];
//		v2z = v2z * cubeSize[ 2 ];
//
//		if (LOGGER.isTraceEnabled())
//		{
//			LOGGER.trace( "v1: " + v1x + " " + v1y + " " + v1z );
//			LOGGER.trace( "v2: " + v2x + " " + v2y + " " + v2z );
//		}

//		float diffX = v2x - v1x;
//		float diffY = v2y - v1y;
//		float diffZ = v2z - v1z;
//
//		diffX *= 0.5;
//		diffY *= 0.5;
//		diffZ *= 0.5;
//
//		diffX += v1x;
//		diffY += v1y;
//		diffZ += v1z;

		return new float[] { ( float ) ( 0.5 * cubeSize[ 0 ] * ( v1x + v2x ) ),  ( float ) ( 0.5 * cubeSize[ 1 ] * ( v1y + v2y ) ), ( float ) ( 0.5 * cubeSize[ 2 ] * ( v1z + v2z ) ) };
	}

	public static void surfaceNormals( final float[] triangles, final float[] normals ) {

		assert triangles.length % 9 == 0;
		assert triangles.length == normals.length;

		final double[] diff1 = new double[ 3 ];
		final double[] diff2 = new double[ 3 ];
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

			normals[ triangle + 0 ] = (float)n1;
			normals[ triangle + 1 ] = (float)n2;
			normals[ triangle + 2 ] = (float)n3;

			normals[ triangle + 3 ] = (float)n1;
			normals[ triangle + 4 ] = (float)n2;
			normals[ triangle + 5 ] = (float)n3;

			normals[ triangle + 6 ] = (float)n1;
			normals[ triangle + 7 ] = (float)n2;
			normals[ triangle + 8 ] = (float)n3;

		}
	}

	public static void averagedSurfaceNormals( final float[] triangles, final float[] normals ) {

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
