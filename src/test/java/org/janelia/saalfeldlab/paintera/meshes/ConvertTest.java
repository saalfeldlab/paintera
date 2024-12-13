/**
 *
 */
package org.janelia.saalfeldlab.paintera.meshes;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import org.janelia.saalfeldlab.net.imglib2.util.Triple;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class ConvertTest {

	private static float[] triangles = new float[200 * 9];

	@BeforeAll
	public static void setUpBeforeClass() throws Exception {

		final Random rnd = new Random();
		for (int i = 0; i < triangles.length / 2; ++i)
			triangles[i] = (float)(2000 * rnd.nextDouble() - 1000);
		for (int i = triangles.length / 2; i < triangles.length - 12 * 10; ++i)
			triangles[i] = triangles[i - triangles.length / 2];
		for (int i = triangles.length - 9 * 10; i < triangles.length; ++i)
			triangles[i] = (float)(2000 * rnd.nextDouble() - 1000);
	}

	@Test
	public void test() {

		Triple<TFloatArrayList, ArrayList<TIntHashSet>, ArrayList<TIntArrayList>> luts = Convert.convertToLUT
				(triangles);

		ArrayList<TIntHashSet> vertexTriangleLUT = luts.getB();
		ArrayList<TIntArrayList> triangleVertexLUT = luts.getC();

		for (int i = 0; i < vertexTriangleLUT.size(); ++i) {
			final int[] triangleIndices = vertexTriangleLUT.get(i).toArray();
			for (int j = 0; j < triangleIndices.length; ++j)
				assertTrue(triangleVertexLUT.get(triangleIndices[j]).contains(i));
		}

		for (int i = 0; i < triangleVertexLUT.size(); ++i) {
			int[] vertexIndices = triangleVertexLUT.get(i).toArray();
			for (int j = 0; j < vertexIndices.length; ++j)
				assertTrue(vertexTriangleLUT.get(vertexIndices[j]).contains(i));
		}

		float[] test = Convert.convertFromLUT(luts.getA(), luts.getC());

		assertArrayEquals(triangles, test, 0.001f);
	}
}
