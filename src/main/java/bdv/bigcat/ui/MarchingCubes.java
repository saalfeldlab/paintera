package bdv.bigcat.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import graphics.scenery.Mesh;
import net.imglib2.RealPoint;

/**
 * MarchingCubes from
 * https://github.com/funkey/sg_gui/blob/master/MarchingCubes.h
 */
public class MarchingCubes< T >
{
	/** Log */
	private static final Logger LOGGER = Logger.getLogger( MarchingCubes.class.getName() );

	/** List of Point3ds which form the isosurface. */
	HashMap< Long, Point3dId > id2Point3dId = new HashMap< Long, Point3dId >();

	/** The number of vertices which make up the isosurface. */
	long nVertices;

	/** The number of triangles which make up the isosurface. */
	long nTriangles;

	/** The number of normals. */
	long nNormals;

	/** the mesh that represents the surface. */
	Mesh mesh;

	/** List of Triangles which form the triangulation of the isosurface. */
	Vector< TriangleId > triangleVector = new Vector< TriangleId >();

	/** No. of cells in x, y, and z directions. */
	long nCellsX, nCellsY, nCellsZ;

	/** Cell length in x, y, and z directions. */
	float cellSizeX, cellSizeY, cellSizeZ;

	/** The isosurface value. */
	T tIsoLevel;

	/** Indicates whether a valid surface is present. */
	boolean bValidSurface;

	private static final int Invalid = -1;

	/** Lookup table used in the construction of the isosurface. */
	private static final int edgeTable[] = {
			0x0, 0x109, 0x203, 0x30a, 0x406, 0x50f, 0x605, 0x70c,
			0x80c, 0x905, 0xa0f, 0xb06, 0xc0a, 0xd03, 0xe09, 0xf00,
			0x190, 0x99, 0x393, 0x29a, 0x596, 0x49f, 0x795, 0x69c,
			0x99c, 0x895, 0xb9f, 0xa96, 0xd9a, 0xc93, 0xf99, 0xe90,
			0x230, 0x339, 0x33, 0x13a, 0x636, 0x73f, 0x435, 0x53c,
			0xa3c, 0xb35, 0x83f, 0x936, 0xe3a, 0xf33, 0xc39, 0xd30,
			0x3a0, 0x2a9, 0x1a3, 0xaa, 0x7a6, 0x6af, 0x5a5, 0x4ac,
			0xbac, 0xaa5, 0x9af, 0x8a6, 0xfaa, 0xea3, 0xda9, 0xca0,
			0x460, 0x569, 0x663, 0x76a, 0x66, 0x16f, 0x265, 0x36c,
			0xc6c, 0xd65, 0xe6f, 0xf66, 0x86a, 0x963, 0xa69, 0xb60,
			0x5f0, 0x4f9, 0x7f3, 0x6fa, 0x1f6, 0xff, 0x3f5, 0x2fc,
			0xdfc, 0xcf5, 0xfff, 0xef6, 0x9fa, 0x8f3, 0xbf9, 0xaf0,
			0x650, 0x759, 0x453, 0x55a, 0x256, 0x35f, 0x55, 0x15c,
			0xe5c, 0xf55, 0xc5f, 0xd56, 0xa5a, 0xb53, 0x859, 0x950,
			0x7c0, 0x6c9, 0x5c3, 0x4ca, 0x3c6, 0x2cf, 0x1c5, 0xcc,
			0xfcc, 0xec5, 0xdcf, 0xcc6, 0xbca, 0xac3, 0x9c9, 0x8c0,
			0x8c0, 0x9c9, 0xac3, 0xbca, 0xcc6, 0xdcf, 0xec5, 0xfcc,
			0xcc, 0x1c5, 0x2cf, 0x3c6, 0x4ca, 0x5c3, 0x6c9, 0x7c0,
			0x950, 0x859, 0xb53, 0xa5a, 0xd56, 0xc5f, 0xf55, 0xe5c,
			0x15c, 0x55, 0x35f, 0x256, 0x55a, 0x453, 0x759, 0x650,
			0xaf0, 0xbf9, 0x8f3, 0x9fa, 0xef6, 0xfff, 0xcf5, 0xdfc,
			0x2fc, 0x3f5, 0xff, 0x1f6, 0x6fa, 0x7f3, 0x4f9, 0x5f0,
			0xb60, 0xa69, 0x963, 0x86a, 0xf66, 0xe6f, 0xd65, 0xc6c,
			0x36c, 0x265, 0x16f, 0x66, 0x76a, 0x663, 0x569, 0x460,
			0xca0, 0xda9, 0xea3, 0xfaa, 0x8a6, 0x9af, 0xaa5, 0xbac,
			0x4ac, 0x5a5, 0x6af, 0x7a6, 0xaa, 0x1a3, 0x2a9, 0x3a0,
			0xd30, 0xc39, 0xf33, 0xe3a, 0x936, 0x83f, 0xb35, 0xa3c,
			0x53c, 0x435, 0x73f, 0x636, 0x13a, 0x33, 0x339, 0x230,
			0xe90, 0xf99, 0xc93, 0xd9a, 0xa96, 0xb9f, 0x895, 0x99c,
			0x69c, 0x795, 0x49f, 0x596, 0x29a, 0x393, 0x99, 0x190,
			0xf00, 0xe09, 0xd03, 0xc0a, 0xb06, 0xa0f, 0x905, 0x80c,
			0x70c, 0x605, 0x50f, 0x406, 0x30a, 0x203, 0x109, 0x0
	};

	private static final int triTable[][] = {
			{ Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 1, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 8, 3, 9, 8, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 3, 1, 2, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 2, 10, 0, 2, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 8, 3, 2, 10, 8, 10, 9, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 11, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 11, 2, 8, 11, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 9, 0, 2, 3, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 11, 2, 1, 9, 11, 9, 8, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 10, 1, 11, 10, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 10, 1, 0, 8, 10, 8, 11, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 9, 0, 3, 11, 9, 11, 10, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 8, 10, 10, 8, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 7, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 3, 0, 7, 3, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 1, 9, 8, 4, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 1, 9, 4, 7, 1, 7, 3, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 10, 8, 4, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 4, 7, 3, 0, 4, 1, 2, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 2, 10, 9, 0, 2, 8, 4, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 10, 9, 2, 9, 7, 2, 7, 3, 7, 9, 4, Invalid, Invalid, Invalid, Invalid },
			{ 8, 4, 7, 3, 11, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 11, 4, 7, 11, 2, 4, 2, 0, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 0, 1, 8, 4, 7, 2, 3, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 7, 11, 9, 4, 11, 9, 11, 2, 9, 2, 1, Invalid, Invalid, Invalid, Invalid },
			{ 3, 10, 1, 3, 11, 10, 7, 8, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 11, 10, 1, 4, 11, 1, 0, 4, 7, 11, 4, Invalid, Invalid, Invalid, Invalid },
			{ 4, 7, 8, 9, 0, 11, 9, 11, 10, 11, 0, 3, Invalid, Invalid, Invalid, Invalid },
			{ 4, 7, 11, 4, 11, 9, 9, 11, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 4, 0, 8, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 5, 4, 1, 5, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 5, 4, 8, 3, 5, 3, 1, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 10, 9, 5, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 0, 8, 1, 2, 10, 4, 9, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 2, 10, 5, 4, 2, 4, 0, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 10, 5, 3, 2, 5, 3, 5, 4, 3, 4, 8, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 4, 2, 3, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 11, 2, 0, 8, 11, 4, 9, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 5, 4, 0, 1, 5, 2, 3, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 1, 5, 2, 5, 8, 2, 8, 11, 4, 8, 5, Invalid, Invalid, Invalid, Invalid },
			{ 10, 3, 11, 10, 1, 3, 9, 5, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 9, 5, 0, 8, 1, 8, 10, 1, 8, 11, 10, Invalid, Invalid, Invalid, Invalid },
			{ 5, 4, 0, 5, 0, 11, 5, 11, 10, 11, 0, 3, Invalid, Invalid, Invalid, Invalid },
			{ 5, 4, 8, 5, 8, 10, 10, 8, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 7, 8, 5, 7, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 3, 0, 9, 5, 3, 5, 7, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 7, 8, 0, 1, 7, 1, 5, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 5, 3, 3, 5, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 7, 8, 9, 5, 7, 10, 1, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 1, 2, 9, 5, 0, 5, 3, 0, 5, 7, 3, Invalid, Invalid, Invalid, Invalid },
			{ 8, 0, 2, 8, 2, 5, 8, 5, 7, 10, 5, 2, Invalid, Invalid, Invalid, Invalid },
			{ 2, 10, 5, 2, 5, 3, 3, 5, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 7, 9, 5, 7, 8, 9, 3, 11, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 7, 9, 7, 2, 9, 2, 0, 2, 7, 11, Invalid, Invalid, Invalid, Invalid },
			{ 2, 3, 11, 0, 1, 8, 1, 7, 8, 1, 5, 7, Invalid, Invalid, Invalid, Invalid },
			{ 11, 2, 1, 11, 1, 7, 7, 1, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 8, 8, 5, 7, 10, 1, 3, 10, 3, 11, Invalid, Invalid, Invalid, Invalid },
			{ 5, 7, 0, 5, 0, 9, 7, 11, 0, 1, 0, 10, 11, 10, 0, Invalid },
			{ 11, 10, 0, 11, 0, 3, 10, 5, 0, 8, 0, 7, 5, 7, 0, Invalid },
			{ 11, 10, 5, 7, 11, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 6, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 3, 5, 10, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 0, 1, 5, 10, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 8, 3, 1, 9, 8, 5, 10, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 6, 5, 2, 6, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 6, 5, 1, 2, 6, 3, 0, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 6, 5, 9, 0, 6, 0, 2, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 9, 8, 5, 8, 2, 5, 2, 6, 3, 2, 8, Invalid, Invalid, Invalid, Invalid },
			{ 2, 3, 11, 10, 6, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 11, 0, 8, 11, 2, 0, 10, 6, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 1, 9, 2, 3, 11, 5, 10, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 10, 6, 1, 9, 2, 9, 11, 2, 9, 8, 11, Invalid, Invalid, Invalid, Invalid },
			{ 6, 3, 11, 6, 5, 3, 5, 1, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 11, 0, 11, 5, 0, 5, 1, 5, 11, 6, Invalid, Invalid, Invalid, Invalid },
			{ 3, 11, 6, 0, 3, 6, 0, 6, 5, 0, 5, 9, Invalid, Invalid, Invalid, Invalid },
			{ 6, 5, 9, 6, 9, 11, 11, 9, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 10, 6, 4, 7, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 3, 0, 4, 7, 3, 6, 5, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 9, 0, 5, 10, 6, 8, 4, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 6, 5, 1, 9, 7, 1, 7, 3, 7, 9, 4, Invalid, Invalid, Invalid, Invalid },
			{ 6, 1, 2, 6, 5, 1, 4, 7, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 5, 5, 2, 6, 3, 0, 4, 3, 4, 7, Invalid, Invalid, Invalid, Invalid },
			{ 8, 4, 7, 9, 0, 5, 0, 6, 5, 0, 2, 6, Invalid, Invalid, Invalid, Invalid },
			{ 7, 3, 9, 7, 9, 4, 3, 2, 9, 5, 9, 6, 2, 6, 9, Invalid },
			{ 3, 11, 2, 7, 8, 4, 10, 6, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 10, 6, 4, 7, 2, 4, 2, 0, 2, 7, 11, Invalid, Invalid, Invalid, Invalid },
			{ 0, 1, 9, 4, 7, 8, 2, 3, 11, 5, 10, 6, Invalid, Invalid, Invalid, Invalid },
			{ 9, 2, 1, 9, 11, 2, 9, 4, 11, 7, 11, 4, 5, 10, 6, Invalid },
			{ 8, 4, 7, 3, 11, 5, 3, 5, 1, 5, 11, 6, Invalid, Invalid, Invalid, Invalid },
			{ 5, 1, 11, 5, 11, 6, 1, 0, 11, 7, 11, 4, 0, 4, 11, Invalid },
			{ 0, 5, 9, 0, 6, 5, 0, 3, 6, 11, 6, 3, 8, 4, 7, Invalid },
			{ 6, 5, 9, 6, 9, 11, 4, 7, 9, 7, 11, 9, Invalid, Invalid, Invalid, Invalid },
			{ 10, 4, 9, 6, 4, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 10, 6, 4, 9, 10, 0, 8, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 0, 1, 10, 6, 0, 6, 4, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 3, 1, 8, 1, 6, 8, 6, 4, 6, 1, 10, Invalid, Invalid, Invalid, Invalid },
			{ 1, 4, 9, 1, 2, 4, 2, 6, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 0, 8, 1, 2, 9, 2, 4, 9, 2, 6, 4, Invalid, Invalid, Invalid, Invalid },
			{ 0, 2, 4, 4, 2, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 3, 2, 8, 2, 4, 4, 2, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 4, 9, 10, 6, 4, 11, 2, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 2, 2, 8, 11, 4, 9, 10, 4, 10, 6, Invalid, Invalid, Invalid, Invalid },
			{ 3, 11, 2, 0, 1, 6, 0, 6, 4, 6, 1, 10, Invalid, Invalid, Invalid, Invalid },
			{ 6, 4, 1, 6, 1, 10, 4, 8, 1, 2, 1, 11, 8, 11, 1, Invalid },
			{ 9, 6, 4, 9, 3, 6, 9, 1, 3, 11, 6, 3, Invalid, Invalid, Invalid, Invalid },
			{ 8, 11, 1, 8, 1, 0, 11, 6, 1, 9, 1, 4, 6, 4, 1, Invalid },
			{ 3, 11, 6, 3, 6, 0, 0, 6, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 6, 4, 8, 11, 6, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 7, 10, 6, 7, 8, 10, 8, 9, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 7, 3, 0, 10, 7, 0, 9, 10, 6, 7, 10, Invalid, Invalid, Invalid, Invalid },
			{ 10, 6, 7, 1, 10, 7, 1, 7, 8, 1, 8, 0, Invalid, Invalid, Invalid, Invalid },
			{ 10, 6, 7, 10, 7, 1, 1, 7, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 6, 1, 6, 8, 1, 8, 9, 8, 6, 7, Invalid, Invalid, Invalid, Invalid },
			{ 2, 6, 9, 2, 9, 1, 6, 7, 9, 0, 9, 3, 7, 3, 9, Invalid },
			{ 7, 8, 0, 7, 0, 6, 6, 0, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 7, 3, 2, 6, 7, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 3, 11, 10, 6, 8, 10, 8, 9, 8, 6, 7, Invalid, Invalid, Invalid, Invalid },
			{ 2, 0, 7, 2, 7, 11, 0, 9, 7, 6, 7, 10, 9, 10, 7, Invalid },
			{ 1, 8, 0, 1, 7, 8, 1, 10, 7, 6, 7, 10, 2, 3, 11, Invalid },
			{ 11, 2, 1, 11, 1, 7, 10, 6, 1, 6, 7, 1, Invalid, Invalid, Invalid, Invalid },
			{ 8, 9, 6, 8, 6, 7, 9, 1, 6, 11, 6, 3, 1, 3, 6, Invalid },
			{ 0, 9, 1, 11, 6, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 7, 8, 0, 7, 0, 6, 3, 11, 0, 11, 6, 0, Invalid, Invalid, Invalid, Invalid },
			{ 7, 11, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 7, 6, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 0, 8, 11, 7, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 1, 9, 11, 7, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 1, 9, 8, 3, 1, 11, 7, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 1, 2, 6, 11, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 10, 3, 0, 8, 6, 11, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 9, 0, 2, 10, 9, 6, 11, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 6, 11, 7, 2, 10, 3, 10, 8, 3, 10, 9, 8, Invalid, Invalid, Invalid, Invalid },
			{ 7, 2, 3, 6, 2, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 7, 0, 8, 7, 6, 0, 6, 2, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 7, 6, 2, 3, 7, 0, 1, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 6, 2, 1, 8, 6, 1, 9, 8, 8, 7, 6, Invalid, Invalid, Invalid, Invalid },
			{ 10, 7, 6, 10, 1, 7, 1, 3, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 7, 6, 1, 7, 10, 1, 8, 7, 1, 0, 8, Invalid, Invalid, Invalid, Invalid },
			{ 0, 3, 7, 0, 7, 10, 0, 10, 9, 6, 10, 7, Invalid, Invalid, Invalid, Invalid },
			{ 7, 6, 10, 7, 10, 8, 8, 10, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 6, 8, 4, 11, 8, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 6, 11, 3, 0, 6, 0, 4, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 6, 11, 8, 4, 6, 9, 0, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 4, 6, 9, 6, 3, 9, 3, 1, 11, 3, 6, Invalid, Invalid, Invalid, Invalid },
			{ 6, 8, 4, 6, 11, 8, 2, 10, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 10, 3, 0, 11, 0, 6, 11, 0, 4, 6, Invalid, Invalid, Invalid, Invalid },
			{ 4, 11, 8, 4, 6, 11, 0, 2, 9, 2, 10, 9, Invalid, Invalid, Invalid, Invalid },
			{ 10, 9, 3, 10, 3, 2, 9, 4, 3, 11, 3, 6, 4, 6, 3, Invalid },
			{ 8, 2, 3, 8, 4, 2, 4, 6, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 4, 2, 4, 6, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 9, 0, 2, 3, 4, 2, 4, 6, 4, 3, 8, Invalid, Invalid, Invalid, Invalid },
			{ 1, 9, 4, 1, 4, 2, 2, 4, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 1, 3, 8, 6, 1, 8, 4, 6, 6, 10, 1, Invalid, Invalid, Invalid, Invalid },
			{ 10, 1, 0, 10, 0, 6, 6, 0, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 6, 3, 4, 3, 8, 6, 10, 3, 0, 3, 9, 10, 9, 3, Invalid },
			{ 10, 9, 4, 6, 10, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 9, 5, 7, 6, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 3, 4, 9, 5, 11, 7, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 0, 1, 5, 4, 0, 7, 6, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 11, 7, 6, 8, 3, 4, 3, 5, 4, 3, 1, 5, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 4, 10, 1, 2, 7, 6, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 6, 11, 7, 1, 2, 10, 0, 8, 3, 4, 9, 5, Invalid, Invalid, Invalid, Invalid },
			{ 7, 6, 11, 5, 4, 10, 4, 2, 10, 4, 0, 2, Invalid, Invalid, Invalid, Invalid },
			{ 3, 4, 8, 3, 5, 4, 3, 2, 5, 10, 5, 2, 11, 7, 6, Invalid },
			{ 7, 2, 3, 7, 6, 2, 5, 4, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 4, 0, 8, 6, 0, 6, 2, 6, 8, 7, Invalid, Invalid, Invalid, Invalid },
			{ 3, 6, 2, 3, 7, 6, 1, 5, 0, 5, 4, 0, Invalid, Invalid, Invalid, Invalid },
			{ 6, 2, 8, 6, 8, 7, 2, 1, 8, 4, 8, 5, 1, 5, 8, Invalid },
			{ 9, 5, 4, 10, 1, 6, 1, 7, 6, 1, 3, 7, Invalid, Invalid, Invalid, Invalid },
			{ 1, 6, 10, 1, 7, 6, 1, 0, 7, 8, 7, 0, 9, 5, 4, Invalid },
			{ 4, 0, 10, 4, 10, 5, 0, 3, 10, 6, 10, 7, 3, 7, 10, Invalid },
			{ 7, 6, 10, 7, 10, 8, 5, 4, 10, 4, 8, 10, Invalid, Invalid, Invalid, Invalid },
			{ 6, 9, 5, 6, 11, 9, 11, 8, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 6, 11, 0, 6, 3, 0, 5, 6, 0, 9, 5, Invalid, Invalid, Invalid, Invalid },
			{ 0, 11, 8, 0, 5, 11, 0, 1, 5, 5, 6, 11, Invalid, Invalid, Invalid, Invalid },
			{ 6, 11, 3, 6, 3, 5, 5, 3, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 10, 9, 5, 11, 9, 11, 8, 11, 5, 6, Invalid, Invalid, Invalid, Invalid },
			{ 0, 11, 3, 0, 6, 11, 0, 9, 6, 5, 6, 9, 1, 2, 10, Invalid },
			{ 11, 8, 5, 11, 5, 6, 8, 0, 5, 10, 5, 2, 0, 2, 5, Invalid },
			{ 6, 11, 3, 6, 3, 5, 2, 10, 3, 10, 5, 3, Invalid, Invalid, Invalid, Invalid },
			{ 5, 8, 9, 5, 2, 8, 5, 6, 2, 3, 8, 2, Invalid, Invalid, Invalid, Invalid },
			{ 9, 5, 6, 9, 6, 0, 0, 6, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 5, 8, 1, 8, 0, 5, 6, 8, 3, 8, 2, 6, 2, 8, Invalid },
			{ 1, 5, 6, 2, 1, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 3, 6, 1, 6, 10, 3, 8, 6, 5, 6, 9, 8, 9, 6, Invalid },
			{ 10, 1, 0, 10, 0, 6, 9, 5, 0, 5, 6, 0, Invalid, Invalid, Invalid, Invalid },
			{ 0, 3, 8, 5, 6, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 5, 6, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 11, 5, 10, 7, 5, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 11, 5, 10, 11, 7, 5, 8, 3, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 11, 7, 5, 10, 11, 1, 9, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 10, 7, 5, 10, 11, 7, 9, 8, 1, 8, 3, 1, Invalid, Invalid, Invalid, Invalid },
			{ 11, 1, 2, 11, 7, 1, 7, 5, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 3, 1, 2, 7, 1, 7, 5, 7, 2, 11, Invalid, Invalid, Invalid, Invalid },
			{ 9, 7, 5, 9, 2, 7, 9, 0, 2, 2, 11, 7, Invalid, Invalid, Invalid, Invalid },
			{ 7, 5, 2, 7, 2, 11, 5, 9, 2, 3, 2, 8, 9, 8, 2, Invalid },
			{ 2, 5, 10, 2, 3, 5, 3, 7, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 2, 0, 8, 5, 2, 8, 7, 5, 10, 2, 5, Invalid, Invalid, Invalid, Invalid },
			{ 9, 0, 1, 5, 10, 3, 5, 3, 7, 3, 10, 2, Invalid, Invalid, Invalid, Invalid },
			{ 9, 8, 2, 9, 2, 1, 8, 7, 2, 10, 2, 5, 7, 5, 2, Invalid },
			{ 1, 3, 5, 3, 7, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 7, 0, 7, 1, 1, 7, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 0, 3, 9, 3, 5, 5, 3, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 8, 7, 5, 9, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 8, 4, 5, 10, 8, 10, 11, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 5, 0, 4, 5, 11, 0, 5, 10, 11, 11, 3, 0, Invalid, Invalid, Invalid, Invalid },
			{ 0, 1, 9, 8, 4, 10, 8, 10, 11, 10, 4, 5, Invalid, Invalid, Invalid, Invalid },
			{ 10, 11, 4, 10, 4, 5, 11, 3, 4, 9, 4, 1, 3, 1, 4, Invalid },
			{ 2, 5, 1, 2, 8, 5, 2, 11, 8, 4, 5, 8, Invalid, Invalid, Invalid, Invalid },
			{ 0, 4, 11, 0, 11, 3, 4, 5, 11, 2, 11, 1, 5, 1, 11, Invalid },
			{ 0, 2, 5, 0, 5, 9, 2, 11, 5, 4, 5, 8, 11, 8, 5, Invalid },
			{ 9, 4, 5, 2, 11, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 5, 10, 3, 5, 2, 3, 4, 5, 3, 8, 4, Invalid, Invalid, Invalid, Invalid },
			{ 5, 10, 2, 5, 2, 4, 4, 2, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 10, 2, 3, 5, 10, 3, 8, 5, 4, 5, 8, 0, 1, 9, Invalid },
			{ 5, 10, 2, 5, 2, 4, 1, 9, 2, 9, 4, 2, Invalid, Invalid, Invalid, Invalid },
			{ 8, 4, 5, 8, 5, 3, 3, 5, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 4, 5, 1, 0, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 8, 4, 5, 8, 5, 3, 9, 0, 5, 0, 3, 5, Invalid, Invalid, Invalid, Invalid },
			{ 9, 4, 5, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 11, 7, 4, 9, 11, 9, 10, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 8, 3, 4, 9, 7, 9, 11, 7, 9, 10, 11, Invalid, Invalid, Invalid, Invalid },
			{ 1, 10, 11, 1, 11, 4, 1, 4, 0, 7, 4, 11, Invalid, Invalid, Invalid, Invalid },
			{ 3, 1, 4, 3, 4, 8, 1, 10, 4, 7, 4, 11, 10, 11, 4, Invalid },
			{ 4, 11, 7, 9, 11, 4, 9, 2, 11, 9, 1, 2, Invalid, Invalid, Invalid, Invalid },
			{ 9, 7, 4, 9, 11, 7, 9, 1, 11, 2, 11, 1, 0, 8, 3, Invalid },
			{ 11, 7, 4, 11, 4, 2, 2, 4, 0, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 11, 7, 4, 11, 4, 2, 8, 3, 4, 3, 2, 4, Invalid, Invalid, Invalid, Invalid },
			{ 2, 9, 10, 2, 7, 9, 2, 3, 7, 7, 4, 9, Invalid, Invalid, Invalid, Invalid },
			{ 9, 10, 7, 9, 7, 4, 10, 2, 7, 8, 7, 0, 2, 0, 7, Invalid },
			{ 3, 7, 10, 3, 10, 2, 7, 4, 10, 1, 10, 0, 4, 0, 10, Invalid },
			{ 1, 10, 2, 8, 7, 4, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 9, 1, 4, 1, 7, 7, 1, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 9, 1, 4, 1, 7, 0, 8, 1, 8, 7, 1, Invalid, Invalid, Invalid, Invalid },
			{ 4, 0, 3, 7, 4, 3, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 4, 8, 7, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 10, 8, 10, 11, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 0, 9, 3, 9, 11, 11, 9, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 1, 10, 0, 10, 8, 8, 10, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 1, 10, 11, 3, 10, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 2, 11, 1, 11, 9, 9, 11, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 0, 9, 3, 9, 11, 1, 2, 9, 2, 11, 9, Invalid, Invalid, Invalid, Invalid },
			{ 0, 2, 11, 8, 0, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 3, 2, 11, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 3, 8, 2, 8, 10, 10, 8, 9, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 9, 10, 2, 0, 9, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 2, 3, 8, 2, 8, 10, 0, 1, 8, 1, 10, 8, Invalid, Invalid, Invalid, Invalid },
			{ 1, 10, 2, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 1, 3, 8, 9, 1, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 9, 1, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ 0, 3, 8, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid },
			{ Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid, Invalid }
	};

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

//		// conversion to regular Point3d
//		operator Point3d () {
//			return Point3d(x, y, z);
//		}
	}

	/**
	 * Triples of points that form a triangle.
	 */
	class TriangleId
	{
		long[] pointId = new long[ 3 ];
	}

	class Volume
	{
		long[][][] volume = new long[ 3 ][ 3 ][ 3 ];

		public long getValue( int x, int y, int z )
		{
			return volume[ x ][ y ][ z ];
		}
	}

	class AcceptAbove< T >
	{

		T threshold;

		public AcceptAbove( T threshold_ )
		{
			threshold = threshold_;
		}
//
//		boolean operator()(T value) const {
//
//			return value > threshold;
//		}
	}

	class AcceptExactly< T >
	{

		T reference;

		public AcceptExactly( T reference_ )
		{
			reference = reference_;
		}
//
//		boolean operator()(T value) const {
//
//			return value == threshold;
//		}
	}

//	private getValue<T>(const Volume volume, int x, int y, int z) {
//
//		return volume.getValue( x, y, z);
////				volume.getBoundingBox().min().x() + (x-1)*_cellSizeX,
////				volume.getBoundingBox().min().y() + (y-1)*_cellSizeY,
////				volume.getBoundingBox().min().z() + (z-1)*_cellSizeZ);
//	}

	public MarchingCubes()
	{
		cellSizeX = 0;
		cellSizeY = 0;
		cellSizeZ = 0;
		nCellsX = 0;
		nCellsY = 0;
		nCellsZ = 0;
		nTriangles = 0;
		nNormals = 0;
		nVertices = 0;
		bValidSurface = false;
	}

	public Mesh generateSurface( float sizeX, float sizeY, float sizeZ )
	{

		if ( bValidSurface )
			deleteSurface();

		mesh = new Mesh();

		float width = 0;// volume.getBoundingBox().width();
		float height = 0;// volume.getBoundingBox().height();
		float depth = 0;// volume.getBoundingBox().depth();

		nCellsX = ( long ) Math.ceil( width / sizeX ) + 1;
		nCellsY = ( long ) Math.ceil( height / sizeY ) + 1;
		nCellsZ = ( long ) Math.ceil( depth / sizeZ ) + 1;
		cellSizeX = sizeX;
		cellSizeY = sizeY;
		cellSizeZ = sizeZ;

		LOGGER.log( Level.FINE, "creating mesh for " + width + "x" + height + "x" + depth
				+ " volume with " + nCellsX + "x" + nCellsY + "x" + nCellsZ + " cells" );

		// Generate isosurface.
		for ( long z = 0; z < nCellsZ; z++ )
			for ( long y = 0; y < nCellsY; y++ )
				for ( long x = 0; x < nCellsX; x++ )
				{
					// Calculate table lookup index from those
					// vertices which are below the isolevel.
					int tableIndex = 0;
//					if (!interiorTest(getValue(volume, x, y, z)))
						tableIndex |= 1;
//					if (!interiorTest(getValue(volume, x, y+1, z)))
						tableIndex |= 2;
//					if (!interiorTest(getValue(volume, x+1, y+1, z)))
						tableIndex |= 4;
//					if (!interiorTest(getValue(volume, x+1, y, z)))
						tableIndex |= 8;
//					if (!interiorTest(getValue(volume, x, y, z+1)))
						tableIndex |= 16;
//					if (!interiorTest(getValue(volume, x, y+1, z+1)))
						tableIndex |= 32;
//					if (!interiorTest(getValue(volume, x+1, y+1, z+1)))
						tableIndex |= 64;
//					if (!interiorTest(getValue(volume, x+1, y, z+1)))
						tableIndex |= 128;

					// Now create a triangulation of the isosurface in this
					// cell.
					if ( edgeTable[ tableIndex ] != 0 )
					{
						if ( ( edgeTable[ tableIndex ] & 8 ) != 0 )
						{
							Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 3);
							long id = getEdgeId( x, y, z, 3 );
//							_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
						}
						if ( ( edgeTable[ tableIndex ] & 1 ) != 0 )
						{
							Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 0);
							long id = getEdgeId( x, y, z, 0 );
//							_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
						}
						if ( ( edgeTable[ tableIndex ] & 256 ) != 0 )
						{
							Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 8);
							long id = getEdgeId( x, y, z, 8 );
//							_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
						}

						if ( x == nCellsX - 1 )
						{
							if ( ( edgeTable[ tableIndex ] & 4 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 2);
								long id = getEdgeId( x, y, z, 2 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
							if ( ( edgeTable[ tableIndex ] & 2048 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 11);
								long id = getEdgeId( x, y, z, 11 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
						}
						if ( y == nCellsY - 1 )
						{
							if ( ( edgeTable[ tableIndex ] & 2 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 1);
								long id = getEdgeId( x, y, z, 1 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
							if ( ( edgeTable[ tableIndex ] & 512 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 9);
								long id = getEdgeId( x, y, z, 9 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
						}
						if ( z == nCellsZ - 1 )
						{
							if ( ( edgeTable[ tableIndex ] & 16 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 4);
								long id = getEdgeId( x, y, z, 4 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
							if ( ( edgeTable[ tableIndex ] & 128 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 7);
								long id = getEdgeId( x, y, z, 7 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
						}
						if ( ( x == nCellsX - 1 ) && ( y == nCellsY - 1 ) )
							if ( ( edgeTable[ tableIndex ] & 1024 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 10);
								long id = getEdgeId( x, y, z, 10 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
						if ( ( x == nCellsX - 1 ) && ( z == nCellsZ - 1 ) )
							if ( ( edgeTable[ tableIndex ] & 64 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 6);
								long id = getEdgeId( x, y, z, 6 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}
						if ( ( y == nCellsY - 1 ) && ( z == nCellsZ - 1 ) )
							if ( ( edgeTable[ tableIndex ] & 32 ) != 0 )
							{
								Point3dId pt = calculateIntersection(volume, interiorTest, x, y, z, 5);
								long id = getEdgeId( x, y, z, 5 );
//								_i2pt3idVertices.insert(Id2Point3dId::value_type(id, pt));
							}

						for ( int i = 0; triTable[ tableIndex ][ i ] != Invalid; i += 3 )
						{
							TriangleId triangle = new TriangleId();
							long pointId0, pointId1, pointId2;
							pointId0 = getEdgeId( x, y, z, triTable[ tableIndex ][ i ] );
							pointId1 = getEdgeId( x, y, z, triTable[ tableIndex ][ i + 1 ] );
							pointId2 = getEdgeId( x, y, z, triTable[ tableIndex ][ i + 2 ] );
							triangle.pointId[ 0 ] = pointId0;
							triangle.pointId[ 1 ] = pointId1;
							triangle.pointId[ 2 ] = pointId2;
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
		nTriangles = 0;
		nNormals = 0;
		nVertices = 0;
		bValidSurface = false;
	}

	private void renameVerticesAndTriangles()
	{
		long nextId = 0;
		Iterator< Entry< Long, Point3dId > > mapIterator = id2Point3dId.entrySet().iterator();
		Iterator< TriangleId > vecIterator = triangleVector.iterator();

		// Rename vertices.
		while ( mapIterator.hasNext() )
		{
			HashMap.Entry< Long, Point3dId > entry = mapIterator.next();
			entry.getValue().newId = nextId;
			nextId++;
		}

		// Now rename triangles.
		while ( vecIterator.hasNext() )
		{
			for ( int i = 0; i < 3; i++ )
			{
				TriangleId next = vecIterator.next();
				long newId = 0;// id2Point3dId[next.pointId[i]].newId;
				next.pointId[ i ] = newId;
			}
		}

		// Copy all the vertices and triangles into two arrays so that they
		// can be efficiently accessed.
		// Copy vertices.
//		mapIterator = i2pt3idVertices.begin();
//		nVertices = i2pt3idVertices.size();
//		mesh.setVertexSize( nVertices );

		LOGGER.log( Level.FINE, "created a mesh with " + nVertices + " vertices" );

//		for (long i = 0; i < nVertices; i++, mapIterator++)
//			mesh->setVertex(i, mapIterator->second);

		// Copy vertex indices which make triangles.
//		vecIterator = triangleVector.begin();
//		nTriangles = triangleVector.size();
//		mesh->setNumTriangles(nTriangles);

		for ( int i = 0; i < nTriangles; i++, vecIterator.next() )
		{
//			mesh.setTriangle(i,vecIterator.pointId[0], vecIteratorpointId[1], vecIterator.pointId[2]);
		}

//		i2pt3idVertices.clear();
		triangleVector.clear();
	}

	private void calculateNormals()
	{
		nNormals = nVertices;

		// Set all normals to 0.
		for ( long i = 0; i < nNormals; i++ )
		{
//			mesh.setNormal(i, Vector3d(0, 0, 0));
		}

		// Calculate normals.
		for ( long i = 0; i < nTriangles; i++ )
		{
//			Vector3d vec1, vec2, normal;
//			long id0, id1, id2;
//			id0 = mesh.getTriangle(i).v0;
//			id1 = mesh.getTriangle(i).v1;
//			id2 = mesh.getTriangle(i).v2;
//			vec1 = mesh.getVertex(id1) - mesh.getVertex(id0);
//			vec2 = mesh.getVertex(id2) - mesh.getVertex(id0);
//			normal.x() = vec1.z()*vec2.y() - vec1.y()*vec2.z();
//			normal.y() = vec1.x()*vec2.z() - vec1.z()*vec2.x();
//			normal.z() = vec1.y()*vec2.x() - vec1.x()*vec2.y();
//			mesh.getNormal(id0) += normal;
//			mesh.getNormal(id1) += normal;
//			mesh.getNormal(id2) += normal;
		}

		// Normalize normals.
		for ( long i = 0; i < nNormals; i++ )
		{
//			float length = sqrt(
//					mesh.getNormal(i).x()*mesh.getNormal(i).x() +
//					mesh.getNormal(i).y()*mesh.getNormal(i).y() +
//					mesh.getNormal(i).z()*mesh.getNormal(i).z());
//			mesh.getNormal(i) /= length;
		}
	}

	private Point3dId calculateIntersection( /* Volume *//* Interiortest */ long nX, long nY, long nZ, int nEdgeNo )
	{
		RealPoint p1, p2;
		long v1x = nX, v1y = nY, v1z = nZ;
		long v2x = nX, v2y = nY, v2z = nZ;

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
//		p1.setPosition( volume.getBoundingBox().min().x() + ( v1x - 1 ) * cellSizeX, 0 );
//		p1.setPosition( volume.getBoundingBox().min().y() + ( v1y - 1 ) * cellSizeY, 1 );
//		p1.setPosition( volume.getBoundingBox().min().z() + ( v1z - 1 ) * cellSizeZ, 2 );
//		p2.setPosition( volume.getBoundingBox().min().x() + ( v2x - 1 ) * cellSizeX, 0 );
//		p2.setPosition( volume.getBoundingBox().min().y() + ( v2y - 1 ) * cellSizeY, 1 );
//		p2.setPosition( volume.getBoundingBox().min().z() + ( v2z - 1 ) * cellSizeZ, 2 );
//
//		T val1 = getValue( volume, v1x, v1y, v1z );
//		T val2 = getValue( volume, v2x, v2y, v2z );

		if ( interiorTest( val1 ) && !interiorTest( val2 ) )
			return findSurfaceIntersection( volume, interiorTest, p2, p1 );
		else
			return findSurfaceIntersection( volume, interiorTest, p1, p2 );
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
			return Invalid;
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

		for ( long i = 0; i < 10; i++, delta /= 2.0 )
		{

			float diffX = p2.getFloatPosition( 0 ) - p1.getFloatPosition( 0 );
			float diffY = p2.getFloatPosition( 1 ) - p1.getFloatPosition( 1 );
			float diffZ = p2.getFloatPosition( 2 ) - p1.getFloatPosition( 2 );

			diffX = diffX * mu;
			diffY = diffY * mu;
			diffZ = diffZ * mu;

			diffX = diffX + p1.getFloatPosition( 0 );
			diffX = diffY + p1.getFloatPosition( 1 );
			diffX = diffZ + p1.getFloatPosition( 2 );

			RealPoint diff = new RealPoint( diffX, diffY, diffZ );
			interpolation = new Point3dId( diff );// p1 + mu*(p2-p1);

//			if ( interiorTest( volume( interpolation.x, interpolation.y,
//								interpolation.z ) ) )
//				mu -= delta; // go to outside
//			else
				mu += delta; // go to inside
		}

		return interpolation;
	}

}
