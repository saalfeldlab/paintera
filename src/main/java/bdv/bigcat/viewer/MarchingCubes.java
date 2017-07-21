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
import net.imglib2.RealPoint;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

/**
 * Implements the marching cubes algorithm. Based on
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

	/** Indicates whether a valid surface is present. */
	private boolean hasValidSurface;

	/** Indicates which criterion is going to be applied */
	private ForegroundCriterion criterion = ForegroundCriterion.EQUAL;

	/** array where the data will be copied if {@link #copyToArray} is true */
	List< Long > volumeArray = new ArrayList< Long >();

	/** dimension on x direction, used to access the volume as an array */
	private int xWidth = 0;

	/** dimension on xy direction, used to access the volume as an array */
	private int xyWidth = 0;

	/** size of the cube */
	int[] cubeSize;

	private ArrayList< float[] > vertices = new ArrayList<>();

	public enum ForegroundCriterion
	{
		EQUAL,
		GREATER_EQUAL
	}

	/**
	 * Creates the mesh given a volume
	 */
	public MarchingCubes()
	{
		nCellsX = 0;
		nCellsY = 0;
		hasValidSurface = false;
		criterion = ForegroundCriterion.EQUAL;
	}

	int[] offset;

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param cubeSize
	 * @param foregroundCriteria
	 * @param level
	 * @param copyToArray
	 * @return
	 */
	public SimpleMesh generateMesh( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, int[] cubeSize,
			ForegroundCriterion foregroundCriteria, int level, boolean copyToArray )
	{
		if ( copyToArray ) { return generateMeshFromArray( input, volDim, offset, cubeSize, foregroundCriteria, level ); }

		return generateMeshFromRAI( input, volDim, offset, cubeSize, foregroundCriteria, level );
	}

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param offset
	 * @param cubeSize
	 * @param isExact
	 * @param level
	 * @return
	 */
	private SimpleMesh generateMeshFromRAI( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, int[] cubeSize,
			ForegroundCriterion criterion, int level )
	{

		if ( hasValidSurface )
		{
			deleteSurface();
		}

		// when using RAI the offset is not necessary, because the data is not
		// repositioned
		this.offset = new int[] { 0, 0, 0 };

		mesh = new SimpleMesh();

		foregroundValue = level;

		nCellsX = ( long ) Math.ceil( ( volDim[ 0 ] + 2 ) / cubeSize[ 0 ] );
		nCellsY = ( long ) Math.ceil( ( volDim[ 1 ] + 2 ) / cubeSize[ 1 ] );
		nCellsZ = ( long ) Math.ceil( ( volDim[ 2 ] + 2 ) / cubeSize[ 2 ] );

		this.criterion = criterion;
		this.cubeSize = cubeSize;

		final ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended = Views
				.extendValue( input, new LabelMultisetType() );

		final Cursor< LabelMultisetType > cursor = Views
				.flatIterable( Views.interval( extended,
						new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
								new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		cursor.next();
		int beginX = cursor.getIntPosition( 0 );

		int xDirection = 0;
		int yDirection = 0;
		int zDirection = 0;
		while ( cursor.hasNext() )
		{
			int cursorX = cursor.getIntPosition( 0 );
			int cursorY = cursor.getIntPosition( 1 );
			int cursorZ = cursor.getIntPosition( 2 );

			if ( beginX != cursorX )
			{
				xDirection++;
			}

			List< Cursor< LabelMultisetType > > verticesCursor = new ArrayList< Cursor< LabelMultisetType > >();
			verticesCursor.add( getCube( extended, cursorX, cursorY, cursorZ ) );
			verticesCursor.add( getCube( extended, cursorX + cubeSize[ 0 ], cursorY, cursorZ ) );
			verticesCursor.add( getCube( extended, cursorX, cursorY + cubeSize[ 1 ], cursorZ ) );
			verticesCursor.add( getCube( extended, cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ ) );
			verticesCursor.add( getCube( extended, cursorX, cursorY, cursorZ + cubeSize[ 2 ] ) );
			verticesCursor.add( getCube( extended, cursorX + cubeSize[ 0 ], cursorY, cursorZ + cubeSize[ 2 ] ) );
			verticesCursor.add( getCube( extended, cursorX, cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );
			verticesCursor.add( getCube( extended, cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );

			int i = 0;
			double[] vertexValues = new double[ 8 ];

			for ( int vert = 0; vert < verticesCursor.size(); vert++ )
			{
				Cursor< LabelMultisetType > cursor2 = verticesCursor.get( vert );
				while ( cursor2.hasNext() )
				{
					LabelMultisetType it = cursor2.next();

					for ( final Multiset.Entry< Label > e : it.entrySet() )
					{
						vertexValues[ i ] = e.getElement().id();
					}
					i++;
				}
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

			triangulation( vertexValues, cursorX, cursorY, cursorZ );

			for ( int j = 0; j < cubeSize[ 0 ]; j++ )
				cursor.next();

			if ( xDirection == nCellsX - 1 )
			{
				int newY = cursorY + cubeSize[ 1 ];
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorX = cursor.getIntPosition( 0 );
					cursorY = cursor.getIntPosition( 1 );
					if ( cursorX == input.min( 0 ) - 1 && cursorY == newY )
						break;
				}

				xDirection = 0;
				yDirection++;
			}

			if ( yDirection == nCellsY )
			{
				int newZ = cursorZ + cubeSize[ 2 ];
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorY = cursor.getIntPosition( 1 );
					cursorZ = cursor.getIntPosition( 2 );
					if ( cursorY == input.min( 1 ) - 1 && cursorZ == newZ )
						break;
				}

				yDirection = 0;
				zDirection++;
			}

			if ( zDirection == nCellsZ )
			{
				while ( cursor.hasNext() )
				{
					cursor.next();
					cursorZ = cursor.getIntPosition( 2 );
					if ( cursorZ == input.min( 2 ) - 1 )
						break;
				}

				zDirection = 0;
			}

		}

		updateVertices();
		hasValidSurface = true;

		return mesh;
	}

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param offset
	 * @param cubeSize
	 * @param isExact
	 * @param level
	 * @return
	 */
	private SimpleMesh generateMeshFromArray( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, int[] cubeSize,
			ForegroundCriterion criterion, int level )
	{
		if ( hasValidSurface )
		{
			deleteSurface();
		}

		mesh = new SimpleMesh();
		this.offset = offset;
		foregroundValue = level;

		this.cubeSize = cubeSize;
		this.criterion = criterion;

		final ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended = Views
				.extendValue( input, new LabelMultisetType() );

		final Cursor< LabelMultisetType > cursor = Views
				.flatIterable( Views.interval( extended,
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

		nCellsX = ( long ) Math.ceil( ( volDim[ 0 ] + 2 ) / cubeSize[ 0 ] ) - 1;
		nCellsY = ( long ) Math.ceil( ( volDim[ 1 ] + 2 ) / cubeSize[ 1 ] ) - 1;
		nCellsZ = ( long ) Math.ceil( ( volDim[ 2 ] + 2 ) / cubeSize[ 2 ] ) - 1;

		if ( LOGGER.isDebugEnabled() )
		{
			LOGGER.debug( "volume size: " + volumeArray.size() );
			LOGGER.debug( "xWidth: " + xWidth + " xyWidth: " + xyWidth );
			LOGGER.debug( "ncells - x, y, z: " + nCellsX + " " + nCellsY + " " + nCellsZ );
			LOGGER.debug( "max position on array: " + ( ( ( int ) ( cubeSize[ 2 ] * nCellsZ ) * xyWidth + ( int ) ( cubeSize[ 1 ] * nCellsY ) * xWidth + ( int ) ( cubeSize[ 0 ] * nCellsX ) ) ) );
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

					vertexValues[ 7 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth + ( int ) ( cursorX * cubeSize[ 0 ] ) ) );
					vertexValues[ 3 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth + ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
					vertexValues[ 6 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( ( cubeSize[ 1 ] * ( cursorY + 1 ) ) ) * xWidth + ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
					vertexValues[ 2 ] = volumeArray.get( ( ( int ) ( cubeSize[ 2 ] * cursorZ ) * xyWidth + ( int ) ( ( cubeSize[ 1 ] * ( cursorY + 1 ) ) ) * xWidth + ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
					vertexValues[ 4 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth + ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
					vertexValues[ 0 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * cursorY ) * xWidth + ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );
					vertexValues[ 5 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * ( cursorY + 1 ) ) * xWidth + ( int ) ( cubeSize[ 0 ] * cursorX ) ) );
					vertexValues[ 1 ] = volumeArray.get( ( ( ( int ) ( cubeSize[ 2 ] * ( cursorZ + 1 ) ) ) * xyWidth + ( int ) ( cubeSize[ 1 ] * ( cursorY + 1 ) ) * xWidth + ( int ) ( cubeSize[ 0 ] * ( cursorX + 1 ) ) ) );

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

		updateVertices();
		hasValidSurface = true;

		return mesh;
	}

	/**
	 * 
	 * @param vertexValues
	 * @param cursorX
	 * @param cursorY
	 * @param cursorZ
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

	private void deleteSurface()
	{
		nCellsX = 0;
		nCellsY = 0;
		hasValidSurface = false;
	}

	private void updateVertices()
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

	private float[] calculateIntersection( final int nX, final int nY, final int nZ, final int nEdgeNo )
	{
		float[] interpolation = new float[3];
		RealPoint p1 = new RealPoint(3), p2 = new RealPoint(3);
		int v1x = nX, v1y = nY, v1z = nZ;
		int v2x = nX, v2y = nY, v2z = nZ;

		switch (nEdgeNo)
		{
		case 0:
			// edge 0 -> from p0 to p1
			// p0 = { 1 + cursorX, 0 + cursorY, 1 + cursorZ }
			v1x +=1;
			v1z+=1;

			// p1 = { 1 + cursorX, 1 + cursorY, 1 + cursorZ }
			v2x += 1;
			v2y+=1;
			v2z+=1;

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
			v2x+=1;
			v2z+=1;

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
			v1x+=1;
			v1z+=1;

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

		p1.setPosition(((v1x + offset[0]) * cubeSize[0]), 0);
		p1.setPosition(((v1y + offset[1]) * cubeSize[1]), 1);
		p1.setPosition(((v1z + offset[2]) * cubeSize[2]), 2);
		p2.setPosition(((v2x + offset[0]) * cubeSize[0]), 0);
		p2.setPosition(((v2y + offset[1]) * cubeSize[1]), 1);
		p2.setPosition(((v2z + offset[2]) * cubeSize[2]), 2);

		if (LOGGER.isTraceEnabled())
		{
			LOGGER.trace( "p1: " + p1.getDoublePosition( 0 ) + " " + p1.getDoublePosition( 1 ) + " " + p1.getDoublePosition( 2 ) );
			LOGGER.trace( "p2: " + p2.getDoublePosition( 0 ) + " " + p2.getDoublePosition( 1 ) + " " + p2.getDoublePosition( 2 ) );
			LOGGER.trace( "p1 value: " + volumeArray.get( ( int )( cubeSize[ 2 ] * v1z ) * xyWidth + ( int )( cubeSize[ 1 ] * v1y ) * xWidth + ( int )( cubeSize[ 0 ] * v1x ) ) );
			LOGGER.trace( "p2 value: " + volumeArray.get( ( int )( cubeSize[ 2 ] * v2z ) * xyWidth + ( int )( cubeSize[ 1 ] * v2y ) * xWidth + ( int )( cubeSize[ 0 ] * v2x ) ) );
		}

		float diffX = p2.getFloatPosition(0) - p1.getFloatPosition(0);
		float diffY = p2.getFloatPosition(1) - p1.getFloatPosition(1);
		float diffZ = p2.getFloatPosition(2) - p1.getFloatPosition(2);

		diffX *= 0.5f;
		diffY *= 0.5f;
		diffZ *= 0.5f;

		diffX += p1.getFloatPosition(0);
		diffY += p1.getFloatPosition(1);
		diffZ += p1.getFloatPosition(2);
		
		interpolation[ 0 ] = diffX;
		interpolation[ 1 ] = diffY;
		interpolation[ 2 ] = diffZ;

		return interpolation;
	}

	/**
	 * Checks if the given value matches the foreground accordingly with the 
	 * foreground criterion. This comparison is dependent on the variable 
	 * {@link #criterion}
	 * 
	 * @param vertexValue
	 *            value that will be compared with the foregroundValue
	 * 
	 * @return true if it comply with the comparison, false otherwise.
	 */
	private boolean foregroundCriterionTest(final double vertexValue)
	{

		if (criterion.equals( ForegroundCriterion.EQUAL ))
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
	 * Get a cube (8 vertices) from a RAI
	 * 
	 * @param extended
	 *            an interval with the data
	 * @param cursorX
	 *            position on x, where the cube starts
	 * @param cursorY
	 *            position on y, where the cube starts
	 * @param cursorZ
	 *            position on z, where the cube starts
	 * @return
	 */
	private Cursor<LabelMultisetType> getCube(
			final ExtendedRandomAccessibleInterval<LabelMultisetType, RandomAccessibleInterval<LabelMultisetType>> extended,
			final int cursorX, final int cursorY, final int cursorZ)
	{
		long[] begin = new long[] { cursorX, cursorY, cursorZ };
		long[] end = new long[] { cursorX, cursorY, cursorZ};

		return Views.flatIterable(Views.interval(extended, new FinalInterval( begin, end ))).cursor();
	}
}
