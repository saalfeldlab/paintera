package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

/**
 * This class implements the marching cubes algorithm. Based on
 * http://paulbourke.net/geometry/polygonise/
 * 
 * @author vleite
 */
public class MarchingCubes
{
	/** logger */
	private static final Logger LOGGER = LoggerFactory.getLogger( MarchingCubes.class );

	/** the mesh that represents the surface. */
	private SimpleMesh mesh;

	/** No. of cells in x, y and z directions. */
	private long nCellsX, nCellsY, nCellsZ;

	/** The value (id) that we will use to create the mesh. */
	private int foregroundValue;

	/** Indicates which criterion is going to be applied */
	private ForegroundCriterion criteria;

	/** size of the cube */
	private int[] cubeSize;

	/** list of calculated vertices */
	private ArrayList< float[] > vertices;

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
	public MarchingCubes()
	{
		this.mesh = new SimpleMesh();
		this.nCellsX = 0;
		this.nCellsY = 0;
		this.nCellsZ = 0;
		this.foregroundValue = 0;
		this.criteria = ForegroundCriterion.EQUAL;
		this.cubeSize = new int[] { 1, 1, 1 };
		this.vertices = new ArrayList<>();
	}

	int[] offset;

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param offset
	 * @param cubeSize
	 * @param foregroundCriteria
	 * @param foregroundValue
	 * @param copyToArray
	 * @return
	 */
	public SimpleMesh generateMesh( final RandomAccessibleInterval< LabelMultisetType > input, final int[] volDim, final int[] offset,
			final int[] cubeSize, final ForegroundCriterion foregroundCriteria, final int foregroundValue,
			final boolean copyToArray )
	{
		initializeVariables( volDim, offset, cubeSize, foregroundCriteria, foregroundValue );
		if ( copyToArray ) { return generateMeshFromArray( input, volDim, cubeSize ); }

		return generateMeshFromRAI( input, cubeSize );
	}

	private void initializeVariables( final int[] volDim, final int[] offset, final int[] cubeSize, final ForegroundCriterion foregroundCriteria, final int foregroundValue )
	{
		this.offset = offset;
		this.cubeSize = cubeSize;
		this.criteria = foregroundCriteria;
		this.foregroundValue = foregroundValue;

		nCellsX = ( long ) Math.ceil( ( volDim[ 0 ] + 2 ) / cubeSize[ 0 ] );
		nCellsY = ( long ) Math.ceil( ( volDim[ 1 ] + 2 ) / cubeSize[ 1 ] );
		nCellsZ = ( long ) Math.ceil( ( volDim[ 2 ] + 2 ) / cubeSize[ 2 ] );

		if ( ( volDim[ 0 ] + 2 ) % cubeSize[ 0 ] == 0 )
			nCellsX--;
		if ( ( volDim[ 1 ] + 2 ) % cubeSize[ 1 ] == 0 )
			nCellsY--;
		if ( ( volDim[ 2 ] + 2 ) % cubeSize[ 2 ] == 0 )
			nCellsZ--;
	}

	/**
	 * 
	 * @param input
	 * @param cubeSize
	 * @return
	 */
	private SimpleMesh generateMeshFromRAI( final RandomAccessibleInterval< LabelMultisetType > input, final int[] cubeSize )
	{
		final ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended =
				Views.extendValue( input, new LabelMultisetType() );

		final Cursor< LabelMultisetType > cursor = Views.flatIterable( Views.interval( extended,
				new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
						new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		cursor.next();
		int beginX = cursor.getIntPosition( 0 );

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
			{
				xCells++;
			}

			// get the 8 vertices of the cube taking into account the cube size
			List< Cursor< LabelMultisetType > > verticesCursor = new ArrayList< Cursor< LabelMultisetType > >();
			verticesCursor.add( getVertex( extended, cursorX, cursorY, cursorZ ) );
			verticesCursor.add( getVertex( extended, cursorX + cubeSize[ 0 ], cursorY, cursorZ ) );
			verticesCursor.add( getVertex( extended, cursorX, cursorY + cubeSize[ 1 ], cursorZ ) );
			verticesCursor.add( getVertex( extended, cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ ) );
			verticesCursor.add( getVertex( extended, cursorX, cursorY, cursorZ + cubeSize[ 2 ] ) );
			verticesCursor.add( getVertex( extended, cursorX + cubeSize[ 0 ], cursorY, cursorZ + cubeSize[ 2 ] ) );
			verticesCursor.add( getVertex( extended, cursorX, cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );
			verticesCursor.add( getVertex( extended, cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );

			// for each one of the verticesCursor, get its value
			double[] vertexValues = new double[ 8 ];
			for ( int i = 0; i < verticesCursor.size(); i++ )
			{
				Cursor< LabelMultisetType > vertex = verticesCursor.get( i );
				while ( vertex.hasNext() )
				{
					LabelMultisetType it = vertex.next();

					for ( final Multiset.Entry< Label > e : it.entrySet() )
					{
						vertexValues[ i ] = e.getElement().id();
					}
				}
			}

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
			{
				cursor.next();
			}

			if ( xCells == nCellsX - 1 )
			{
				int newY = cursorY + cubeSize[ 1 ];
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorX = cursor.getIntPosition( 0 );
					cursorY = cursor.getIntPosition( 1 );
					if ( cursorX == input.min( 0 ) - 1 && cursorY == newY )
					{
						break;
					}
				}
				xCells = 0;
				yCells++;
			}

			if ( yCells == nCellsY )
			{
				int newZ = cursorZ + cubeSize[ 2 ];
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorY = cursor.getIntPosition( 1 );
					cursorZ = cursor.getIntPosition( 2 );
					if ( cursorY == input.min( 1 ) - 1 && cursorZ == newZ )
					{
						break;
					}
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
					{
						break;
					}
				}
				zCells = 0;
			}
		}

		convertVerticesFormat();

		return mesh;
	}

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param cubeSize
	 * @return
	 */
	private SimpleMesh generateMeshFromArray( final RandomAccessibleInterval< LabelMultisetType > input, final int[] volDim, final int[] cubeSize )
	{
		// array where the data will be copied
		final List< Long > volumeArray = new ArrayList< Long >();

		// dimension on x direction, used to access the volume as an array
		int xWidth = 0;

		// dimension on xy direction, used to access the volume as an array
		int xyWidth = 0;

		final ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended =
				Views.extendValue( input, new LabelMultisetType() );

		final Cursor< LabelMultisetType > cursor = Views.flatIterable( Views.interval( extended,
				new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
						new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		while ( cursor.hasNext() )
		{
			final LabelMultisetType iterator = cursor.next();

			for ( final Multiset.Entry< Label > e : iterator.entrySet() )
			{
				volumeArray.add( e.getElement().id() );
				LOGGER.trace( " {}", e.getElement().id() );
			}
		}

		// two dimensions more: from 'min minus one' to 'max plus one'
		xWidth = ( volDim[ 0 ] + 2 );
		xyWidth = xWidth * ( volDim[ 1 ] + 2 );

		if ( LOGGER.isDebugEnabled() )
		{
			LOGGER.debug( "volume size: " + volumeArray.size() );
			LOGGER.debug( "xWidth: " + xWidth + " xyWidth: " + xyWidth );
			LOGGER.debug( "ncells - x, y, z: " + nCellsX + " " + nCellsY + " " + nCellsZ );
			LOGGER.debug( "max position on array: "
					+ ( ( ( int ) ( cubeSize[ 2 ] * nCellsZ ) * xyWidth
							+ ( int ) ( cubeSize[ 1 ] * nCellsY ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * nCellsX ) ) ) );
		}

		double[] vertexValues = new double[ 8 ];

		for ( int cursorZ = 0; cursorZ < nCellsZ; cursorZ++ )
		{
			for ( int cursorY = 0; cursorY < nCellsY; cursorY++ )
			{
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
						LOGGER.trace( "position 7: " + ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
								+ ( int ) ( cursorX * cubeSize[ 0 ] ) ) );
						LOGGER.trace( "position 6: " + ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
								+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
						LOGGER.trace( "position 3: " + ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( ( cubeSize[ 1 ] * ( cursorY + 1 ) ) ) * xWidth
								+ ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
						LOGGER.trace( "position 2: " + ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( ( cubeSize[ 1 ] * ( cursorY + 1 ) ) ) * xWidth
								+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
						LOGGER.trace( "position 4: " + ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
								+ ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
						LOGGER.trace( "position 0: " + ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
								+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
						LOGGER.trace( "position 5: " + ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * ( cursorY + 1 ) ) * xWidth
								+ ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
						LOGGER.trace( "position 1: " + ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * ( cursorY + 1 ) ) * xWidth
								+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
					}

					vertexValues[ 7 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
							+ ( int ) ( cursorX * cubeSize[ 0 ] ) ) );
					vertexValues[ 3 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
					vertexValues[ 6 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( ( cubeSize[ 1 ] * ( cursorY + 1 ) ) ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
					vertexValues[ 2 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( ( cubeSize[ 1 ] * ( cursorY + 1 ) ) ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
					vertexValues[ 4 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
					vertexValues[ 0 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
					vertexValues[ 5 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * ( cursorY + 1 ) ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
					vertexValues[ 1 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * ( cursorY + 1 ) ) * xWidth
							+ ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );

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
			}
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
		{
			if ( foregroundCriterionTest( vertexValues[ i ] ) )
			{
				tableIndex |= ( int ) Math.pow( 2, i );
			}
		}

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
		float[][] interpolationPoints = new float[ 12 ][];
		if (MarchingCubesTables.MC_EDGE_TABLE[tableIndex] != 0)
		{
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 1) != 0)
			{
				interpolationPoints[ 0 ] = calculateIntersection(cursorX, cursorY, cursorZ, 0);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 2) != 0)
			{
				interpolationPoints[ 1 ] = calculateIntersection(cursorX, cursorY, cursorZ, 1);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 4) != 0)
			{
				interpolationPoints[ 2 ] = calculateIntersection(cursorX, cursorY, cursorZ, 2);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 8) != 0)
			{
				interpolationPoints[ 3 ] = calculateIntersection(cursorX, cursorY, cursorZ, 3);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 16) != 0)
			{
				interpolationPoints[ 4 ] = calculateIntersection(cursorX, cursorY, cursorZ, 4);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 32) != 0)
			{
				interpolationPoints[ 5 ] = calculateIntersection(cursorX, cursorY, cursorZ, 5);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 64) != 0)
			{
				interpolationPoints[ 6 ] = calculateIntersection(cursorX, cursorY, cursorZ, 6);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 128) != 0)
			{
				interpolationPoints[ 7 ] = calculateIntersection(cursorX, cursorY, cursorZ, 7);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 256) != 0)
			{
				interpolationPoints[ 8 ] = calculateIntersection(cursorX, cursorY, cursorZ, 8);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 512) != 0)
			{
				interpolationPoints[ 9 ] = calculateIntersection(cursorX, cursorY, cursorZ, 9);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 1024) != 0)
			{
				interpolationPoints[ 10 ] = calculateIntersection(cursorX, cursorY, cursorZ, 10);
			}

			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 2048) != 0)
			{
				interpolationPoints[ 11 ] = calculateIntersection(cursorX, cursorY, cursorZ, 11);
			}

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

		float[][] verticesArray = new float[numberOfVertices][3];

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
		int v1x = cursorX, v1y = cursorY, v1z = cursorZ;
		int v2x = cursorX, v2y = cursorY, v2z = cursorZ;

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

		v1x = ( v1x + offset[ 0 ] ) * cubeSize[ 0 ];
		v1y = ( v1y + offset[ 1 ] ) * cubeSize[ 1 ];
		v1z = ( v1z + offset[ 2 ] ) * cubeSize[ 2 ];

		v2x = ( v2x + offset[ 0 ] ) * cubeSize[ 0 ];
		v2y = ( v2y + offset[ 1 ] ) * cubeSize[ 1 ];
		v2z = ( v2z + offset[ 2 ] ) * cubeSize[ 2 ];

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
		if (criteria.equals( ForegroundCriterion.EQUAL ))
		{
			return (vertexValue == foregroundValue);
		} 
		else
		{
			return (vertexValue >= foregroundValue);
		}
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
		double[] vv = new double[8];
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

	/**
	 * Get a cursor of a cube vertex from a RAI
	 * 
	 * @param extended
	 *            an interval with the data
	 * @param cursorX
	 *            position on x
	 * @param cursorY
	 *            position on y
	 * @param cursorZ
	 *            position on z
	 * @return
	 */
	private Cursor<LabelMultisetType> getVertex(
			final ExtendedRandomAccessibleInterval<LabelMultisetType, RandomAccessibleInterval<LabelMultisetType>> extended,
			final int cursorX, final int cursorY, final int cursorZ)
	{
		final long[] begin = new long[] { cursorX, cursorY, cursorZ };
		final long[] end = new long[] { cursorX, cursorY, cursorZ};

		return Views.flatIterable(Views.interval(extended, new FinalInterval( begin, end ))).cursor();
	}
}
