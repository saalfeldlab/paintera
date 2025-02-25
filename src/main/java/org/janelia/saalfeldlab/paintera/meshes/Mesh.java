package org.janelia.saalfeldlab.paintera.meshes;

import gnu.trove.impl.Constants;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;

import java.util.ArrayList;
import java.util.Arrays;

import static gnu.trove.impl.Constants.DEFAULT_CAPACITY;
import static gnu.trove.impl.Constants.DEFAULT_LOAD_FACTOR;

public class Mesh {

	/**
	 * vertex coordinates, each consisting of three elements
	 * [x_0, y_0, z_0, x_1, y_1, z_1, ... , x_n, y_n, z_n]
	 */
	private final float[] vertices;

	/**
	 * normal vectors for each vertex
	 * [x_0, y_0, z_0, x_1, y_1, z_1, ... , x_n, y_n, z_n]
	 */
	private final float[] normals;

	/**
	 * vertex indices forming triangles
	 * [t0_v0, t0_v1, t0_v2, t1_v0, t1_v1, t1_v2, ... , tn_v0, tn_v1, tn_v2]
	 */
	private final int[] vertexIndices;

	/**
	 *
	 */
	private final ArrayList<int[]> trianglesPerVertex = new ArrayList<>();

	/**
	 * overhanging vertices
	 */
	private final TIntHashSet overhangingVertexIndices = new TIntHashSet(DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);

	private static class FloatLocalizable implements RealLocalizable {

		private final float[] position;

		public FloatLocalizable(float[] position) {

			this.position = position;
		}

		@Override public void localize(final RealPositionable position) {

			if (position.numDimensions() == numDimensions())
				position.setPosition(this);
			else {
				final int n = numDimensions();
				for (int d = 0; d < n; ++d)
					position.setPosition(getFloatPosition(d), d);
			}
		}

		@Override public float getFloatPosition(int d) {

			return position[d];
		}

		@Override public double getDoublePosition(int d) {

			return position[d];
		}

		@Override public int numDimensions() {

			return position.length;
		}
	}

	/**
	 * @param flatTrianglesAndVertices array of triangles, each consisting of 3 vertices, which in turn consist of 3 coordinates.
	 *                                 Of the form [ t1_v0_x, t1_v1_y, t1_v2_z, t2_v0_x, t2_v1_y, t2_v2_z, ..., tn_v0_x, tn_v1_y, tn_v2_z]
	 */
	public Mesh(final float[] flatTrianglesAndVertices, final Interval interval, final AffineTransform3D transform) {
		this(flatTrianglesAndVertices, interval, transform, true);
	}

	public Mesh(final float[] flatTrianglesAndVertices, final Interval interval, final AffineTransform3D transform, final boolean overlap) {

		assert flatTrianglesAndVertices.length % 9 == 0;

		final TFloatArrayList transformedVertexPositions = new TFloatArrayList();
		final TObjectIntHashMap<TFloatArrayList> vertexPositionIndexMap = new TObjectIntHashMap<>(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, -1);
		final ArrayList<TIntArrayList> trianglesPerVertex = new ArrayList<>();
		final TIntArrayList triangleVertexIndices = new TIntArrayList();

		final double minX = interval.min(0) - 1;
		final double minY = interval.min(1) - 1;
		final double minZ = interval.min(2) - 1;

		final double overlapOffset = overlap ? 1 : .5;
		final double maxX = interval.max(0) + overlapOffset;
		final double maxY = interval.max(1) + overlapOffset;
		final double maxZ = interval.max(2) + overlapOffset;

		final RealInterval vertexBounds = new FinalRealInterval(
				new double[]{minX, minY, minZ},
				new double[]{maxX, maxY, maxZ}
		);

		final float[] vertex1Position = new float[3];
		final float[] vertex2Position = new float[3];
		final float[] vertex3Position = new float[3];
		final float[][] triangleAsArray = new float[][]{vertex1Position, vertex2Position, vertex3Position};

		final FloatLocalizable vertex1 = new FloatLocalizable(vertex1Position);
		final FloatLocalizable vertex2 = new FloatLocalizable(vertex2Position);
		final FloatLocalizable vertex3 = new FloatLocalizable(vertex3Position);
		final FloatLocalizable[] triangle = new FloatLocalizable[]{vertex1, vertex2, vertex3};

		final float[] initialPos = new float[3];
		final float[] transformedPos = new float[3];

		int triangleIdx = 0;
		for (int triangleStartIdx = 0; triangleStartIdx < flatTrianglesAndVertices.length; triangleStartIdx += 9) {

			final int vertexIdx1 = triangleStartIdx;
			final int vertexIdx2 = triangleStartIdx + 3;
			final int vertexIdx3 = triangleStartIdx + 6;

			System.arraycopy(flatTrianglesAndVertices, vertexIdx1, vertex1Position, 0, 3);
			System.arraycopy(flatTrianglesAndVertices, vertexIdx2, vertex2Position, 0, 3);
			System.arraycopy(flatTrianglesAndVertices, vertexIdx3, vertex3Position, 0, 3);

			for (int localTriangleVertexIdx = 0; localTriangleVertexIdx < triangle.length; localTriangleVertexIdx++) {
				final FloatLocalizable vertexPosLocalizable = triangle[localTriangleVertexIdx];
				final float[] vertexPos = triangleAsArray[localTriangleVertexIdx];
				final TFloatArrayList vertexPosKey = new TFloatArrayList(vertexPos);

				int vertexIndex = vertexPositionIndexMap.get(vertexPosKey);
				if (vertexIndex < 0) {

					vertexIndex = transformedVertexPositions.size() / 3;
					vertexPositionIndexMap.put(vertexPosKey, vertexIndex);

					if (!Intervals.contains(vertexBounds, vertexPosLocalizable)) {
						overhangingVertexIndices.add(vertexIndex);
					}

					initialPos[0] = vertexPos[0];
					initialPos[1] = vertexPos[1];
					initialPos[2] = vertexPos[2];

					transform.apply(initialPos, transformedPos);
					transformedVertexPositions.add(transformedPos);
				}
				triangleVertexIndices.add(vertexIndex);

				TIntArrayList trianglesForVertex;
				if (vertexIndex < trianglesPerVertex.size()) {
					trianglesForVertex = trianglesPerVertex.get(vertexIndex);
				} else {
					trianglesForVertex = new TIntArrayList();
					trianglesPerVertex.add(trianglesForVertex);
				}
				trianglesForVertex.add(triangleIdx);
			}
			triangleIdx++;
		}

		vertices = transformedVertexPositions.toArray();
		vertexIndices = triangleVertexIndices.toArray();
		normals = new float[vertices.length];

		for (TIntArrayList triangles : trianglesPerVertex) {
			this.trianglesPerVertex.add(triangles.toArray());
		}
	}

	public void averageNormals() {

		final double[] triangleNormals = new double[vertexIndices.length]; // coincidental match 3 vertices and 3 coordinates

		for (int triangle = 0; triangle < vertexIndices.length; triangle += 3) {

			final int v1 = vertexIndices[triangle] * 3;
			final int v2 = vertexIndices[triangle + 1] * 3;
			final int v3 = vertexIndices[triangle + 2] * 3;

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

			triangleNormals[triangle] = x;
			triangleNormals[triangle + 1] = y;
			triangleNormals[triangle + 2] = z;
		}

		for (int vertex = 0; vertex < vertices.length; vertex += 3) {

			final int[] triangles = trianglesPerVertex.get(vertex / 3);
			double x = 0, y = 0, z = 0;
			for (final int triangle : triangles) {
				final int t = triangle * 3;
				x -= triangleNormals[t];
				y -= triangleNormals[t + 1];
				z -= triangleNormals[t + 2];
			}
			normals[vertex] = (float)(x / triangles.length);
			normals[vertex + 1] = (float)(y / triangles.length);
			normals[vertex + 2] = (float)(z / triangles.length);
		}
	}

	public void smooth(final double lambda, final int iterations) {

		final double[] newP = new double[3];
		final double[] vertexP = new double[3];
		final double[] vertexQ = new double[3];

		final float[] smoothedVertices = new float[vertices.length];
		for (int i = 0; i < iterations; ++i) {
			int curVertexIdx = 0;
			for (int[] triangles : trianglesPerVertex) {

				final int curVertex = curVertexIdx * 3;
				vertexP[0] = vertices[curVertex];
				vertexP[1] = vertices[curVertex + 1];
				vertexP[2] = vertices[curVertex + 2];

				Arrays.fill(newP, 0);
				double distanceWeightSum = 0;

				for (final int triangleIndex : triangles) {
					final int triangleFirstVertexIndex = triangleIndex * 3;

					for (int triangleLocalVertexIdx = 0; triangleLocalVertexIdx < 3; triangleLocalVertexIdx++) {
						final int triangleGlobalVertexIdx = vertexIndices[triangleFirstVertexIndex + triangleLocalVertexIdx];
						final int vertex = triangleGlobalVertexIdx * 3;
						if (vertex == curVertex)
							continue;

						vertexQ[0] = vertices[vertex];
						vertexQ[1] = vertices[vertex + 1];
						vertexQ[2] = vertices[vertex + 2];
						final var distWeight = 1.0 / LinAlgHelpers.distance(vertexP, vertexQ);
						if (Float.isInfinite((float)distWeight))
							continue;
						vertexQ[0] *= distWeight;
						vertexQ[1] *= distWeight;
						vertexQ[2] *= distWeight;
						LinAlgHelpers.add(newP, vertexQ, newP);

						distanceWeightSum += distWeight;
					}
				}

				/* This ensures that in the case where there are no adjacent vertices that are Float.isFinite,
				 *	we safely just don't smooth the vertex*/
				distanceWeightSum = distanceWeightSum == 0.0 ? 1.0 : distanceWeightSum;

				newP[0] *= 1.0 / distanceWeightSum;
				newP[1] *= 1.0 / distanceWeightSum;
				newP[2] *= 1.0 / distanceWeightSum;

				LinAlgHelpers.subtract(newP, vertexP, newP);

				smoothedVertices[curVertex] = vertices[curVertex] + (float)(lambda * newP[0]);
				smoothedVertices[curVertex + 1] = vertices[curVertex + 1] + (float)(lambda * newP[1]);
				smoothedVertices[curVertex + 2] = vertices[curVertex + 2] + (float)(lambda * newP[2]);
				curVertexIdx++;

			}
			System.arraycopy(smoothedVertices, 0, vertices, 0, vertices.length);
		}
	}

	public PainteraTriangleMesh asPainteraTriangleMesh() {

		final var nonOverhangingVertexIndices = new TIntArrayList();

		for (int idx = 0; idx < vertexIndices.length; idx += 3) {
			final int v1 = vertexIndices[idx];
			final int v2 = vertexIndices[idx + 1];
			final int v3 = vertexIndices[idx + 2];
			if (overhangingVertexIndices.contains(v1) || overhangingVertexIndices.contains(v2) || overhangingVertexIndices.contains(v3))
				continue;

			nonOverhangingVertexIndices.add(v1);
			nonOverhangingVertexIndices.add(v2);
			nonOverhangingVertexIndices.add(v3);

		}

		return new PainteraTriangleMesh(vertices, normals, nonOverhangingVertexIndices.toArray());
	}
}
