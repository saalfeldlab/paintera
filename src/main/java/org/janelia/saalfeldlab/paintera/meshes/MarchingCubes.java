package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

import gnu.trove.list.array.TFloatArrayList;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation;
import net.imglib2.type.BooleanType;
import net.imglib2.util.Intervals;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the marching cubes algorithm. Based on http://paulbourke.net/geometry/polygonise/
 *
 * @param <B>
 *
 * @author Vanessa Leite
 * @author Philipp Hanslovsky
 */
public class MarchingCubes<B extends BooleanType<B>>
{
	private static final int INVALID = -1;

	/**
	 * For any edge, if one vertex is inside of the surface and the other is outside of the surface then the edge
	 * intersects the surface. For each of the 8 vertices of the cube can be two possible states: either inside or
	 * outside of the surface For any cube the are 2^8=256 possible sets of vertex states. This table lists the edges
	 * intersected by the surface for all 256 possible vertex states. There are 12 edges. For each entry in the table,
	 * if edge #n is intersected, then bit #n is set to 1
	 */
	private static final int[] MC_EDGE_TABLE = {
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
			0x70c, 0x605, 0x50f, 0x406, 0x30a, 0x203, 0x109, 0x0};

	/**
	 * For each of the possible cube state there is a specific triangulation of the edge intersection points. This
	 * table
	 * lists all of them in the form of 0-5 edge triples with the list terminated by the invalid value. For example:
	 * tritable[3] list the 2 triangles formed when corner[0] and corner[1] are inside of the surface, but the rest of
	 * the cube is not.
	 */
	private static final int MC_TRI_TABLE[][] = {
			{INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 8, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{0, 1, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{1, 8, 3, 9, 8, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 2, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{0, 8, 3, 1, 2, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{9, 2, 10, 0, 2, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{2, 8, 3, 2, 10, 8, 10, 9, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 11, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{0, 11, 2, 8, 11, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 9, 0, 2, 3, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 11, 2, 1, 9, 11, 9, 8, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 10, 1, 11, 10, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 10, 1, 0, 8, 10, 8, 11, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 9, 0, 3, 11, 9, 11, 10, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 8, 10, 10, 8, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{4, 7, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{4, 3, 0, 7, 3, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 1, 9, 8, 4, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{4, 1, 9, 4, 7, 1, 7, 3, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 2, 10, 8, 4, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{3, 4, 7, 3, 0, 4, 1, 2, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 2, 10, 9, 0, 2, 8, 4, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{2, 10, 9, 2, 9, 7, 2, 7, 3, 7, 9, 4, INVALID, INVALID, INVALID, INVALID},
			{8, 4, 7, 3, 11, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{11, 4, 7, 11, 2, 4, 2, 0, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 0, 1, 8, 4, 7, 2, 3, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{4, 7, 11, 9, 4, 11, 9, 11, 2, 9, 2, 1, INVALID, INVALID, INVALID, INVALID},
			{3, 10, 1, 3, 11, 10, 7, 8, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 11, 10, 1, 4, 11, 1, 0, 4, 7, 11, 4, INVALID, INVALID, INVALID, INVALID},
			{4, 7, 8, 9, 0, 11, 9, 11, 10, 11, 0, 3, INVALID, INVALID, INVALID, INVALID},
			{4, 7, 11, 4, 11, 9, 9, 11, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 5, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{9, 5, 4, 0, 8, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 5, 4, 1, 5, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{8, 5, 4, 8, 3, 5, 3, 1, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 2, 10, 9, 5, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{3, 0, 8, 1, 2, 10, 4, 9, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 2, 10, 5, 4, 2, 4, 0, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{2, 10, 5, 3, 2, 5, 3, 5, 4, 3, 4, 8, INVALID, INVALID, INVALID, INVALID},
			{9, 5, 4, 2, 3, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 11, 2, 0, 8, 11, 4, 9, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 5, 4, 0, 1, 5, 2, 3, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{2, 1, 5, 2, 5, 8, 2, 8, 11, 4, 8, 5, INVALID, INVALID, INVALID, INVALID},
			{10, 3, 11, 10, 1, 3, 9, 5, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{4, 9, 5, 0, 8, 1, 8, 10, 1, 8, 11, 10, INVALID, INVALID, INVALID, INVALID},
			{5, 4, 0, 5, 0, 11, 5, 11, 10, 11, 0, 3, INVALID, INVALID, INVALID, INVALID},
			{5, 4, 8, 5, 8, 10, 10, 8, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 7, 8, 5, 7, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{9, 3, 0, 9, 5, 3, 5, 7, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 7, 8, 0, 1, 7, 1, 5, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 5, 3, 3, 5, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{9, 7, 8, 9, 5, 7, 10, 1, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{10, 1, 2, 9, 5, 0, 5, 3, 0, 5, 7, 3, INVALID, INVALID, INVALID, INVALID},
			{8, 0, 2, 8, 2, 5, 8, 5, 7, 10, 5, 2, INVALID, INVALID, INVALID, INVALID},
			{2, 10, 5, 2, 5, 3, 3, 5, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{7, 9, 5, 7, 8, 9, 3, 11, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 5, 7, 9, 7, 2, 9, 2, 0, 2, 7, 11, INVALID, INVALID, INVALID, INVALID},
			{2, 3, 11, 0, 1, 8, 1, 7, 8, 1, 5, 7, INVALID, INVALID, INVALID, INVALID},
			{11, 2, 1, 11, 1, 7, 7, 1, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 5, 8, 8, 5, 7, 10, 1, 3, 10, 3, 11, INVALID, INVALID, INVALID, INVALID},
			{5, 7, 0, 5, 0, 9, 7, 11, 0, 1, 0, 10, 11, 10, 0, INVALID},
			{11, 10, 0, 11, 0, 3, 10, 5, 0, 8, 0, 7, 5, 7, 0, INVALID},
			{11, 10, 5, 7, 11, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{10, 6, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{0, 8, 3, 5, 10, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{9, 0, 1, 5, 10, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 8, 3, 1, 9, 8, 5, 10, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 6, 5, 2, 6, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 6, 5, 1, 2, 6, 3, 0, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 6, 5, 9, 0, 6, 0, 2, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 9, 8, 5, 8, 2, 5, 2, 6, 3, 2, 8, INVALID, INVALID, INVALID, INVALID},
			{2, 3, 11, 10, 6, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{11, 0, 8, 11, 2, 0, 10, 6, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 1, 9, 2, 3, 11, 5, 10, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 10, 6, 1, 9, 2, 9, 11, 2, 9, 8, 11, INVALID, INVALID, INVALID, INVALID},
			{6, 3, 11, 6, 5, 3, 5, 1, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 8, 11, 0, 11, 5, 0, 5, 1, 5, 11, 6, INVALID, INVALID, INVALID, INVALID},
			{3, 11, 6, 0, 3, 6, 0, 6, 5, 0, 5, 9, INVALID, INVALID, INVALID, INVALID},
			{6, 5, 9, 6, 9, 11, 11, 9, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 10, 6, 4, 7, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{4, 3, 0, 4, 7, 3, 6, 5, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 9, 0, 5, 10, 6, 8, 4, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{10, 6, 5, 1, 9, 7, 1, 7, 3, 7, 9, 4, INVALID, INVALID, INVALID, INVALID},
			{6, 1, 2, 6, 5, 1, 4, 7, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 2, 5, 5, 2, 6, 3, 0, 4, 3, 4, 7, INVALID, INVALID, INVALID, INVALID},
			{8, 4, 7, 9, 0, 5, 0, 6, 5, 0, 2, 6, INVALID, INVALID, INVALID, INVALID},
			{7, 3, 9, 7, 9, 4, 3, 2, 9, 5, 9, 6, 2, 6, 9, INVALID},
			{3, 11, 2, 7, 8, 4, 10, 6, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 10, 6, 4, 7, 2, 4, 2, 0, 2, 7, 11, INVALID, INVALID, INVALID, INVALID},
			{0, 1, 9, 4, 7, 8, 2, 3, 11, 5, 10, 6, INVALID, INVALID, INVALID, INVALID},
			{9, 2, 1, 9, 11, 2, 9, 4, 11, 7, 11, 4, 5, 10, 6, INVALID},
			{8, 4, 7, 3, 11, 5, 3, 5, 1, 5, 11, 6, INVALID, INVALID, INVALID, INVALID},
			{5, 1, 11, 5, 11, 6, 1, 0, 11, 7, 11, 4, 0, 4, 11, INVALID},
			{0, 5, 9, 0, 6, 5, 0, 3, 6, 11, 6, 3, 8, 4, 7, INVALID},
			{6, 5, 9, 6, 9, 11, 4, 7, 9, 7, 11, 9, INVALID, INVALID, INVALID, INVALID},
			{10, 4, 9, 6, 4, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{4, 10, 6, 4, 9, 10, 0, 8, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{10, 0, 1, 10, 6, 0, 6, 4, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{8, 3, 1, 8, 1, 6, 8, 6, 4, 6, 1, 10, INVALID, INVALID, INVALID, INVALID},
			{1, 4, 9, 1, 2, 4, 2, 6, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 0, 8, 1, 2, 9, 2, 4, 9, 2, 6, 4, INVALID, INVALID, INVALID, INVALID},
			{0, 2, 4, 4, 2, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{8, 3, 2, 8, 2, 4, 4, 2, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{10, 4, 9, 10, 6, 4, 11, 2, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 8, 2, 2, 8, 11, 4, 9, 10, 4, 10, 6, INVALID, INVALID, INVALID, INVALID},
			{3, 11, 2, 0, 1, 6, 0, 6, 4, 6, 1, 10, INVALID, INVALID, INVALID, INVALID},
			{6, 4, 1, 6, 1, 10, 4, 8, 1, 2, 1, 11, 8, 11, 1, INVALID},
			{9, 6, 4, 9, 3, 6, 9, 1, 3, 11, 6, 3, INVALID, INVALID, INVALID, INVALID},
			{8, 11, 1, 8, 1, 0, 11, 6, 1, 9, 1, 4, 6, 4, 1, INVALID},
			{3, 11, 6, 3, 6, 0, 0, 6, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{6, 4, 8, 11, 6, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{7, 10, 6, 7, 8, 10, 8, 9, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 7, 3, 0, 10, 7, 0, 9, 10, 6, 7, 10, INVALID, INVALID, INVALID, INVALID},
			{10, 6, 7, 1, 10, 7, 1, 7, 8, 1, 8, 0, INVALID, INVALID, INVALID, INVALID},
			{10, 6, 7, 10, 7, 1, 1, 7, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 2, 6, 1, 6, 8, 1, 8, 9, 8, 6, 7, INVALID, INVALID, INVALID, INVALID},
			{2, 6, 9, 2, 9, 1, 6, 7, 9, 0, 9, 3, 7, 3, 9, INVALID},
			{7, 8, 0, 7, 0, 6, 6, 0, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{7, 3, 2, 6, 7, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{2, 3, 11, 10, 6, 8, 10, 8, 9, 8, 6, 7, INVALID, INVALID, INVALID, INVALID},
			{2, 0, 7, 2, 7, 11, 0, 9, 7, 6, 7, 10, 9, 10, 7, INVALID},
			{1, 8, 0, 1, 7, 8, 1, 10, 7, 6, 7, 10, 2, 3, 11, INVALID},
			{11, 2, 1, 11, 1, 7, 10, 6, 1, 6, 7, 1, INVALID, INVALID, INVALID, INVALID},
			{8, 9, 6, 8, 6, 7, 9, 1, 6, 11, 6, 3, 1, 3, 6, INVALID},
			{0, 9, 1, 11, 6, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{7, 8, 0, 7, 0, 6, 3, 11, 0, 11, 6, 0, INVALID, INVALID, INVALID, INVALID},
			{7, 11, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{7, 6, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{3, 0, 8, 11, 7, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 1, 9, 11, 7, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{8, 1, 9, 8, 3, 1, 11, 7, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{10, 1, 2, 6, 11, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 2, 10, 3, 0, 8, 6, 11, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{2, 9, 0, 2, 10, 9, 6, 11, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{6, 11, 7, 2, 10, 3, 10, 8, 3, 10, 9, 8, INVALID, INVALID, INVALID, INVALID},
			{7, 2, 3, 6, 2, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{7, 0, 8, 7, 6, 0, 6, 2, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{2, 7, 6, 2, 3, 7, 0, 1, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 6, 2, 1, 8, 6, 1, 9, 8, 8, 7, 6, INVALID, INVALID, INVALID, INVALID},
			{10, 7, 6, 10, 1, 7, 1, 3, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{10, 7, 6, 1, 7, 10, 1, 8, 7, 1, 0, 8, INVALID, INVALID, INVALID, INVALID},
			{0, 3, 7, 0, 7, 10, 0, 10, 9, 6, 10, 7, INVALID, INVALID, INVALID, INVALID},
			{7, 6, 10, 7, 10, 8, 8, 10, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{6, 8, 4, 11, 8, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{3, 6, 11, 3, 0, 6, 0, 4, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{8, 6, 11, 8, 4, 6, 9, 0, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 4, 6, 9, 6, 3, 9, 3, 1, 11, 3, 6, INVALID, INVALID, INVALID, INVALID},
			{6, 8, 4, 6, 11, 8, 2, 10, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 2, 10, 3, 0, 11, 0, 6, 11, 0, 4, 6, INVALID, INVALID, INVALID, INVALID},
			{4, 11, 8, 4, 6, 11, 0, 2, 9, 2, 10, 9, INVALID, INVALID, INVALID, INVALID},
			{10, 9, 3, 10, 3, 2, 9, 4, 3, 11, 3, 6, 4, 6, 3, INVALID},
			{8, 2, 3, 8, 4, 2, 4, 6, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 4, 2, 4, 6, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 9, 0, 2, 3, 4, 2, 4, 6, 4, 3, 8, INVALID, INVALID, INVALID, INVALID},
			{1, 9, 4, 1, 4, 2, 2, 4, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{8, 1, 3, 8, 6, 1, 8, 4, 6, 6, 10, 1, INVALID, INVALID, INVALID, INVALID},
			{10, 1, 0, 10, 0, 6, 6, 0, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{4, 6, 3, 4, 3, 8, 6, 10, 3, 0, 3, 9, 10, 9, 3, INVALID},
			{10, 9, 4, 6, 10, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{4, 9, 5, 7, 6, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 8, 3, 4, 9, 5, 11, 7, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 0, 1, 5, 4, 0, 7, 6, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{11, 7, 6, 8, 3, 4, 3, 5, 4, 3, 1, 5, INVALID, INVALID, INVALID, INVALID},
			{9, 5, 4, 10, 1, 2, 7, 6, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{6, 11, 7, 1, 2, 10, 0, 8, 3, 4, 9, 5, INVALID, INVALID, INVALID, INVALID},
			{7, 6, 11, 5, 4, 10, 4, 2, 10, 4, 0, 2, INVALID, INVALID, INVALID, INVALID},
			{3, 4, 8, 3, 5, 4, 3, 2, 5, 10, 5, 2, 11, 7, 6, INVALID},
			{7, 2, 3, 7, 6, 2, 5, 4, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 5, 4, 0, 8, 6, 0, 6, 2, 6, 8, 7, INVALID, INVALID, INVALID, INVALID},
			{3, 6, 2, 3, 7, 6, 1, 5, 0, 5, 4, 0, INVALID, INVALID, INVALID, INVALID},
			{6, 2, 8, 6, 8, 7, 2, 1, 8, 4, 8, 5, 1, 5, 8, INVALID},
			{9, 5, 4, 10, 1, 6, 1, 7, 6, 1, 3, 7, INVALID, INVALID, INVALID, INVALID},
			{1, 6, 10, 1, 7, 6, 1, 0, 7, 8, 7, 0, 9, 5, 4, INVALID},
			{4, 0, 10, 4, 10, 5, 0, 3, 10, 6, 10, 7, 3, 7, 10, INVALID},
			{7, 6, 10, 7, 10, 8, 5, 4, 10, 4, 8, 10, INVALID, INVALID, INVALID, INVALID},
			{6, 9, 5, 6, 11, 9, 11, 8, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 6, 11, 0, 6, 3, 0, 5, 6, 0, 9, 5, INVALID, INVALID, INVALID, INVALID},
			{0, 11, 8, 0, 5, 11, 0, 1, 5, 5, 6, 11, INVALID, INVALID, INVALID, INVALID},
			{6, 11, 3, 6, 3, 5, 5, 3, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 2, 10, 9, 5, 11, 9, 11, 8, 11, 5, 6, INVALID, INVALID, INVALID, INVALID},
			{0, 11, 3, 0, 6, 11, 0, 9, 6, 5, 6, 9, 1, 2, 10, INVALID},
			{11, 8, 5, 11, 5, 6, 8, 0, 5, 10, 5, 2, 0, 2, 5, INVALID},
			{6, 11, 3, 6, 3, 5, 2, 10, 3, 10, 5, 3, INVALID, INVALID, INVALID, INVALID},
			{5, 8, 9, 5, 2, 8, 5, 6, 2, 3, 8, 2, INVALID, INVALID, INVALID, INVALID},
			{9, 5, 6, 9, 6, 0, 0, 6, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{1, 5, 8, 1, 8, 0, 5, 6, 8, 3, 8, 2, 6, 2, 8, INVALID},
			{1, 5, 6, 2, 1, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 3, 6, 1, 6, 10, 3, 8, 6, 5, 6, 9, 8, 9, 6, INVALID},
			{10, 1, 0, 10, 0, 6, 9, 5, 0, 5, 6, 0, INVALID, INVALID, INVALID, INVALID},
			{0, 3, 8, 5, 6, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{10, 5, 6, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{11, 5, 10, 7, 5, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{11, 5, 10, 11, 7, 5, 8, 3, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 11, 7, 5, 10, 11, 1, 9, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{10, 7, 5, 10, 11, 7, 9, 8, 1, 8, 3, 1, INVALID, INVALID, INVALID, INVALID},
			{11, 1, 2, 11, 7, 1, 7, 5, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 8, 3, 1, 2, 7, 1, 7, 5, 7, 2, 11, INVALID, INVALID, INVALID, INVALID},
			{9, 7, 5, 9, 2, 7, 9, 0, 2, 2, 11, 7, INVALID, INVALID, INVALID, INVALID},
			{7, 5, 2, 7, 2, 11, 5, 9, 2, 3, 2, 8, 9, 8, 2, INVALID},
			{2, 5, 10, 2, 3, 5, 3, 7, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{8, 2, 0, 8, 5, 2, 8, 7, 5, 10, 2, 5, INVALID, INVALID, INVALID, INVALID},
			{9, 0, 1, 5, 10, 3, 5, 3, 7, 3, 10, 2, INVALID, INVALID, INVALID, INVALID},
			{9, 8, 2, 9, 2, 1, 8, 7, 2, 10, 2, 5, 7, 5, 2, INVALID},
			{1, 3, 5, 3, 7, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 8, 7, 0, 7, 1, 1, 7, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 0, 3, 9, 3, 5, 5, 3, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 8, 7, 5, 9, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{5, 8, 4, 5, 10, 8, 10, 11, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{5, 0, 4, 5, 11, 0, 5, 10, 11, 11, 3, 0, INVALID, INVALID, INVALID, INVALID},
			{0, 1, 9, 8, 4, 10, 8, 10, 11, 10, 4, 5, INVALID, INVALID, INVALID, INVALID},
			{10, 11, 4, 10, 4, 5, 11, 3, 4, 9, 4, 1, 3, 1, 4, INVALID},
			{2, 5, 1, 2, 8, 5, 2, 11, 8, 4, 5, 8, INVALID, INVALID, INVALID, INVALID},
			{0, 4, 11, 0, 11, 3, 4, 5, 11, 2, 11, 1, 5, 1, 11, INVALID},
			{0, 2, 5, 0, 5, 9, 2, 11, 5, 4, 5, 8, 11, 8, 5, INVALID},
			{9, 4, 5, 2, 11, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{2, 5, 10, 3, 5, 2, 3, 4, 5, 3, 8, 4, INVALID, INVALID, INVALID, INVALID},
			{5, 10, 2, 5, 2, 4, 4, 2, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 10, 2, 3, 5, 10, 3, 8, 5, 4, 5, 8, 0, 1, 9, INVALID},
			{5, 10, 2, 5, 2, 4, 1, 9, 2, 9, 4, 2, INVALID, INVALID, INVALID, INVALID},
			{8, 4, 5, 8, 5, 3, 3, 5, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 4, 5, 1, 0, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{8, 4, 5, 8, 5, 3, 9, 0, 5, 0, 3, 5, INVALID, INVALID, INVALID, INVALID},
			{9, 4, 5, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{4, 11, 7, 4, 9, 11, 9, 10, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 8, 3, 4, 9, 7, 9, 11, 7, 9, 10, 11, INVALID, INVALID, INVALID, INVALID},
			{1, 10, 11, 1, 11, 4, 1, 4, 0, 7, 4, 11, INVALID, INVALID, INVALID, INVALID},
			{3, 1, 4, 3, 4, 8, 1, 10, 4, 7, 4, 11, 10, 11, 4, INVALID},
			{4, 11, 7, 9, 11, 4, 9, 2, 11, 9, 1, 2, INVALID, INVALID, INVALID, INVALID},
			{9, 7, 4, 9, 11, 7, 9, 1, 11, 2, 11, 1, 0, 8, 3, INVALID},
			{11, 7, 4, 11, 4, 2, 2, 4, 0, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{11, 7, 4, 11, 4, 2, 8, 3, 4, 3, 2, 4, INVALID, INVALID, INVALID, INVALID},
			{2, 9, 10, 2, 7, 9, 2, 3, 7, 7, 4, 9, INVALID, INVALID, INVALID, INVALID},
			{9, 10, 7, 9, 7, 4, 10, 2, 7, 8, 7, 0, 2, 0, 7, INVALID},
			{3, 7, 10, 3, 10, 2, 7, 4, 10, 1, 10, 0, 4, 0, 10, INVALID},
			{1, 10, 2, 8, 7, 4, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{4, 9, 1, 4, 1, 7, 7, 1, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{4, 9, 1, 4, 1, 7, 0, 8, 1, 8, 7, 1, INVALID, INVALID, INVALID, INVALID},
			{4, 0, 3, 7, 4, 3, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{4, 8, 7, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{9, 10, 8, 10, 11, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{3, 0, 9, 3, 9, 11, 11, 9, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{0, 1, 10, 0, 10, 8, 8, 10, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 1, 10, 11, 3, 10, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{1, 2, 11, 1, 11, 9, 9, 11, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{3, 0, 9, 3, 9, 11, 1, 2, 9, 2, 11, 9, INVALID, INVALID, INVALID, INVALID},
			{0, 2, 11, 8, 0, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{3, 2, 11, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{2, 3, 8, 2, 8, 10, 10, 8, 9, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID},
			{9, 10, 2, 0, 9, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{2, 3, 8, 2, 8, 10, 0, 1, 8, 1, 10, 8, INVALID, INVALID, INVALID, INVALID},
			{1, 10, 2, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{1, 3, 8, 9, 1, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID},
			{0, 9, 1, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{0, 3, 8, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID},
			{INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID, INVALID,
					INVALID, INVALID, INVALID, INVALID, INVALID}
	};

	/**
	 * logger
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(MarchingCubes.class);

	private final RandomAccessible<B> input;

	private final Interval interval;

	private final AffineTransform3D transform;

	/**
	 * size of the cube
	 */
	private final int[] cubeSize;

	private final BooleanSupplier wasInterrupted;

	/**
	 * Initialize the class parameters with default values
	 */
	public MarchingCubes(
			final RandomAccessible<B> input,
			final Interval interval,
			final AffineTransform3D transform,
			final int[] cubeSize,
			final BooleanSupplier wasInterrupted)
	{
		this.input = input;
		this.interval = interval;
		this.cubeSize = cubeSize;
		this.transform = transform;
		this.wasInterrupted = wasInterrupted;
	}

	/**
	 * Creates the mesh using the information directly from the RAI structure
	 *
	 * @param input
	 * 		RAI with the label (segmentation) information
	 * @param cubeSize
	 * 		size of the cube to walk in the volume
	 * @param nextValuesVertex
	 * 		generic interface to access the information on RAI
	 *
	 * @return SimpleMesh, basically an array with the vertices
	 */
	public float[] generateMesh()
	{
		final long[]                   stride           = Arrays.stream(cubeSize).mapToLong(i -> i).toArray();
		final FinalInterval            expandedInterval = Intervals.expand(
				interval,
				Arrays.stream(stride).map(s -> s + 1).toArray()
		                                                                  );
		final SubsampleIntervalView<B> subsampled       = Views.subsample(
				Views.interval(input, expandedInterval),
				stride
		                                                                 );
		final Cursor<B>                cursor0          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						0,
						0,
						0
				            ),
				subsampled
		                                                                                   )).localizingCursor();
		final Cursor<B>                cursor1          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						1,
						0,
						0
				            ),
				subsampled
		                                                                                   )).cursor();
		final Cursor<B>                cursor2          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						0,
						1,
						0
				            ),
				subsampled
		                                                                                   )).cursor();
		final Cursor<B>                cursor3          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						1,
						1,
						0
				            ),
				subsampled
		                                                                                   )).cursor();
		final Cursor<B>                cursor4          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						0,
						0,
						1
				            ),
				subsampled
		                                                                                   )).cursor();
		final Cursor<B>                cursor5          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						1,
						0,
						1
				            ),
				subsampled
		                                                                                   )).cursor();
		final Cursor<B>                cursor6          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						0,
						1,
						1
				            ),
				subsampled
		                                                                                   )).cursor();
		final Cursor<B>                cursor7          = Views.flatIterable(Views.interval(
				Views.offset(
						subsampled,
						1,
						1,
						1
				            ),
				subsampled
		                                                                                   )).cursor();
		final Translation              translation      = new Translation(Arrays.stream(Intervals.minAsLongArray(
				expandedInterval)).mapToDouble(l -> l).toArray());

		final TFloatArrayList vertices = new TFloatArrayList();
		final double[]        p        = new double[3];

		final float[][] interpolationPoints = new float[12][3];

		while (cursor0.hasNext() && !wasInterrupted.getAsBoolean())
		{

			// Remap the vertices of the cube (8 positions) obtained from a RAI
			// to match the expected order for this implementation
			// @formatter:off
			// the values from the cube are given first in z, then y, then x
			// this way, the vertex_values (from getCube) are positioned in this
			// way:
			//
			//
			//   4-----6
			//  /|    /|
			// 0-----2 |
			// | 5---|-7
			// |/    |/
			// 1-----3
			//
			// this algorithm (based on
			// http://paulbourke.net/geometry/polygonise/)
			// considers the vertices of the cube in this order:
			//
			//   4-----5
			//  /|    /|
			// 7-----6 |
			// | 0---|-1
			// |/    |/
			// 3-----2
			//
			// This way, we need to remap the cube vertices:
			// @formatter:on
			final int vertexValues =
					(cursor5.next().get() ? 0b00000001 : 0) |
							(cursor7.next().get() ? 0b00000010 : 0) |
							(cursor3.next().get() ? 0b00000100 : 0) |
							(cursor1.next().get() ? 0b00001000 : 0) |
							(cursor4.next().get() ? 0b00010000 : 0) |
							(cursor6.next().get() ? 0b00100000 : 0) |
							(cursor2.next().get() ? 0b01000000 : 0) |
							(cursor0.next().get() ? 0b10000000 : 0);

			triangulation(
					vertexValues,
					cursor0.getLongPosition(0),
					cursor0.getLongPosition(1),
					cursor0.getLongPosition(2),
					vertices,
					interpolationPoints
			             );

		}

		final float[] vertexArray = new float[vertices.size()];

		for (int i = 0; i < vertexArray.length; i += 3)
		{
			p[0] = vertices.get(i + 0);
			p[1] = vertices.get(i + 1);
			p[2] = vertices.get(i + 2);
			translation.apply(p, p);
			transform.apply(p, p);
			vertexArray[i + 0] = (float) p[0];
			vertexArray[i + 1] = (float) p[1];
			vertexArray[i + 2] = (float) p[2];
		}

		return vertexArray;
	}

	/**
	 * Given the values of the vertices (in a specific order) identifies which of them are inside the mesh. For each
	 * one
	 * of the points that form the mesh, a triangulation is calculated.
	 *
	 * @param vertexValues
	 * 		the values of the eight vertices of the cube
	 * @param cursorX
	 * 		position on x
	 * @param cursorY
	 * 		position on y
	 * @param cursorZ
	 * 		position on z
	 */
	private void triangulation(
			final int vertexValues,
			final long cursorX,
			final long cursorY,
			final long cursorZ,
			final TFloatArrayList vertices,
			final float[][] interpolationPoints)
	{
		// @formatter:off
		// this algorithm (based on http://paulbourke.net/geometry/polygonise/)
		// considers the vertices of the cube in this order:
		//
		//   4-----5
		//  /|    /|
		// 7-----6 |
		// | 0---|-1
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
		//   |    0----*0*|----1
		// *11*  /       *10* /
		//   |  /         | *1*
		//   |*3*         | /
		//   |/           |/
		//   3-----*2*----2
		// @formatter: on

		// Now create a triangulation of the isosurface in this cell.
		final int McEdge = MC_EDGE_TABLE[tableIndex];
		if (McEdge != 0)
		{
			if ((McEdge & 1) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 0, interpolationPoints[0]);

			if ((McEdge & 2) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 1, interpolationPoints[1]);

			if ((McEdge & 4) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 2, interpolationPoints[2]);

			if ((McEdge & 8) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 3, interpolationPoints[3]);

			if ((McEdge & 16) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 4, interpolationPoints[4]);

			if ((McEdge & 32) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 5, interpolationPoints[5]);

			if ((McEdge & 64) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 6, interpolationPoints[6]);

			if ((McEdge & 128) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 7, interpolationPoints[7]);

			if ((McEdge & 256) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 8, interpolationPoints[8]);

			if ((McEdge & 512) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 9, interpolationPoints[9]);

			if ((McEdge & 1024) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 10, interpolationPoints[10]);

			if ((McEdge & 2048) != 0)
				calculateIntersection(cursorX, cursorY, cursorZ, 11, interpolationPoints[11]);

			final int[] McTri = MC_TRI_TABLE[tableIndex];

			for (int i = 0; McTri[i] != INVALID; i += 3)
			{
				final float[] v1 = interpolationPoints[McTri[i]];
				final float[] v2 = interpolationPoints[McTri[i + 1]];
				final float[] v3 = interpolationPoints[McTri[i + 2]];

				vertices.add(v1[0]);
				vertices.add(v1[1]);
				vertices.add(v1[2]);

				vertices.add(v2[0]);
				vertices.add(v2[1]);
				vertices.add(v2[2]);

				vertices.add(v3[0]);
				vertices.add(v3[1]);
				vertices.add(v3[2]);
			}
		}
	}

	/**
	 * Given the position on the volume and the intersected edge, calculates the intersection point. The intersection
	 * point is going to be in the middle of the intersected edge. In this method also the offset is applied.
	 *
	 * @param cursorX
	 * 		position on x
	 * @param cursorY
	 * 		position on y
	 * @param cursorZ
	 * 		position on z
	 * @param intersectedEdge
	 * 		intersected edge
	 *
	 * @return intersected point in world coordinates
	 */
	private void calculateIntersection(final long cursorX, final long cursorY, final long cursorZ, final int intersectedEdge, final float[] intersection)
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

		intersection[0] = (float) (0.5 * cubeSize[0] * (v1x + v2x));
		intersection[1] = (float) (0.5 * cubeSize[1] * (v1y + v2y));
		intersection[2] = (float) (0.5 * cubeSize[2] * (v1z + v2z));
	}
}
