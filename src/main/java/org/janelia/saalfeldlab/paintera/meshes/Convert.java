package org.janelia.saalfeldlab.paintera.meshes;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import javafx.geometry.Point3D;
import kotlin.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Convert flat mesh data into indexed representations and back.
 *
 * @author Stephan Saalfeld
 * @author Philipp Hanslovsky
 */
public class Convert {

	/**
	 * logger
	 */
	private static final Logger LOG = LoggerFactory.getLogger(Convert.class);

	/**
	 * Convert a set of triangles defined by three vertices each into a reduced list of vertices and a LUT from vertex
	 * index to triangle index and from triangle index to vertex index.
	 *
	 * @param triangles triangles
	 * @param triangles triangles
	 * @return ([vertices], [vertex - triangle - lookup], [triangle - vertex - lut])
	 */
	public static Triple<TFloatArrayList, ArrayList<TIntHashSet>, ArrayList<TIntArrayList>> convertToLUT(
			final float[] triangles) {

		LOG.trace("Converting {} triangles to lut", triangles.length);

		assert triangles.length % 9 == 0;

		final TFloatArrayList vertices = new TFloatArrayList(); // stride 3
		final TObjectIntHashMap<Point3D> vertexIndexMap = new TObjectIntHashMap<>();
		final ArrayList<TIntHashSet> vertexTriangleLUT = new ArrayList<>();
		final ArrayList<TIntArrayList> triangleVertexLUT = new ArrayList<>();

		for (int triangle = 0; triangle < triangles.length; triangle += 9) {
			final int triangleIndex = triangle / 9;

			final Point3D[] keys = new Point3D[]{
					new Point3D(triangles[triangle + 0], triangles[triangle + 1], triangles[triangle + 2]),
					new Point3D(triangles[triangle + 3], triangles[triangle + 4], triangles[triangle + 5]),
					new Point3D(triangles[triangle + 6], triangles[triangle + 7], triangles[triangle + 8])
			};

			final TIntArrayList vertexIndices = new TIntArrayList();
			triangleVertexLUT.add(vertexIndices);
			for (int i = 0; i < keys.length; ++i) {
				final Point3D key = keys[i];
				final int vertexIndex;
				if (vertexIndexMap.contains(key))
					vertexIndex = vertexIndexMap.get(keys[i]);
				else {
					vertexIndex = vertices.size() / 3;
					vertexIndexMap.put(key, vertexIndex);
					vertices.add((float)key.getX());
					vertices.add((float)key.getY());
					vertices.add((float)key.getZ());
				}
				vertexIndices.add(vertexIndex);

				final TIntHashSet triangleIndices;
				if (vertexTriangleLUT.size() > vertexIndex) {
					triangleIndices = vertexTriangleLUT.get(vertexIndex);
				} else {
					triangleIndices = new TIntHashSet();
					vertexTriangleLUT.add(triangleIndices);
				}
				triangleIndices.add(triangleIndex);
			}
		}
		return new Triple<>(
				vertices,
				vertexTriangleLUT,
				triangleVertexLUT
		);
	}

	/**
	 * @param vertices          vertices
	 * @param triangleVertexLUT triangleVertexLUT
	 * @param triangleVertexLUT triangleVertexLUT
	 * @return vertices
	 */
	public static float[] convertFromLUT(
			final TFloatArrayList vertices,
			final ArrayList<TIntArrayList> triangleVertexLUT) {

		final float[] export = new float[triangleVertexLUT.size() * 9];
		int t = -1;
		for (final TIntArrayList triangleVertices : triangleVertexLUT) {
			final TIntArrayList vertexIndices = triangleVertices;
			for (int i = 0; i < vertexIndices.size(); ++i) {
				int vertexIndex = vertexIndices.get(i) * 3;
				export[++t] = vertices.get(vertexIndex);
				export[++t] = vertices.get(++vertexIndex);
				export[++t] = vertices.get(++vertexIndex);
			}
		}
		return export;
	}

	/**
	 * Convert vertex to triangle and triangle to vertex lookups into a vertex to vertex lookup of all edges.
	 *
	 * @param vertexTriangleLUT vertexTriangleLUT
	 * @param triangleVertexLUT triangleVertexLUT
	 * @param triangleVertexLUT triangleVertexLUT
	 * @return vertex to edge lookup
	 */
	public static ArrayList<TIntHashSet> convertToEdgeSets(
			final ArrayList<TIntHashSet> vertexTriangleLUT,
			final ArrayList<TIntArrayList> triangleVertexLUT) {

		final ArrayList<TIntHashSet> vertexEdgeLUT = new ArrayList<>();
		for (int vertexIndex = 0; vertexIndex < vertexTriangleLUT.size(); ++vertexIndex) {
			final int fVertexIndex = vertexIndex;
			final TIntHashSet edges = new TIntHashSet();
			vertexEdgeLUT.add(edges);
			final int[] triangles = vertexTriangleLUT.get(vertexIndex).toArray();
			for (int i = 0; i < triangles.length; ++i) {
				final TIntArrayList vertices = triangleVertexLUT.get(triangles[i]);
				vertices.forEach(vertex -> {
					if (vertex != fVertexIndex)
						edges.add(vertex);
					return true;
				});
			}
		}
		return vertexEdgeLUT;
	}
}
