package org.janelia.saalfeldlab.paintera.meshes;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import javafx.geometry.Point3D;
import net.imglib2.RealInterval;
import net.imglib2.util.Triple;
import net.imglib2.util.ValueTriple;

import java.util.ArrayList;

public class Mesh {

	/**
	 * [x_0, y_0, z_0, x_1, y_1, z_1, ... , x_n, y_n, z_n]
	 */
	private final float[] vertices;

	/**
	 * [x_0, y_0, z_0, x_1, y_1, z_1, ... , x_n, y_n, z_n]
	 */
	private final float[] normals;

	/**
	 * [vertex_0.0, vertex_0.1, vertex_0.2, vertex_1.0, vertex_1.1, vertex_1.2, ... , vertex_n.0, vertex_n.1, vertex_n.2]
	 */
	private final int[] triangles;

	private final ArrayList<int[]> vertexTriangles = new ArrayList<>();

	/**
	 * overhanging vertices
	 */
	private final int[] overhanging;


	public Mesh(final float[] flatVertices, final RealInterval interval) {

		assert flatVertices.length % 9 == 0;

		final TFloatArrayList vertexList = new TFloatArrayList();
		final TObjectIntHashMap<Point3D> vertexIndexMap = new TObjectIntHashMap<>();
		final ArrayList<TIntHashSet> vertexTrianglesList = new ArrayList<>();
		final TIntArrayList triangleList = new TIntArrayList();
		final TIntArrayList overhangingList = new TIntArrayList();

		final float minX = (float)interval.realMin(0);
		final float minY = (float)interval.realMin(1);
		final float minZ = (float)interval.realMin(2);

		final double maxX = (float)interval.realMax(0);
		final double maxY = (float)interval.realMax(1);
		final double maxZ = (float)interval.realMax(2);

		for (int triangle = 0; triangle < flatVertices.length; triangle += 9) {

			final Point3D[] keys = new Point3D[]{
					new Point3D(flatVertices[triangle + 0], flatVertices[triangle + 1], flatVertices[triangle + 2]),
					new Point3D(flatVertices[triangle + 3], flatVertices[triangle + 4], flatVertices[triangle + 5]),
					new Point3D(flatVertices[triangle + 6], flatVertices[triangle + 7], flatVertices[triangle + 8])
			};

			for (int i = 0; i < keys.length; ++i) {
				final Point3D key = keys[i];
				final int vertexIndex;
				if (vertexIndexMap.contains(key))
					vertexIndex = vertexIndexMap.get(keys[i]);
				else {
					vertexIndex = vertexList.size() / 3;
					vertexIndexMap.put(key, vertexIndex);

					final float x = (float)key.getX();
					final float y = (float)key.getY();
					final float z = (float)key.getZ();
					vertexList.add(x);
					vertexList.add(y);
					vertexList.add(z);
					if (x < minX || y < minY || z < minZ || x > maxX || y > maxY || z > maxZ)
						overhangingList.add(vertexIndex);
				}
				triangleList.add(vertexIndex);

				final TIntHashSet triangleIndices;
				if (vertexTrianglesList.size() > vertexIndex) {
					triangleIndices = vertexTrianglesList.get(vertexIndex);
				} else {
					triangleIndices = new TIntHashSet();
					vertexTrianglesList.add(triangleIndices);
				}
				triangleIndices.add(triangle / 9);
			}
		}

		vertices = vertexList.toArray();
		overhanging = overhangingList.toArray();
		triangles = triangleList.toArray();
		normals = new float[vertices.length];

		vertexTriangles.clear();
		for (final TIntHashSet vertexIndices : vertexTrianglesList) {
			vertexTriangles.add(vertexIndices.toArray());
		}

		averageNormals();
	}

	public void averageNormals() {

		final double[] triangleNormals = new double[triangles.length]; // coincidental match 3 vertices and 3 coordinates

		for (int triangle = 0; triangle < triangles.length; triangle += 3) {

			final int v1 = triangles[triangle] * 3;
			final int v2 = triangles[triangle + 1] * 3;
			final int v3 = triangles[triangle + 2] * 3;

			final double v11 = vertices[v1], v12 = vertices[v1 + 1], v13 = vertices[v1 + 2];
			final double v21 = vertices[v2], v22 = vertices[v2 + 1], v23 = vertices[v2 + 2];
			final double v31 = vertices[v3], v32 = vertices[v3 + 1], v33 = vertices[v3 + 2];

			final double diff10 = v21 - v11;
			final double diff11 = v22 - v12;
			final double diff12 = v23 - v13;

			final double diff20 = v31 - v11;
			final double diff21 = v32 - v12;
			final double diff22 = v33 - v13;

			double x = diff11 * diff22 - diff12 * diff21;
			double y = diff12 * diff20 - diff10 * diff22;
			double z = diff10 * diff21 - diff11 * diff20;
			final double norm = Math.sqrt(x * x + y * y + z * z);
			x /= norm;
			y /= norm;
			z /= norm;

			triangleNormals[triangle + 0] = x;
			triangleNormals[triangle + 1] = y;
			triangleNormals[triangle + 2] = z;
		}

		for (int vertex = 0; vertex < vertices.length; vertex += 3) {

			final int[] triangles = vertexTriangles.get(vertex / 3);
			double x = 0, y = 0, z = 0;
			for (final int triangle : triangles) {
				final int t = triangle * 3;
				x += triangleNormals[t];
				y += triangleNormals[t + 1];
				z += triangleNormals[t + 2];
			}
			normals[vertex] = (float)(x / triangles.length);
			normals[vertex + 1] = (float)(y / triangles.length);
			normals[vertex + 2] = (float)(z / triangles.length);
		}
	}

	public void smooth(final double lambda, final int iterations) {

		final float[] smoothedVertices = new float[vertices.length];
		final double lambda1 = 1.0 - lambda;
		final TIntHashSet otherVertices = new TIntHashSet();
		for (int i = 0; i < iterations; ++i) {
			for (int vertex = 0; vertex < vertices.length; vertex += 3) {

				final int vi = vertex / 3;
				otherVertices.clear();
				final int[] triangles = vertexTriangles.get(vi);
				for (final int triangle : triangles) {
					final int ti = triangle * 3;
					otherVertices.add(triangles[ti]);
					otherVertices.add(triangles[ti + 1]);
					otherVertices.add(triangles[ti + 2]);
				}
				otherVertices.remove(vi);

				double x = 0, y = 0, z = 0;
				final TIntIterator it = otherVertices.iterator();
				final double norm = 1.0 / otherVertices.size() * lambda;
				while (it.hasNext()) {
					final int oi = it.next() * 3;
					x += vertices[oi];
					y += vertices[oi + 1];
					z += vertices[oi + 2];
				}

				smoothedVertices[vertex] = (float) (lambda1 * vertices[vertex] + x * norm);
				smoothedVertices[vertex + 1] = (float) (lambda1 * vertices[vertex + 1] + y * norm);
				smoothedVertices[vertex + 2] = (float) (lambda1 * vertices[vertex + 2] + z * norm);
			}
			System.arraycopy(smoothedVertices, 0, vertices, 0, vertices.length);
		}
	}
}
