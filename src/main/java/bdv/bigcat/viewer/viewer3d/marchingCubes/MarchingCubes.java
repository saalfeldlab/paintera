package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.viewer3d.util.SimpleMesh;
import bdv.labels.labelset.LabelMultisetType;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;

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

	/** the mesh that represents the surface. */
	private final SimpleMesh mesh;

	private final RandomAccessibleInterval< T > input;

	/** No. of cells in x, y and z directions. */
	private final long nCellsX, nCellsY, nCellsZ;

	/** The value (id) that we will use to create the mesh. */
	private final long foregroundValue;

	/** Indicates which criterion is going to be applied */
	private final ForegroundCriterion criteria;

	/** size of the cube */
	private final int[] cubeSize;

	/** list of calculated vertices */
	private final ArrayList< float[] > vertices;

	private final long[] offset;

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
	public MarchingCubes( final RandomAccessibleInterval< T > input, final int[] cubeSize, final long foregroundValue, final long[] offset )
	{
		this.mesh = new SimpleMesh();
		this.input = input;
		this.foregroundValue = foregroundValue;
		this.criteria = ForegroundCriterion.EQUAL;
		this.cubeSize = cubeSize;
		this.vertices = new ArrayList<>();
		this.offset = offset;

		final long[] volDim = Intervals.dimensionsAsLongArray( input );

		long nCellsX = ( long ) Math.ceil( ( volDim[ 0 ] + 2 ) / cubeSize[ 0 ] );
		long nCellsY = ( long ) Math.ceil( ( volDim[ 1 ] + 2 ) / cubeSize[ 1 ] );
		long nCellsZ = ( long ) Math.ceil( ( volDim[ 2 ] + 2 ) / cubeSize[ 2 ] );

		if ( ( volDim[ 0 ] + 2 ) % cubeSize[ 0 ] == 0 )
			nCellsX--;
		if ( ( volDim[ 1 ] + 2 ) % cubeSize[ 1 ] == 0 )
			nCellsY--;
		if ( ( volDim[ 2 ] + 2 ) % cubeSize[ 2 ] == 0 )
			nCellsZ--;

		this.nCellsX = nCellsX;
		this.nCellsY = nCellsY;
		this.nCellsZ = nCellsZ;
	}

	/**
	 * Generic method to generate the mesh
	 *
	 * @param input
	 *            RAI<T> that contains the volume label information
	 * @param volDim
	 *            dimension of the volume (chunk)
	 * @param offset
	 *            the chunk offset to correctly positioning the mesh
	 * @param cubeSize
	 *            the size of the cube that will generate the mesh
	 * @param foregroundCriteria
	 *            criteria to be considered in order to activate voxels
	 * @param foregroundValue
	 *            the value that will be used to generate the mesh
	 * @param copyToArray
	 *            if the data must be copied to an array before the mesh
	 *            generation
	 * @return SimpleMesh, basically an array with the vertices
	 */
	public SimpleMesh generateMesh( final boolean copyToArray )
	{
		SimpleMesh mesh = null;
		final int[] volDim = Intervals.dimensionsAsIntArray( input );
		if ( copyToArray )
		{
			if ( Util.getTypeFromInterval( input ) instanceof LabelMultisetType )
			{
				LOGGER.info( "copyToArray - input is instance of LabelMultisetType" );
				// TODO: to verify this instantiation
				final CopyDataToArray< T > copy = ( CopyDataToArray< T > ) new CopyDataToArrayFromLabelMultisetType();
				mesh = generateMeshFromArray( input, volDim, cubeSize, copy );
			}
			else if ( Util.getTypeFromInterval( input ) instanceof IntegerType< ? > )
			{
				LOGGER.info( "copyToArray - input is instance of IntegerType" );
				// TODO: to verify this instantiation
				final CopyDataToArray< T > copy = ( CopyDataToArray< T > ) new CopyDataToArrayFromIntegerType<>();
				mesh = generateMeshFromArray( input, volDim, cubeSize, copy );
			}
			else
				LOGGER.error( "copyToArray - input has unknown type" );
		}
		else if ( Util.getTypeFromInterval( input ) instanceof LabelMultisetType )
		{
			LOGGER.info( "input is instance of LabelMultisetType" );
			final NextVertexValues< T > nextVertices = ( NextVertexValues< T > ) new NextVertexValuesFromLabelMultisetType();
			mesh = generateMeshFromRAI( input, cubeSize, nextVertices );
		}
		else if ( Util.getTypeFromInterval( input ) instanceof IntegerType< ? > )
		{
			LOGGER.info( "input is instance of IntegerType" );
			final NextVertexValues< T > nextVertices = ( NextVertexValues< T > ) new NextVertexValuesFromIntegerType<>();
			mesh = generateMeshFromRAI( input, cubeSize, nextVertices );
		}
		else
			LOGGER.error( "input has unknown type" );

		return mesh;
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
	private SimpleMesh generateMeshFromRAI( final RandomAccessibleInterval< T > input, final int[] cubeSize, final NextVertexValues< T > nextValuesVertex )
	{
		final Cursor< T > cursor = nextValuesVertex.createCursor( input );

		cursor.next();
		final int beginX = cursor.getIntPosition( 0 );

		// how many cells we walked in x, y and z.
		int xCells = 0;
		int yCells = 0;
		int zCells = 0;

		while ( cursor.hasNext() )
		{
			int cursorX = cursor.getIntPosition( 0 );
			int cursorY = cursor.getIntPosition( 1 );
			int cursorZ = cursor.getIntPosition( 2 );

			if ( beginX != cursorX )
				xCells++;

			// for each one of the verticesCursor, get its value
			double[] vertexValues = new double[ 8 ];
			nextValuesVertex.getVerticesValues( cursorX, cursorY, cursorZ, cubeSize, vertexValues );

			if ( LOGGER.isDebugEnabled() )
			{
				// @formatter:off
				LOGGER.debug( " " + ( int ) vertexValues[ 4 ] + "------" + ( int ) vertexValues[ 5 ] );
				LOGGER.debug( " /|     /|" );
				LOGGER.debug( " " + ( int ) vertexValues[ 7 ] + "-----" + ( int ) vertexValues[ 6 ] + " |" );
				LOGGER.debug( " |" + ( int ) vertexValues[ 0 ] + "----|-" + ( int ) vertexValues[ 1 ] );
				LOGGER.debug( " |/    |/" );
				LOGGER.debug( " " + ( int ) vertexValues[ 3 ] + "-----" + ( int ) vertexValues[ 2 ] );
				// @formatter:on
			}

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

			vertexValues = remapCube( vertexValues );

			if ( LOGGER.isDebugEnabled() )
			{
				// @formatter:off
				LOGGER.debug( " " + ( int ) vertexValues[ 4 ] + "------" + ( int ) vertexValues[ 5 ] );
				LOGGER.debug( " /|     /|" );
				LOGGER.debug( " " + ( int ) vertexValues[ 7 ] + "-----" + ( int ) vertexValues[ 6 ] + " |" );
				LOGGER.debug( " |" + ( int ) vertexValues[ 0 ] + "----|-" + ( int ) vertexValues[ 1 ] );
				LOGGER.debug( " |/    |/" );
				LOGGER.debug( " " + ( int ) vertexValues[ 3 ] + "-----" + ( int ) vertexValues[ 2 ] );
				// @formatter:on
			}

			triangulation( vertexValues, xCells, yCells, zCells );

			for ( int j = 0; j < cubeSize[ 0 ]; j++ )
				cursor.next();

			if ( xCells == nCellsX - 1 )
			{
				final int newY = cursorY + cubeSize[ 1 ];
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorX = cursor.getIntPosition( 0 );
					cursorY = cursor.getIntPosition( 1 );
					if ( cursorX == input.min( 0 ) - 1 && cursorY == newY )
						break;
				}
				xCells = 0;
				yCells++;
			}

			if ( yCells == nCellsY )
			{
				final int newZ = cursorZ + cubeSize[ 2 ];
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorY = cursor.getIntPosition( 1 );
					cursorZ = cursor.getIntPosition( 2 );
					if ( cursorY == input.min( 1 ) - 1 && cursorZ == newZ )
						break;
				}
				yCells = 0;
				zCells++;
			}

			if ( zCells == nCellsZ )
			{
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorZ = cursor.getIntPosition( 2 );
					if ( cursorZ == input.min( 2 ) - 1 )
						break;
				}
				zCells = 0;
			}
		}

		convertVerticesFormat();

		return mesh;
	}

	/**
	 * @param input
	 *            RAI with the label (segmentation) information
	 * @param volDim
	 *            dimension of the volume (chunk)
	 * @param cubeSize
	 *            size of the cube to walk in the volume
	 * @param copyData
	 *            generic interface that copies the data to an array
	 * @return SimpleMesh, basically an array with the vertices
	 */
	private SimpleMesh generateMeshFromArray( final RandomAccessibleInterval< T > input, final int[] volDim, final int[] cubeSize, final CopyDataToArray< T > copyData )
	{
		// array where the data will be copied
		final List< Long > volumeArray = new ArrayList<>();

		// dimension on x direction, used to access the volume as an array
		int xWidth = 0;

		// dimension on xy direction, used to access the volume as an array
		int xyWidth = 0;

		copyData.copyDataToArray( input, volumeArray );

		// two dimensions more: from 'min minus one' to 'max plus one'
		xWidth = volDim[ 0 ] + 2;
		xyWidth = xWidth * ( volDim[ 1 ] + 2 );

		if ( LOGGER.isDebugEnabled() )
		{
			LOGGER.debug( "volume size: " + volumeArray.size() );
			LOGGER.debug( "xWidth: " + xWidth + " xyWidth: " + xyWidth );
			LOGGER.debug( "ncells - x, y, z: " + nCellsX + " " + nCellsY + " " + nCellsZ );
			LOGGER.debug( "max position on array: "
					+ ( ( int ) ( cubeSize[ 2 ] * nCellsZ ) * xyWidth
							+ ( int ) ( cubeSize[ 1 ] * nCellsY ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * nCellsX ) ) );
		}

		final double[] vertexValues = new double[ 8 ];

		for ( int cursorZ = 0; cursorZ < nCellsZ; cursorZ++ )
			for ( int cursorY = 0; cursorY < nCellsY; cursorY++ )
				for ( int cursorX = 0; cursorX < nCellsX; cursorX++ )
				{
					// @formatter:off
					// the values from the cube are given first in y, then x, then z
					// this way, the vertex_values (from getCube) are positioned in this
					// way:
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

					if ( LOGGER.isTraceEnabled() )
					{
						LOGGER.trace( "position 7: " + ( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
								+ cursorX * cubeSize[ 0 ] ) );
						LOGGER.trace( "position 6: " + ( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
								+ cubeSize[ 0 ] * ( cursorX + 1 ) ) );
						LOGGER.trace( "position 3: " + ( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
								+ cubeSize[ 0 ] * cursorX ) );
						LOGGER.trace( "position 2: " + ( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
								+ cubeSize[ 0 ] * ( cursorX + 1 ) ) );
						LOGGER.trace( "position 4: " + ( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
								+ cubeSize[ 0 ] * cursorX ) );
						LOGGER.trace( "position 0: " + ( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
								+ cubeSize[ 0 ] * ( cursorX + 1 ) ) );
						LOGGER.trace( "position 5: " + ( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
								+ cubeSize[ 0 ] * cursorX ) );
						LOGGER.trace( "position 1: " + ( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
								+ cubeSize[ 0 ] * ( cursorX + 1 ) ) );
					}

					vertexValues[ 7 ] = volumeArray.get( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
							+ cursorX * cubeSize[ 0 ] );
					vertexValues[ 3 ] = volumeArray.get( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
							+ cubeSize[ 0 ] * ( cursorX + 1 ) );
					vertexValues[ 6 ] = volumeArray.get( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
							+ cubeSize[ 0 ] * cursorX );
					vertexValues[ 2 ] = volumeArray.get( cubeSize[ 2 ] * cursorZ * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
							+ cubeSize[ 0 ] * ( cursorX + 1 ) );
					vertexValues[ 4 ] = volumeArray.get( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
							+ cubeSize[ 0 ] * cursorX );
					vertexValues[ 0 ] = volumeArray.get( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * cursorY * xWidth
							+ cubeSize[ 0 ] * ( cursorX + 1 ) );
					vertexValues[ 5 ] = volumeArray.get( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
							+ cubeSize[ 0 ] * cursorX );
					vertexValues[ 1 ] = volumeArray.get( cubeSize[ 2 ] * ( cursorZ + 1 ) * xyWidth + cubeSize[ 1 ] * ( cursorY + 1 ) * xWidth
							+ cubeSize[ 0 ] * ( cursorX + 1 ) );

					if ( LOGGER.isDebugEnabled() )
					{
						// @formatter:off
						LOGGER.debug( " " + ( int ) vertexValues[ 4 ] + "------" + ( int ) vertexValues[ 5 ] );
						LOGGER.debug( " /|     /|" );
						LOGGER.debug( " " + ( int ) vertexValues[ 7 ] + "-----" + ( int ) vertexValues[ 6 ] + " |" );
						LOGGER.debug( " |" + ( int ) vertexValues[ 0 ] + "----|-" + ( int ) vertexValues[ 1 ] );
						LOGGER.debug( " |/    |/" );
						LOGGER.debug( " " + ( int ) vertexValues[ 3 ] + "-----" + ( int ) vertexValues[ 2 ] );
						// @formatter:on
					}

					triangulation( vertexValues, cursorX, cursorY, cursorZ );
				}

		convertVerticesFormat();

		return mesh;
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
	private void triangulation( final double[] vertexValues, final int cursorX, final int cursorY, final int cursorZ )
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
		int tableIndex = 0;
		for ( int i = 0; i < 8; i++ )
			if ( foregroundCriterionTest( vertexValues[ i ] ) )
				tableIndex |= 1 << i;

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

			for (int i = 0; MarchingCubesTables.MC_TRI_TABLE[tableIndex][i] != MarchingCubesTables.Invalid; i += 3)
			{
				vertices.add( new float[] {
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i ] ][ 0 ],
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i ] ][ 1 ],
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i ] ][ 2 ]} );

				vertices.add( new float[] {
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 1 ] ][ 0 ],
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 1 ] ][ 1 ],
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 1 ] ][ 2 ]} );

				vertices.add( new float[] {
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i + 2] ][ 0 ],
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i  + 2] ][ 1 ],
						interpolationPoints[ MarchingCubesTables.MC_TRI_TABLE[ tableIndex ][ i  + 2] ][ 2 ]} );
			}
		}
	}

	/**
	 * Convert the arraylist into a float[][]
	 */
	private void convertVerticesFormat()
	{
		final int numberOfVertices = vertices.size();
		mesh.setNumberOfVertices(numberOfVertices);

		final float[][] verticesArray = new float[numberOfVertices][3];

		for (int i = 0; i < numberOfVertices; i++)
		{
			verticesArray[i][0] = vertices.get( i )[ 0 ];
			verticesArray[i][1] = vertices.get( i )[ 1 ];
			verticesArray[i][2] = vertices.get( i )[ 2 ];

			if (LOGGER.isTraceEnabled())
			{
				LOGGER.trace( "vertex x: " + verticesArray[i][0] );
				LOGGER.trace( "vertex y: " + verticesArray[i][1] );
				LOGGER.trace( "vertex z: " + verticesArray[i][2] );
			}
		}

		mesh.setVertices(verticesArray);
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
	private float[] calculateIntersection( final int cursorX, final int cursorY, final int cursorZ, final int intersectedEdge )
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

		v1x = ( v1x + offset[ 0 ] / cubeSize[ 0 ] ) * cubeSize[ 0 ];
		v1y = ( v1y + offset[ 1 ] / cubeSize[ 1 ] ) * cubeSize[ 1 ];
		v1z = ( v1z + offset[ 2 ] / cubeSize[ 2 ] ) * cubeSize[ 2 ];

		v2x = ( v2x + offset[ 0 ] / cubeSize[ 0 ] ) * cubeSize[ 0 ];
		v2y = ( v2y + offset[ 1 ] / cubeSize[ 1 ] ) * cubeSize[ 1 ];
		v2z = ( v2z + offset[ 2 ] / cubeSize[ 2 ] ) * cubeSize[ 2 ];

		if (LOGGER.isTraceEnabled())
		{
			LOGGER.trace( "v1: " + v1x + " " + v1y + " " + v1z );
			LOGGER.trace( "v2: " + v2x + " " + v2y + " " + v2z );
		}

		float diffX = v2x - v1x;
		float diffY = v2y - v1y;
		float diffZ = v2z - v1z;

		diffX *= 0.5f;
		diffY *= 0.5f;
		diffZ *= 0.5f;

		diffX += v1x;
		diffY += v1y;
		diffZ += v1z;

		return new float[] { diffX, diffY, diffZ };
	}

	/**
	 * Checks if the given value matches the foreground accordingly with the
	 * foreground criterion. This comparison is dependent on the variable
	 * {@link #criteria}
	 *
	 * @param vertexValue
	 *            value that will be compared with the foregroundValue
	 *
	 * @return true if it comply with the comparison, false otherwise.
	 */
	private boolean foregroundCriterionTest(final double vertexValue)
	{
		int result = Double.compare( vertexValue, foregroundValue );
		if (criteria.equals( ForegroundCriterion.EQUAL ))
			return (result == 0); // vertexValue == foregroundValue;
		else
			return (result > 0); // vertexValue >= foregroundValue;
	}

	/**
	 * Remap the vertices of the cube (8 positions) obtained from a RAI to match
	 * the expected order for this implementation
	 *
	 * @param vertexValues
	 *            the vertices to change the order
	 * @return same array but with positions in the expected place
	 */
	private double[] remapCube(final double[] vertexValues)
	{
		final double[] vv = new double[8];
		vv[0] = vertexValues[5];
		vv[1] = vertexValues[7];
		vv[2] = vertexValues[3];
		vv[3] = vertexValues[1];
		vv[4] = vertexValues[4];
		vv[5] = vertexValues[6];
		vv[6] = vertexValues[2];
		vv[7] = vertexValues[0];

		return vv;
	}
}
