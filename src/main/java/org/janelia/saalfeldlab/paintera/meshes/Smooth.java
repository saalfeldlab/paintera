package org.janelia.saalfeldlab.paintera.meshes;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import net.imglib2.util.Triple;

/**
 * Smooth a triangle mesh
 *
 * @author Stephan Saalfeld
 */
public class Smooth
{
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger( Smooth.class );

	private static void getVertex( final float[] vertices, final double[] vertexRef, final int vertexIndex )
	{
		int i = vertexIndex * 3;
		vertexRef[ 0 ] = vertices[ i ];
		vertexRef[ 1 ] = vertices[ ++i ];
		vertexRef[ 2 ] = vertices[ ++i ];
	}

	private static void setVertex( final float[] vertices, final double[] vertexRef, final int vertexIndex )
	{
		int i = vertexIndex * 3;
		vertices[ i ] = ( float )vertexRef[ 0 ];
		vertices[ ++i ] = ( float )vertexRef[ 1 ];
		vertices[ ++i ] = ( float )vertexRef[ 2 ];
	}


	private static void addVertex( final float[] vertices, final double[] vertexRef, final int vertexIndex )
	{
		int i = vertexIndex * 3;
		vertexRef[ 0 ] += vertices[ i ];
		vertexRef[ 1 ] += vertices[ ++i ];
		vertexRef[ 2 ] += vertices[ ++i ];
	}

	public static float[] smooth( final float[] vertices, final double lambda, final int iterations )
	{
		Triple< TFloatArrayList, ArrayList< TIntArrayList >, ArrayList< TIntArrayList > > luts = Convert.convertToLUT( vertices );
		float[] vertexCoordinates1 = luts.getA().toArray();
		final ArrayList< TIntArrayList > vertexTriangleLUT = luts.getB();
		final ArrayList< TIntArrayList > triangleVertexLUT = luts.getC();

		for ( int iteration = 0; iteration < iterations; ++iteration )
		{
			float[] vertexCoordinates2 = new float[ vertexCoordinates1.length ];

			final double[] vertexRef = new double[ 3 ];
			final double[] otherVertexRef = new double[ 3 ];
			AtomicInteger count = new AtomicInteger( 0 );
			final TIntHashSet otherVertexIndices = new TIntHashSet();
			for ( int vertexIndex = 0; vertexIndex < vertexTriangleLUT.size(); ++vertexIndex )
			{
				count.set( 0 );
				otherVertexIndices.clear();
				otherVertexRef[ 0 ] = 0;
				otherVertexRef[ 1 ] = 0;
				otherVertexRef[ 2 ] = 0;
				getVertex( vertexCoordinates1, vertexRef, vertexIndex );

				final TIntArrayList triangles = vertexTriangleLUT.get( vertexIndex );
				for ( int j = 0; j < triangles.size(); ++j )
				{
					final int otherTriangleIndex = triangles.get( j );
					final TIntArrayList otherVertices = triangleVertexLUT.get( otherTriangleIndex );
					for ( int k = 0; k < otherVertices.size(); ++k )
					{
						final int otherVertexIndex = otherVertices.get( k );
						if ( otherVertexIndex != vertexIndex )
							otherVertexIndices.add( otherVertexIndex );
					}
				}

				final float[] fVertexCoordinates1 = vertexCoordinates1;

				otherVertexIndices.forEach( l -> {
					count.incrementAndGet();
					addVertex( fVertexCoordinates1, otherVertexRef, l );
					return true;
				});

				final double c = 1.0 / count.get();
				vertexRef[ 0 ] = ( otherVertexRef[ 0 ] * c - vertexRef[ 0 ] ) * lambda + vertexRef[ 0 ];
				vertexRef[ 1 ] = ( otherVertexRef[ 1 ] * c - vertexRef[ 1 ] ) * lambda + vertexRef[ 1 ];
				vertexRef[ 2 ] = ( otherVertexRef[ 2 ] * c - vertexRef[ 2 ] ) * lambda + vertexRef[ 2 ];

				setVertex( vertexCoordinates2, vertexRef, vertexIndex );

				System.out.println( "count = " + count.get() );
			}
			vertexCoordinates1 = vertexCoordinates2;
		}

		return Convert.convertFromLUT( TFloatArrayList.wrap( vertexCoordinates1 ), triangleVertexLUT);
	}
}
