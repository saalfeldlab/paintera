package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;

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
 * MarchingCubes from
 * https://github.com/funkey/sg_gui/blob/master/MarchingCubes.h
 */
public class MarchingCubesRAI
{
	/** List of Point3ds which form the isosurface. */
	private HashMap< Long, Point3dId > id2Point3dId = new HashMap< Long, Point3dId >();

	/** the mesh that represents the surface. */
	private Mesh2 mesh;

	/** List of Triangles which form the triangulation of the isosurface. */
	private Vector< Triangle > triangleVector = new Vector< Triangle >();

	/** No. of cells in x and y directions. */
	private long nCellsX, nCellsY;

	/** The isosurface value. */
	private int isoLevel;

	/** Indicates whether a valid surface is present. */
	private boolean hasValidSurface;

	/**
	 * Indicates if the threshold will be applied for the exact value or above
	 * it
	 */
	private boolean acceptExactly = false;

	/**
	 * A point in 3D with an id
	 */
	private class Point3dId
	{
		private long id;

		private float x, y, z;

		/** constructor from RealPoint 3d - x, y, z */
		private Point3dId( RealPoint point )
		{
			id = 0;
			x = point.getFloatPosition( 0 );
			y = point.getFloatPosition( 1 );
			z = point.getFloatPosition( 2 );
		}
	}

	/**
	 * Triples of points that form a triangle.
	 */
	private class Triangle
	{
		private long[] point = new long[ 3 ];
	}

	/**
	 * Creates the mesh given a volume
	 */
	public MarchingCubesRAI()
	{
		nCellsX = 0;
		nCellsY = 0;
		hasValidSurface = false;
		acceptExactly = false;
	}

	int[] offset;

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param voxDim
	 * @param isExact
	 * @param level
	 * @param usingRAI
	 * @return
	 */
	public Mesh2 generateSurface( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, float[] voxDim,
			boolean isExact, int level, boolean usingRAI )
	{
		if ( usingRAI )
			return generateSurfaceRAI( input, volDim, offset, voxDim, isExact, level );

		return generateSurfaceArray( input, volDim, offset, voxDim, isExact, level );
	}

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param offset
	 * @param voxDim
	 * @param isExact
	 * @param level
	 * @return
	 */
	public Mesh2 generateSurfaceRAI( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, float[] voxDim,
			boolean isExact, int level )
	{

		if ( hasValidSurface )
			deleteSurface();

		this.offset = offset;

		mesh = new Mesh2();

		isoLevel = level;

		nCellsX = ( long ) Math.ceil( volDim[ 0 ] / voxDim[ 0 ] ) - 1;
		nCellsY = ( long ) Math.ceil( volDim[ 1 ] / voxDim[ 1 ] ) - 1;
		acceptExactly = isExact;

		ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended = Views
				.extendValue( input, new LabelMultisetType() );
		Cursor< LabelMultisetType > c = Views
				.interval( extended,
						new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
								new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) )
				.localizingCursor();

		while ( c.hasNext() )
		{
			c.next();

			int cursorX = c.getIntPosition( 0 );
			int cursorY = c.getIntPosition( 1 );
			int cursorZ = c.getIntPosition( 2 );

			Cursor< LabelMultisetType > cu = getCube( extended, cursorX, cursorY, cursorZ );

			int i = 0;
			double[] vertex_values = new double[ 8 ];
			while ( cu.hasNext() )
			{
				LabelMultisetType it = cu.next();

				for ( final Multiset.Entry< Label > e : it.entrySet() )
				{
					vertex_values[ i ] = e.getElement().id();
				}
				i++;
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

			vertex_values = remapCube( vertex_values );

			triangulation( vertex_values, cursorX, cursorY, cursorZ );
		}

		renameVerticesAndTriangles();
		hasValidSurface = true;

		return mesh;
	}

	/**
	 * 
	 * @param input
	 * @param volDim
	 * @param offset
	 * @param voxDim
	 * @param isExact
	 * @param level
	 * @return
	 */
	public Mesh2 generateSurfaceArray( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, float[] voxDim,
			boolean isExact, int level )
	{
		if ( hasValidSurface )
			deleteSurface();

		mesh = new Mesh2();
		this.offset = offset;
		isoLevel = level;

		nCellsX = ( long ) Math.ceil( volDim[ 0 ] / voxDim[ 0 ] ) - 1;
		nCellsY = ( long ) Math.ceil( volDim[ 1 ] / voxDim[ 1 ] ) - 1;
		acceptExactly = isExact;

		ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended = Views
				.extendValue( input, new LabelMultisetType() );

		Cursor< LabelMultisetType > c = Views
				.flatIterable( Views.interval( extended,
						new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
								new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		List< Long > volume = new ArrayList< Long >();

		while ( c.hasNext() )
		{
			LabelMultisetType it = c.next();

			for ( final Multiset.Entry< Label > e : it.entrySet() )
			{
				volume.add( e.getElement().id() );
			}
		}

		System.out.println( "volume size: " + volume.size() );

		// two dimensions more: from 'min minus one' to 'max plus one'
		int xWidth = ( volDim[ 0 ] + 2 );
		int xyWidth = xWidth * ( volDim[ 1 ] + 2 );

		double[] vertex_values = new double[ 8 ];

		for ( int cursorZ = 0; cursorZ < volDim[ 2 ] + 1; cursorZ++ )
		{
			for ( int cursorY = 0; cursorY < volDim[ 1 ] + 1; cursorY++ )
			{
				for ( int cursorX = 0; cursorX < volDim[ 0 ] + 1; cursorX++ )
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

					vertex_values[ 7 ] = volume.get( cursorZ * xyWidth + cursorY * xWidth + cursorX );
					vertex_values[ 3 ] = volume.get( cursorZ * xyWidth + cursorY * xWidth + ( cursorX + 1 ) );
					vertex_values[ 6 ] = volume.get( cursorZ * xyWidth + ( cursorY + 1 ) * xWidth + cursorX );
					vertex_values[ 2 ] = volume.get( cursorZ * xyWidth + ( cursorY + 1 ) * xWidth + ( cursorX + 1 ) );
					vertex_values[ 4 ] = volume.get( ( cursorZ + 1 ) * xyWidth + cursorY * xWidth + cursorX );
					vertex_values[ 0 ] = volume.get( ( cursorZ + 1 ) * xyWidth + cursorY * xWidth + ( cursorX + 1 ) );
					vertex_values[ 5 ] = volume.get( ( cursorZ + 1 ) * xyWidth + ( cursorY + 1 ) * xWidth + cursorX );
					vertex_values[ 1 ] = volume.get( ( cursorZ + 1 ) * xyWidth + ( cursorY + 1 ) * xWidth + ( cursorX + 1 ) );

					triangulation( vertex_values, cursorX, cursorY, cursorZ );

				}
			}
		}

		renameVerticesAndTriangles();
		hasValidSurface = true;

		return mesh;
	}

	/**
	 * 
	 * @param vertex_values
	 * @param cursorX
	 * @param cursorY
	 * @param cursorZ
	 */
	private void triangulation( double[] vertex_values, int cursorX, int cursorY, int cursorZ )
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
			if ( !interiorTest( vertex_values[ i ] ) )
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

		float[][] vertlist = new float[12][3];
		// Now create a triangulation of the isosurface in this cell.
		if (MarchingCubesTables.MC_EDGE_TABLE[tableIndex] != 0)
		{
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 1) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 0, vertex_values[0], vertex_values[1]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 0);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 2) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 1, vertex_values[1], vertex_values[2]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 1);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 4) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 2, vertex_values[2], vertex_values[3]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 2);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 8) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 3, vertex_values[3], vertex_values[0]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 3);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 16) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 4, vertex_values[4], vertex_values[5]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 4);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 32) != 0) {
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 5, vertex_values[5], vertex_values[6]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 5);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 64) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 6, vertex_values[6], vertex_values[7]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 6);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 128) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 7, vertex_values[7], vertex_values[4]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 7);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 256) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 8, vertex_values[0], vertex_values[4]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 8);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 512) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 9, vertex_values[1], vertex_values[5]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 9);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 1024) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 10, vertex_values[2], vertex_values[6]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 10);
				id2Point3dId.put(id, pt);
			}
			if ((MarchingCubesTables.MC_EDGE_TABLE[tableIndex] & 2048) != 0)
			{
				Point3dId pt = calculateIntersection(cursorX, cursorY, cursorZ, 11, vertex_values[3], vertex_values[7]);
				long id = getEdgeId(cursorX, cursorY, cursorZ, 11);
				id2Point3dId.put(id, pt);
			}

			for (int i = 0; MarchingCubesTables.MC_TRI_TABLE[tableIndex][i] != MarchingCubesTables.Invalid; i += 3)
			{
				Triangle triangle = new Triangle();
				long pointId0, pointId1, pointId2;
				pointId0 = getEdgeId(cursorX, cursorY, cursorZ, MarchingCubesTables.MC_TRI_TABLE[tableIndex][i]);
				pointId1 = getEdgeId(cursorX, cursorY, cursorZ, MarchingCubesTables.MC_TRI_TABLE[tableIndex][i + 1]);
				pointId2 = getEdgeId(cursorX, cursorY, cursorZ, MarchingCubesTables.MC_TRI_TABLE[tableIndex][i + 2]);
				triangle.point[0] = pointId0;
				triangle.point[1] = pointId1;
				triangle.point[2] = pointId2;
				triangleVector.add(triangle);
			}
		}
	}

	private void deleteSurface()
	{
		nCellsX = 0;
		nCellsY = 0;
		hasValidSurface = false;
	}

	private void renameVerticesAndTriangles()
	{
		long nextId = 0;
		Iterator<Entry<Long, Point3dId>> mapIterator = id2Point3dId.entrySet().iterator();
		Iterator<Triangle> vecIterator = triangleVector.iterator();
		while (mapIterator.hasNext()) {
			HashMap.Entry<Long, Point3dId> entry = mapIterator.next();
			entry.getValue().id = nextId;
			nextId++;
		}

		// Now rename triangles.
		while (vecIterator.hasNext())
		{
			Triangle next = vecIterator.next();
			for (int i = 0; i < 3; i++)
			{
				long newId = id2Point3dId.get(next.point[i]).id;
				next.point[i] = newId;
			}
		}

		// Copy all the vertices and triangles into two arrays so that they
		// can be efficiently accessed.
		// Copy vertices.
		int numberOfVertices = id2Point3dId.size();
		mesh.setNumberOfVertices(numberOfVertices);

		mapIterator = id2Point3dId.entrySet().iterator();
		float[][] vertices = new float[numberOfVertices][3];

		for (int i = 0; i < numberOfVertices; i++)
		{
			HashMap.Entry<Long, Point3dId> entry = mapIterator.next();
			vertices[i][0] = entry.getValue().x;
			vertices[i][1] = entry.getValue().y;
			vertices[i][2] = entry.getValue().z;
		}

		mesh.setVertices(vertices);

		// Copy vertex indices which make triangles.
		vecIterator = triangleVector.iterator();
		int numberOfTriangles = triangleVector.size();
		mesh.numberOfTriangles = numberOfTriangles;
		int[] faces = new int[numberOfTriangles * 3];
		for (int i = 0; i < numberOfTriangles; i++)
		{
			Triangle next = vecIterator.next();
			faces[i * 3] = (int) next.point[0];
			faces[i * 3 + 1] = (int) next.point[1];
			faces[i * 3 + 2] = (int) next.point[2];
		}

		mesh.setTriangles(faces);
		id2Point3dId.clear();
		triangleVector.clear();
	}

	private Point3dId calculateIntersection(int nX, int nY, int nZ, int nEdgeNo, double vertex_values,
			double vertex_values2)
	{
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

		p1.setPosition((v1x + offset[0]), 0);
		p1.setPosition((v1y + offset[1]), 1);
		p1.setPosition((v1z + offset[2]), 2);
		p2.setPosition((v2x + offset[0]), 0);
		p2.setPosition((v2y + offset[1]), 1);
		p2.setPosition((v2z + offset[2]), 2);

		if (interiorTest(vertex_values) && !interiorTest(vertex_values2))
		{
			return findSurfaceIntersection(p2, p1, vertex_values2, vertex_values);
		} else {
			return findSurfaceIntersection(p1, p2, vertex_values, vertex_values2);
		}
	}

	private long getEdgeId(long nX, long nY, long nZ, int nEdgeNo)
	{
		switch (nEdgeNo)
		{
		case 0:
			return getVertexId(nX, nY, nZ) + 1;
		case 1:
			return getVertexId(nX, nY + 1, nZ);
		case 2:
			return getVertexId(nX + 1, nY, nZ) + 1;
		case 3:
			return getVertexId(nX, nY, nZ);
		case 4:
			return getVertexId(nX, nY, nZ + 1) + 1;
		case 5:
			return getVertexId(nX, nY + 1, nZ + 1);
		case 6:
			return getVertexId(nX + 1, nY, nZ + 1) + 1;
		case 7:
			return getVertexId(nX, nY, nZ + 1);
		case 8:
			return getVertexId(nX, nY, nZ) + 2;
		case 9:
			return getVertexId(nX, nY + 1, nZ) + 2;
		case 10:
			return getVertexId(nX + 1, nY + 1, nZ) + 2;
		case 11:
			return getVertexId(nX + 1, nY, nZ) + 2;
		default:
			// Invalid edge no.
			return MarchingCubesTables.Invalid;
		}
	}

	private long getVertexId(long nX, long nY, long nZ)
	{
		return 3 * (nZ * (nCellsY + 1) * (nCellsX + 1) + nY * (nCellsX + 1) + nX);
	}

	/**
	 * Binary search for intersection. Given two points and its values,
	 * interpolates the position of the intersection.
	 * 
	 * @param p1
	 *            starting point
	 * @param p2
	 *            final point
	 * @param val1
	 *            value of starting point
	 * @param val2
	 *            value of final point.
	 * @return
	 */
	private Point3dId findSurfaceIntersection(RealPoint p1, RealPoint p2, double val1, double val2)
	{

		Point3dId interpolation = null;

		float mu = (float) 0.5;
		float delta = (float) 0.25;

		// assume that p1 is outside, p2 is inside
		//
		// mu == 0 -> p1, mu == 1 -> p2
		//
		// incrase mu -> go to inside
		// decrease mu -> go to outside

		for (long i = 0; i < 10; i++, delta /= 2.0)
		{

			float diffX = p2.getFloatPosition(0) - p1.getFloatPosition(0);
			float diffY = p2.getFloatPosition(1) - p1.getFloatPosition(1);
			float diffZ = p2.getFloatPosition(2) - p1.getFloatPosition(2);

			diffX = diffX * mu;
			diffY = diffY * mu;
			diffZ = diffZ * mu;

			diffX = diffX + p1.getFloatPosition(0);
			diffY = diffY + p1.getFloatPosition(1);
			diffZ = diffZ + p1.getFloatPosition(2);

			RealPoint diff = new RealPoint(diffX, diffY, diffZ);
			interpolation = new Point3dId(diff);// p1 + mu*(p2-p1);

			if (interiorTest(val1 + mu * (val2 - val1)))
				mu -= delta; // go to outside
			else
				mu += delta; // go to inside
		}

		return interpolation;
	}

	/**
	 * Checks if the given value is equal or above a certain threshold. This
	 * comparison is dependent on the variable {@link #acceptExactly}
	 * 
	 * @param vertexValue
	 *            value that will be compared with the isolevel
	 * @return true if it comply with the comparison, false otherwise.
	 */
	private boolean interiorTest(double vertexValue)
	{

		if (acceptExactly)
		{
			if ((int) vertexValue == isoLevel)
			{
				return true;
			} else
			{
				return false;
			}
		} else
		{
			if ((int) vertexValue < isoLevel)
			{
				return true;
			} else
			{
				return false;
			}
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
		return Views.flatIterable(Views.interval(extended, new FinalInterval(new long[] { cursorX, cursorY, cursorZ },
				new long[] { cursorX + 1, cursorY + 1, cursorZ + 1 }))).cursor();
	}
}
