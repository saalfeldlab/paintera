package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.list.array.TFloatArrayList;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.view.SubsampleView;
import net.imglib2.view.Views;

/**
 * This class implements the marching cubes algorithm. Based on
 * http://paulbourke.net/geometry/polygonise/
 *
 * @author vleite
 * @param <T>
 */
public class MarchingCubes< T >
{
	/** logger */
	private static final Logger LOGGER = LoggerFactory.getLogger( MarchingCubes.class );

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
		System.out.println( "MESH GENERATING? " + Arrays.toString( Intervals.minAsLongArray( interval ) ) + " " + Arrays.toString( Intervals.maxAsLongArray( interval ) ) );
		final long[] stride = Arrays.stream( cubeSize ).mapToLong( i -> i ).toArray();
		final FinalInterval expandedInterval = Intervals.expand( interval, stride );
		final SubsampleView< T > subsampled = Views.subsample( Views.interval( input, expandedInterval ), stride );
		final Cursor< T >[] cursors = new Cursor[ 8 ];
		final FinalInterval zeroMinInterval = new FinalInterval( Arrays.stream( Intervals.dimensionsAsLongArray( expandedInterval ) ).map( l -> l - 1 ).toArray() );
		cursors[ 0 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 0, 0 ), zeroMinInterval ) ).localizingCursor();
		cursors[ 1 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 0, 0 ), zeroMinInterval ) ).cursor();
		cursors[ 2 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 1, 0 ), zeroMinInterval ) ).cursor();
		cursors[ 3 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 1, 0 ), zeroMinInterval ) ).cursor();
		cursors[ 4 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 0, 1 ), zeroMinInterval ) ).cursor();
		cursors[ 5 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 0, 1 ), zeroMinInterval ) ).cursor();
		cursors[ 6 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 0, 1, 1 ), zeroMinInterval ) ).cursor();
		cursors[ 7 ] = Views.flatIterable( Views.interval( Views.offset( subsampled, 1, 1, 1 ), zeroMinInterval ) ).cursor();

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
//			System.out.println( new Point( cursors[ 0 ] ) + " " + cursors[ 0 ].get() + " " + vertexValues );
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

		for ( int i = 0; i < vertices.size(); i += 3 )
		{
			p.setPosition( vertices.get( i + 0 ), 0 );
			p.setPosition( vertices.get( i + 1 ), 1 );
			p.setPosition( vertices.get( i + 2 ), 2 );
			transform.apply( p, p );
			vertices.set( i + 0, p.getFloatPosition( 0 ) );
			vertices.set( i + 1, p.getFloatPosition( 1 ) );
			vertices.set( i + 2, p.getFloatPosition( 2 ) );
		}

//		System.out.println( vertices.size() );
		if ( vertices.size() > 0 )
			System.out.println( vertices.get( 0 ) + " " + vertices.get( 1 ) + " " + vertices.get( 2 ) + " " + Arrays.toString( Intervals.minAsLongArray( interval ) ) + " " + transform );
		return vertices.toArray();
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
	private void triangulation( final int vertexValues, final long cursorX, final long cursorY, final long cursorZ, final TFloatArrayList vertices )
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
				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i ] ][ 0 ] );
				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i ] ][ 1 ] );
				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i ] ][ 2 ] );

				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 1 ] ][ 0 ] );
				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 1 ] ][ 1 ] );
				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 1 ] ][ 2 ] );

				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 2 ] ][ 0 ] );
				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 2 ] ][ 1 ] );
				vertices.add( interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 2 ] ][ 2 ] );
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

		v1x = ( v1x + interval.min( 0 ) / cubeSize[ 0 ] ) * cubeSize[ 0 ];
		v1y = ( v1y + interval.min( 1 ) / cubeSize[ 1 ] ) * cubeSize[ 1 ];
		v1z = ( v1z + interval.min( 2 ) / cubeSize[ 2 ] ) * cubeSize[ 2 ];

		v2x = ( v2x + interval.min( 0 ) / cubeSize[ 0 ] ) * cubeSize[ 0 ];
		v2y = ( v2y + interval.min( 1 ) / cubeSize[ 1 ] ) * cubeSize[ 1 ];
		v2z = ( v2z + interval.min( 2 ) / cubeSize[ 2 ] ) * cubeSize[ 2 ];

		if (LOGGER.isTraceEnabled())
		{
			LOGGER.trace( "v1: " + v1x + " " + v1y + " " + v1z );
			LOGGER.trace( "v2: " + v2x + " " + v2y + " " + v2z );
		}

		float diffX = v2x - v1x;
		float diffY = v2y - v1y;
		float diffZ = v2z - v1z;

		diffX *= 0.5;
		diffY *= 0.5;
		diffZ *= 0.5;

		diffX += v1x;
		diffY += v1y;
		diffZ += v1z;

		return new float[] { diffX, diffY, diffZ };
	}
}
