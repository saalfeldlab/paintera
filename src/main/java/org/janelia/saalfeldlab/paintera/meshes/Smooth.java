package org.janelia.saalfeldlab.paintera.meshes;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import kotlin.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smooth a triangle mesh.
 *
 * @author Stephan Saalfeld
 */
public class Smooth {

	/**
	 * logger
	 */
	private static final Logger LOG = LoggerFactory.getLogger(Smooth.class);

	public static final double DEFAULT_LAMBDA = 1.0;

	public static final int DEFAULT_ITERATIONS = 5;

	private static boolean isBoundary(
			final ArrayList<TIntHashSet> vertexTriangleLUT,
			final ArrayList<TIntHashSet> edgeSets,
			final int vertexIndex) {

		final TIntHashSet vertexTriangles = vertexTriangleLUT.get(vertexIndex);
		final TIntHashSet copy = new TIntHashSet();
		return edgeSets.get(vertexIndex).forEach(edge ->
		{
			copy.clear();
			copy.addAll(vertexTriangles);
			copy.retainAll(vertexTriangleLUT.get(edge));
			return copy.size() < 2;

		});
	}

	private static boolean[] boundaryVertices(
			final ArrayList<TIntHashSet> vertexTriangleLUT,
			final ArrayList<TIntArrayList> triangleVertexLUT) {

		final ArrayList<TIntHashSet> edgeSets = Convert.convertToEdgeSets(vertexTriangleLUT,
				triangleVertexLUT);
		final boolean[] boundaryVertices = new boolean[vertexTriangleLUT.size()];
		for (int i = 0; i < boundaryVertices.length; ++i) {
			boundaryVertices[i] = isBoundary(vertexTriangleLUT, edgeSets, i);
		}
		return boundaryVertices;
	}

	private static void getVertex(final float[] vertices, final double[] vertexRef, final int vertexIndex) {

		int i = vertexIndex * 3;
		vertexRef[0] = vertices[i];
		vertexRef[1] = vertices[++i];
		vertexRef[2] = vertices[++i];
	}

	private static void setVertex(final float[] vertices, final double[] vertexRef, final int vertexIndex) {

		int i = vertexIndex * 3;
		vertices[i] = (float)vertexRef[0];
		vertices[++i] = (float)vertexRef[1];
		vertices[++i] = (float)vertexRef[2];
	}

	private static void addVertex(final float[] vertices, final double[] vertexRef, final int vertexIndex) {

		int i = vertexIndex * 3;
		vertexRef[0] += vertices[i];
		vertexRef[1] += vertices[++i];
		vertexRef[2] += vertices[++i];
	}

	public static float[] smooth(final float[] vertices, final double lambda, final int iterations) {

		LOG.trace("Smoothing {} vertices with lambda={} and iterations={}", vertices.length, lambda, iterations);
		Triple<TFloatArrayList, ArrayList<TIntHashSet>, ArrayList<TIntArrayList>> luts = Convert
				.convertToLUT(
						vertices);
		float[] vertexCoordinates1 = luts.getFirst()
				.toArray();
		final ArrayList<TIntHashSet> vertexTriangleLUT = luts.getSecond();
		final ArrayList<TIntArrayList> triangleVertexLUT = luts.getThird();
		final boolean[] boundaryVertices =
				boundaryVertices(
						vertexTriangleLUT,
						triangleVertexLUT
				);

		for (int iteration = 0; iteration < iterations; ++iteration) {
			float[] vertexCoordinates2 = new float[vertexCoordinates1.length];

			final double[] vertexRef = new double[3];
			final double[] otherVertexRef = new double[3];
			AtomicInteger count = new AtomicInteger(0);
			final TIntHashSet otherVertexIndices = new TIntHashSet();
			for (int vertexIndex = 0; vertexIndex < vertexTriangleLUT.size(); ++vertexIndex) {
				getVertex(vertexCoordinates1, vertexRef, vertexIndex);
				if (!boundaryVertices[vertexIndex]) {
					final int fVertexIndex = vertexIndex;
					count.set(0);
					otherVertexIndices.clear();
					otherVertexRef[0] = 0;
					otherVertexRef[1] = 0;
					otherVertexRef[2] = 0;

					vertexTriangleLUT.get(vertexIndex).forEach(otherTriangleIndex ->
					{
						final TIntArrayList otherVertices = triangleVertexLUT.get(otherTriangleIndex);
						for (int k = 0; k < otherVertices.size(); ++k) {
							final int otherVertexIndex = otherVertices.get(k);
							if (otherVertexIndex != fVertexIndex)
								otherVertexIndices.add(otherVertexIndex);
						}
						return true;
					});

					final float[] fVertexCoordinates1 = vertexCoordinates1;

					otherVertexIndices.forEach(l -> {
						count.incrementAndGet();
						addVertex(fVertexCoordinates1, otherVertexRef, l);
						return true;
					});

					final double c = 1.0 / count.get();
					vertexRef[0] = (otherVertexRef[0] * c - vertexRef[0]) * lambda + vertexRef[0];
					vertexRef[1] = (otherVertexRef[1] * c - vertexRef[1]) * lambda + vertexRef[1];
					vertexRef[2] = (otherVertexRef[2] * c - vertexRef[2]) * lambda + vertexRef[2];

					//					System.out.println( "count = " + count.get() );
				}
				//				else
				//					System.out.println( "leaving boundary vertex untouched." );

				setVertex(vertexCoordinates2, vertexRef, vertexIndex);
			}
			vertexCoordinates1 = vertexCoordinates2;
		}

		return Convert.convertFromLUT(TFloatArrayList.wrap(vertexCoordinates1), triangleVertexLUT);
	}
}
