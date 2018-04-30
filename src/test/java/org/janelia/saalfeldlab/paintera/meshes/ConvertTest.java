/**
 *
 */
package org.janelia.saalfeldlab.paintera.meshes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import net.imglib2.util.Triple;


/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class ConvertTest {

	private static float[] triangles = new float[ 200 * 9 ];

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		final Random rnd = new Random();
		for ( int i = 0; i < triangles.length / 2; ++i )
			triangles[ i ] = ( float )( 2000 * rnd.nextDouble() - 1000 );
		for ( int i = triangles.length / 2; i < triangles.length - 12 * 10; ++i )
			triangles[ i ] = triangles[ i - triangles.length / 2 ];
		for ( int i = triangles.length - 9 * 10; i < triangles.length; ++i )
			triangles[ i ] = ( float )( 2000 * rnd.nextDouble() - 1000 );
	}

	@Test
	public void test()
	{
		Triple< TFloatArrayList, ArrayList< TIntArrayList >, ArrayList< TIntArrayList > > luts = Convert.convertToLUT( triangles );

		ArrayList< TIntArrayList > vertexTriangleLUT = luts.getB();
		ArrayList< TIntArrayList > triangleVertexLUT = luts.getC();

		for ( int i = 0; i < vertexTriangleLUT.size(); ++i ) {
			TIntArrayList triangleIndices = vertexTriangleLUT.get( i );
			for ( int j = 0; j < triangleIndices.size(); ++j )
				assertTrue( triangleVertexLUT.get( triangleIndices.get( j ) ).contains( i ) );
		}

		for ( int i = 0; i < triangleVertexLUT.size(); ++i ) {
			TIntArrayList vertexIndices = triangleVertexLUT.get( i );
			for ( int j = 0; j < vertexIndices.size(); ++j )
				assertTrue( vertexTriangleLUT.get( vertexIndices.get( j ) ).contains( i ) );
		}

		float[] test = Convert.convertFromLUT( luts.getA(), luts.getC() );

		assertArrayEquals( triangles, test, 0.001f );
	}
}
