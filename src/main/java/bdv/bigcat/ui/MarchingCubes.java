package bdv.bigcat.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.imglib2.RealPoint;

/**
 * MarchingCubes from
 * https://github.com/funkey/sg_gui/blob/master/MarchingCubes.h
 */
public class MarchingCubes< T extends Comparable< T > >
{
	/** Log */
	private static final Logger LOGGER = Logger.getLogger( MarchingCubes.class.getName() );

	/** List of Point3ds which form the isosurface. */
	HashMap< Long, Point3dId > id2Point3dId = new HashMap< Long, Point3dId >();

	/** the mesh that represents the surface. */
	Mesh mesh;

	/** List of Triangles which form the triangulation of the isosurface. */
	Vector< Triangle > triangleVector = new Vector< Triangle >();

	/** No. of cells in x, y, and z directions. */
	long nCellsX, nCellsY, nCellsZ;

	/** Cell length in x, y, and z directions. */
	float cellSizeX, cellSizeY, cellSizeZ;

	int width, height, depth;

	/** The isosurface value. */
	T tIsoLevel;

	T[] volume;

	/** Indicates whether a valid surface is present. */
	boolean bValidSurface;

	/**
	 * Indicates if the threshold will be applied for the exact value or above
	 */
	private boolean isExactly = false;

	int[] volumeMin;

	/**
	 * A point in 3D with an id
	 */
	class Point3dId
	{
		long newId;

		float x, y, z;

		// default constructor
		public Point3dId()
		{}

		// constructor from RealPoint 3d - x, y, z
		public Point3dId( RealPoint point )
		{
			newId = 0;
			x = point.getFloatPosition( 0 );
			y = point.getFloatPosition( 1 );
			z = point.getFloatPosition( 2 );
		}
	}

	public class Mesh
	{
		int vertexCount;

		float[][] vertices;

		float[][] normals;

		int faceCount;

		int[] faces;

		public Mesh( int vertex, float[][] vert, float[][] norm, int face, int[] nFaces )
		{
			vertexCount = vertex;
			vertices = vert;
			normals = norm;
			faceCount = face;
			faces = nFaces;
		}

		public Mesh()
		{

		}

	}

	/**
	 * Triples of points that form a triangle.
	 */
	class Triangle
	{
		long[] point = new long[ 3 ];
	}

	public MarchingCubes( int[] volMin )
	{
		cellSizeX = 0;
		cellSizeY = 0;
		cellSizeZ = 0;
		nCellsX = 0;
		nCellsY = 0;
		nCellsZ = 0;
		bValidSurface = false;
		isExactly = false;
		volumeMin = volMin;
	}

	/**
	 * Creates the mesh given a volume
	 * 
	 * @param voxDim
	 *            dimensions x, y, z of the cube
	 * @param volDim
	 *            dimensions x, y, z of the volume
	 * @return a triangle mesh
	 */
	public Mesh generateSurface( T[] vol, float[] voxDim, int[] volDim, boolean isExact, T level )
	{

		if ( bValidSurface )
			deleteSurface();

		mesh = new Mesh();

		volume = vol;
		tIsoLevel = level;

		width = volDim[ 0 ];// volume.getBoundingBox().width();
		height = volDim[ 1 ];// volume.getBoundingBox().height();
		depth = volDim[ 2 ];// volume.getBoundingBox().depth();

		nCellsX = ( long ) Math.ceil( width / voxDim[ 0 ] ) - 1;
		nCellsY = ( long ) Math.ceil( height / voxDim[ 1 ] ) - 1;
		nCellsZ = ( long ) Math.ceil( depth / voxDim[ 2 ] ) - 1;
		cellSizeX = voxDim[ 0 ];
		cellSizeY = voxDim[ 1 ];
		cellSizeZ = voxDim[ 2 ];
		isExactly = isExact;

		LOGGER.log( Level.FINE, "creating mesh for " + width + "x" + height + "x" + depth
				+ " volume with " + nCellsX + "x" + nCellsY + "x" + nCellsZ + " cells" );

		LOGGER.log( Level.FINE, "volume size: " + width*height*depth);
		
		// Generate isosurface.
		for ( int z = 0; z < nCellsZ; z++ )
			for ( int y = 0; y < nCellsY; y++ )
				for ( int x = 0; x < nCellsX; x++ )
				{
					// Calculate table lookup index from those
					// vertices which are below the isolevel.
					int tableIndex = 0;

					LOGGER.log( Level.FINER, "x y z: " + x + " " + y + " " + z);
					if ( !interiorTest( volume[ ( int ) ( (x)* cellSizeX + ( width * (y)*cellSizeY ) + width * height * (z)*cellSizeZ ) ] ) ) {
						tableIndex |= 1;
					}
					if ( !interiorTest( volume[ ( int ) ( (x)* cellSizeX + ( width * (y+1)*cellSizeY ) + width * height * (z)*cellSizeZ ) ] ) ) {
						tableIndex |= 2;
					}
					if ( !interiorTest( volume[ ( int ) ( (x+1)* cellSizeX + ( width * ((y+1)*cellSizeY) ) + width * height * (z)*cellSizeZ ) ] ) ) {
						tableIndex |= 4;
					}
					if ( !interiorTest( volume[ ( int ) ( (x+1)* cellSizeX + ( width * (y)*cellSizeY ) + width * height * (z)*cellSizeZ ) ] ) ) {
						tableIndex |= 8;
					}
					if ( !interiorTest( volume[ ( int ) ( (x)* cellSizeX + ( width * (y)*cellSizeY ) + width * height * (z+1)*cellSizeZ ) ] ) ) {
						tableIndex |= 16;
					}
					if ( !interiorTest( volume[ ( int ) ( (x)* cellSizeX + ( width * (y+1)*cellSizeY ) + width * height * (z+1)*cellSizeZ ) ] ) ) {
						tableIndex |= 32;
					}
					if ( !interiorTest( volume[ ( int ) ( (x+1)* cellSizeX + ( width * (y+1)*cellSizeY ) + width * height * (z+1)*cellSizeZ ) ] ) ) {
						tableIndex |= 64;
					}
					if ( !interiorTest( volume[ ( int ) ( (x+1)* cellSizeX + ( width * (y)*cellSizeY ) + width * height * (z+1)*cellSizeZ ) ] ) ) {
						tableIndex |= 128;
					}
					
					System.out.println("tableIndex = " + tableIndex );
					// Now create a triangulation of the isosurface in this
					// cell.
					if ( TablesMC.MC_EDGE_TABLE[ tableIndex ] != 0 )
					{
						if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 8 ) != 0 )
						{
							LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
							Point3dId pt = calculateIntersection( /*
																	 * volume,
																	 * interiorTest,
																	 */ x, y, z, 3 );
							long id = getEdgeId( x, y, z, 3 );
							LOGGER.log( Level.FINEST, " adding point with id: " + id);
							id2Point3dId.put( id, pt );
						}
						if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 1 ) != 0 )
						{
							LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
							Point3dId pt = calculateIntersection( /*
																	 * volume,
																	 * interiorTest,
																	 */ x, y, z, 0 );
							long id = getEdgeId( x, y, z, 0 );
							LOGGER.log( Level.FINEST, " adding point with id: " + id);
							id2Point3dId.put( id, pt );
						}
						if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 256 ) != 0 )
						{
							LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);							Point3dId pt = calculateIntersection( /*
																	 * volume,
																	 * interiorTest,
																	 */ x, y, z, 8 );
							long id = getEdgeId( x, y, z, 8 );
							LOGGER.log( Level.FINEST, " adding point with id: " + id);
							id2Point3dId.put( id, pt );
						}

						if ( x == nCellsX - 1 )
						{
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 4 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 2 );
								long id = getEdgeId( x, y, z, 2 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 2048 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 11 );
								long id = getEdgeId( x, y, z, 11 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
						}
						if ( y == nCellsY - 1 )
						{
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 2 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 1 );
								long id = getEdgeId( x, y, z, 1 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 512 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 9 );
								long id = getEdgeId( x, y, z, 9 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
						}
						if ( z == nCellsZ - 1 )
						{
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 16 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 4 );
								long id = getEdgeId( x, y, z, 4 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 128 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 7 );
								long id = getEdgeId( x, y, z, 7 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
						}
						if ( ( x == nCellsX - 1 ) && ( y == nCellsY - 1 ) )
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 1024 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 10 );
								long id = getEdgeId( x, y, z, 10 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
						if ( ( x == nCellsX - 1 ) && ( z == nCellsZ - 1 ) )
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 64 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 6 );
								long id = getEdgeId( x, y, z, 6 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}
						if ( ( y == nCellsY - 1 ) && ( z == nCellsZ - 1 ) )
							if ( ( TablesMC.MC_EDGE_TABLE[ tableIndex ] & 32 ) != 0 )
							{
								LOGGER.log( Level.FINEST, "x: " + x + " y " + y + " z " + z);
								Point3dId pt = calculateIntersection( /*
																		 * volume,
																		 * interiorTest,
																		 */ x, y, z, 5 );
								long id = getEdgeId( x, y, z, 5 );
								LOGGER.log( Level.FINEST, " adding point with id: " + id);
								id2Point3dId.put( id, pt );
							}

						for ( int i = 0; TablesMC.triTable[ tableIndex ][ i ] != TablesMC.Invalid; i += 3 )
						{
							Triangle triangle = new Triangle();
							long pointId0, pointId1, pointId2;
							pointId0 = getEdgeId( x, y, z, TablesMC.triTable[ tableIndex ][ i ] );
							pointId1 = getEdgeId( x, y, z, TablesMC.triTable[ tableIndex ][ i + 1 ] );
							pointId2 = getEdgeId( x, y, z, TablesMC.triTable[ tableIndex ][ i + 2 ] );
							System.out.println("value on tritable: " + TablesMC.triTable[ tableIndex ][ i ]);
							System.out.println("value on tritable: " + TablesMC.triTable[ tableIndex ][ i + 1]);
							System.out.println("value on tritable: " + TablesMC.triTable[ tableIndex ][ i + 2]);
							System.out.println("triangles point: " + pointId0 + " " + pointId1 + " " + pointId2);
							triangle.point[ 0 ] = pointId0;
							triangle.point[ 1 ] = pointId1;
							triangle.point[ 2 ] = pointId2;
							triangleVector.add( triangle );
						}
					}
				}

		renameVerticesAndTriangles();
		calculateNormals();
		bValidSurface = true;

		return mesh;
	}

	private void deleteSurface()
	{
		cellSizeX = 0;
		cellSizeY = 0;
		cellSizeZ = 0;
		nCellsX = 0;
		nCellsY = 0;
		nCellsZ = 0;
		bValidSurface = false;
	}

	private void renameVerticesAndTriangles()
	{
		long nextId = 0;
		Iterator< Entry< Long, Point3dId > > mapIterator = id2Point3dId.entrySet().iterator();
		Iterator< Triangle > vecIterator = triangleVector.iterator();

		System.out.println("number of ids on map: " + id2Point3dId.size());

		// add an id for each point in the map
		while ( mapIterator.hasNext() )
		{
			HashMap.Entry< Long, Point3dId > entry = mapIterator.next();
			System.out.println("key id: " + entry.getKey());
			entry.getValue().newId = nextId;
			nextId++;
		}

		System.out.println("number of triangles: " + triangleVector.size());
		
		// Now rename triangles.
		while ( vecIterator.hasNext() )
		{
			Triangle next = vecIterator.next();
			System.out.println("getting triangle");
			for ( int i = 0; i < 3; i++ )
			{
				System.out.println("triangle point old id: " + next.point[i]);
				System.out.println("id of id - new id: " + id2Point3dId.get( next.point[ i ] ).newId);
				long newId = id2Point3dId.get( next.point[ i ] ).newId;
				next.point[ i ] = newId;
			}
		}

		// Copy all the vertices and triangles into two arrays so that they
		// can be efficiently accessed.
		// Copy vertices.
		mesh.vertexCount = id2Point3dId.size();
		LOGGER.log( Level.FINE, "created a mesh with " + mesh.vertexCount + " vertices" );

		mapIterator = id2Point3dId.entrySet().iterator();
		float[][] vertices = new float[ mesh.vertexCount ][ 3 ];
		
		for ( int i = 0; i < mesh.vertexCount; i++ )
		{
			HashMap.Entry< Long, Point3dId > entry = mapIterator.next();
			vertices[ i ][ 0 ] = entry.getValue().x;
			vertices[ i ][ 1 ] = entry.getValue().y;
			vertices[ i ][ 2 ] = entry.getValue().z;
		}
		
		mesh.vertices = vertices;
		// Copy vertex indices which make triangles.
		vecIterator = triangleVector.iterator();
		mesh.faceCount = triangleVector.size();
		int[] faces = new int[ mesh.faceCount * 3 ];
		for ( int i = 0; i < mesh.faceCount; i++ )
		{
			Triangle next = vecIterator.next();
			faces[ i * 3 ] = ( int ) next.point[ 0 ];
			faces[ i * 3 + 1 ] = ( int ) next.point[ 1 ];
			faces[ i * 3 + 2 ] = ( int ) next.point[ 2 ];
		}

		mesh.faces = faces;
		id2Point3dId.clear();
		triangleVector.clear();
	}

	private void calculateNormals()
	{
		int vertexCount = mesh.vertexCount;
		float[][] vertices = mesh.vertices;
		int triangleCount = mesh.faceCount;
		int[] triangles = mesh.faces;

		float[][] normals = new float[ vertexCount ][ 3 ];

		// Set all normals to 0.
		for ( int i = 0; i < vertexCount; ++i )
		{
			normals[ i ][ 0 ] = 0;
			normals[ i ][ 1 ] = 0;
			normals[ i ][ 2 ] = 0;
		}

		// Calculate normals.
		for ( int i = 0; i < triangleCount; ++i )
		{
			float[] vec1 = new float[ 3 ],
					vec2 = new float[ 3 ],
					normal = new float[ 3 ];

			int id0, id1, id2;

			id0 = triangles[ i * 3 ];
			id1 = triangles[ i * 3 + 1 ];
			id2 = triangles[ i * 3 + 2 ];
			vec1[ 0 ] = vertices[ id1 ][ 0 ] - vertices[ id0 ][ 0 ];
			vec1[ 1 ] = vertices[ id1 ][ 1 ] - vertices[ id0 ][ 1 ];
			vec1[ 2 ] = vertices[ id1 ][ 2 ] - vertices[ id0 ][ 2 ];
			vec2[ 0 ] = vertices[ id2 ][ 0 ] - vertices[ id0 ][ 0 ];
			vec2[ 1 ] = vertices[ id2 ][ 1 ] - vertices[ id0 ][ 1 ];
			vec2[ 2 ] = vertices[ id2 ][ 2 ] - vertices[ id0 ][ 2 ];
			normal[ 0 ] = vec1[ 2 ] * vec2[ 1 ] - vec1[ 1 ] * vec2[ 2 ];
			normal[ 1 ] = vec1[ 0 ] * vec2[ 2 ] - vec1[ 2 ] * vec2[ 0 ];
			normal[ 2 ] = vec1[ 1 ] * vec2[ 0 ] - vec1[ 0 ] * vec2[ 1 ];
			normals[ id0 ][ 0 ] += normal[ 0 ];
			normals[ id0 ][ 1 ] += normal[ 1 ];
			normals[ id0 ][ 2 ] += normal[ 2 ];
			normals[ id1 ][ 0 ] += normal[ 0 ];
			normals[ id1 ][ 1 ] += normal[ 1 ];
			normals[ id1 ][ 2 ] += normal[ 2 ];
			normals[ id2 ][ 0 ] += normal[ 0 ];
			normals[ id2 ][ 1 ] += normal[ 1 ];
			normals[ id2 ][ 2 ] += normal[ 2 ];
		}

		// Normalize normals.
		for ( int i = 0; i < vertexCount; ++i )
		{
			float length = ( float ) Math.sqrt(
					normals[ i ][ 0 ] * normals[ i ][ 0 ] +
							normals[ i ][ 1 ] * normals[ i ][ 1 ] +
							normals[ i ][ 2 ] * normals[ i ][ 2 ] );

			normals[ i ][ 0 ] /= length;
			normals[ i ][ 1 ] /= length;
			normals[ i ][ 2 ] /= length;
		}
		mesh.normals = normals;
	}

	private Point3dId calculateIntersection( /* Volume *//* Interiortest */ int nX, int nY, int nZ, int nEdgeNo )
	{
		RealPoint p1 = new RealPoint( 3 ),
				p2 = new RealPoint( 3 );
		int v1x = nX, v1y = nY, v1z = nZ;
		int v2x = nX, v2y = nY, v2z = nZ;

		switch ( nEdgeNo )
		{
		case 0:
			v2y += 1;
			break;
		case 1:
			v1y += 1;
			v2x += 1;
			v2y += 1;
			break;
		case 2:
			v1x += 1;
			v1y += 1;
			v2x += 1;
			break;
		case 3:
			v1x += 1;
			break;
		case 4:
			v1z += 1;
			v2y += 1;
			v2z += 1;
			break;
		case 5:
			v1y += 1;
			v1z += 1;
			v2x += 1;
			v2y += 1;
			v2z += 1;
			break;
		case 6:
			v1x += 1;
			v1y += 1;
			v1z += 1;
			v2x += 1;
			v2z += 1;
			break;
		case 7:
			v1x += 1;
			v1z += 1;
			v2z += 1;
			break;
		case 8:
			v2z += 1;
			break;
		case 9:
			v1y += 1;
			v2y += 1;
			v2z += 1;
			break;
		case 10:
			v1x += 1;
			v1y += 1;
			v2x += 1;
			v2y += 1;
			v2z += 1;
			break;
		case 11:
			v1x += 1;
			v2x += 1;
			v2z += 1;
			break;
		}

		// transform local coordinates back into volume space
//		p1.setPosition( volumeMin[ 0 ] + ( v1x - 1 ) * cellSizeX, 0 );
//		p1.setPosition( volumeMin[ 1 ] + ( v1y - 1 ) * cellSizeY, 1 );
//		p1.setPosition( volumeMin[ 2 ] + ( v1z - 1 ) * cellSizeZ, 2 );
//		p2.setPosition( volumeMin[ 0 ] + ( v2x - 1 ) * cellSizeX, 0 );
//		p2.setPosition( volumeMin[ 1 ] + ( v2y - 1 ) * cellSizeY, 1 );
//		p2.setPosition( volumeMin[ 2 ] + ( v2z - 1 ) * cellSizeZ, 2 );

		p1.setPosition( ( v1x) * cellSizeX, 0 );
		p1.setPosition( ( v1y) * cellSizeY, 1 );
		p1.setPosition( ( v1z) * cellSizeZ, 2 );
		p2.setPosition( ( v2x) * cellSizeX, 0 );
		p2.setPosition( ( v2y) * cellSizeY, 1 );
		p2.setPosition( ( v2z) * cellSizeZ, 2 );

		
		System.out.println("calculateIntersection x: " + nX + " y: " + nY + " z: " + nZ);

		System.out.println("v1x: " + v1x + " v1y: " + v1y + " v1z: " + v1z);
		System.out.println("v2x: " + v2x + " v2y: " + v2y + " v2z: " + v2z);
		
		System.out.println("accessing position v1: " + (int) (v1x + ( width * v1y ) + width * height * v1z));
		System.out.println("accessing position v2: " + (int) (v2x + ( width * v2y ) + width * height * v2z));
		System.out.println("volume size: " + (int)(width*height*depth));
		T val1 = volume[ (int) (v1x + ( width * v1y ) + width * height * v1z) ];
		T val2 = volume[ (int) (v2x + ( width * v2y ) + width * height * v2z) ];

		System.out.println("value of volume[v1]: " + val1);
		System.out.println("value of volume[v2]: " + val2);
		
		System.out.println("p1: " + p1.getDoublePosition( 0 ) + " " + p1.getDoublePosition( 1 ) + " " + p1.getDoublePosition( 2 ));
		System.out.println("p2: " + p2.getDoublePosition( 0 ) + " " + p2.getDoublePosition( 1 ) + " " + p2.getDoublePosition( 2 ));

		if ( interiorTest( val1 ) && !interiorTest( val2 ) ) {
			System.out.println("p1 and p2 swap position");
			return findSurfaceIntersection( /* volume, interiorTest, */ p2, p1 );
		}else {
			System.out.println("p1 and p2 keep position");
			return findSurfaceIntersection( /* volume, interiorTest, */ p1, p2 );
		}
	}

	private long getEdgeId( long nX, long nY, long nZ, int nEdgeNo )
	{
		switch ( nEdgeNo )
		{
		case 0:
			return getVertexId( nX, nY, nZ ) + 1;
		case 1:
			return getVertexId( nX, nY + 1, nZ );
		case 2:
			return getVertexId( nX + 1, nY, nZ ) + 1;
		case 3:
			return getVertexId( nX, nY, nZ );
		case 4:
			return getVertexId( nX, nY, nZ + 1 ) + 1;
		case 5:
			return getVertexId( nX, nY + 1, nZ + 1 );
		case 6:
			return getVertexId( nX + 1, nY, nZ + 1 ) + 1;
		case 7:
			return getVertexId( nX, nY, nZ + 1 );
		case 8:
			return getVertexId( nX, nY, nZ ) + 2;
		case 9:
			return getVertexId( nX, nY + 1, nZ ) + 2;
		case 10:
			return getVertexId( nX + 1, nY + 1, nZ ) + 2;
		case 11:
			return getVertexId( nX + 1, nY, nZ ) + 2;
		default:
			// Invalid edge no.
			return TablesMC.Invalid;
		}
	}

	private long getVertexId( long nX, long nY, long nZ )
	{
		return 3 * ( nZ * ( nCellsY + 1 ) * ( nCellsX + 1 ) + nY * ( nCellsX + 1 ) + nX );
	}

	private Point3dId findSurfaceIntersection( /* Volume *//* InteriorTest */RealPoint p1, RealPoint p2 )
	{

		Point3dId interpolation = null;

		// binary search for intersection
		float mu = ( float ) 0.5;
		float delta = ( float ) 0.25;

		// assume that p1 is outside, p2 is inside
		//
		// mu == 0 -> p1, mu == 1 -> p2
		//
		// incrase mu -> go to inside
		// decrease mu -> go to outside

		System.out.println("volume dimensions: " + width + " " + height + " " + depth);
		System.out.println("volume max position: " + width * height * depth);
		for ( long i = 0; i < 10; i++, delta /= 2.0 )
		{

			float diffX = p2.getFloatPosition( 0 ) - p1.getFloatPosition( 0 );
			float diffY = p2.getFloatPosition( 1 ) - p1.getFloatPosition( 1 );
			float diffZ = p2.getFloatPosition( 2 ) - p1.getFloatPosition( 2 );

			System.out.println("diff: " + diffX + " " + diffY + " " + diffZ);
			diffX = diffX * mu;
			diffY = diffY * mu;
			diffZ = diffZ * mu;
			System.out.println("mu: " + mu );
			System.out.println("diff * mu: " + diffX + " " + diffY + " " + diffZ);

			diffX = diffX + p1.getFloatPosition( 0 );
			diffY = diffY + p1.getFloatPosition( 1 );
			diffZ = diffZ + p1.getFloatPosition( 2 );
			
			System.out.println("diff + p1: " + diffX + " " + diffY + " " + diffZ);

			RealPoint diff = new RealPoint( diffX, diffY, diffZ );
			interpolation = new Point3dId( diff );// p1 + mu*(p2-p1);

			System.out.println("interpolation point: " + interpolation.x + " " + interpolation.y + " " + interpolation.z);
			System.out.println("delta: " + delta );
//			if (( int ) ( interpolation.x + ( width * interpolation.y ) + width * height * interpolation.z ) >= width*height*depth )
//				continue;
//			if (( int ) ( interpolation.x + ( width * interpolation.y ) + width * height * interpolation.z ) < 0 )
//				continue;
			if ( interiorTest( volume[ ( int ) ( interpolation.x + ( width * interpolation.y ) + width * height * interpolation.z ) ] ) )
				mu -= delta; // go to outside
			else
				mu += delta; // go to inside
		}

		return interpolation;
	}

	private boolean interiorTest( T value )
	{

		if ( isExactly )
		{
			if ( value.compareTo( tIsoLevel ) == 0 )
			{
				return true;
			}
			else
				return false;
		}
		else
		{
			if ( value.compareTo( tIsoLevel ) > 0 )
			{
				return true;
			}
			else {
				return false;
			}
		}
	}
}
