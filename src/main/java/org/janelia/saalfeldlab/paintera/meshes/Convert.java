package org.janelia.saalfeldlab.paintera.meshes;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import javafx.geometry.Point3D;
import net.imglib2.util.Triple;
import net.imglib2.util.ValueTriple;

/**
 * Calculate and assign surface normals of a triangle mesh.
 *
 * @author Philipp Hanslovsky
 */
public class Convert
{
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger( Convert.class );

	/**
	 * Convert a set of triangles defined by three vertices each into a reduced
	 * list of vertices and a LUT from vertex index to triangle indnex and
	 * from triangle index to vertex index.
	 *
	 * @param triangles
	 * @return
	 */
	public static Triple< TFloatArrayList, ArrayList< TIntArrayList >, ArrayList< TIntArrayList > > convertToLUT(
			final float[] triangles ) {

		assert triangles.length % 9 == 0;

		final TFloatArrayList vertices = new TFloatArrayList(); // stride 3
		final TObjectIntHashMap< Point3D > vertexIndexMap = new TObjectIntHashMap<>();
		final ArrayList< TIntArrayList > vertexTriangleLUT = new ArrayList<>();
		final ArrayList< TIntArrayList > triangleVertexLUT = new ArrayList<>();

		for ( int triangle = 0; triangle < triangles.length; triangle += 9 )
		{
			final int triangleIndex = triangle / 9;

			final Point3D[] keys = new Point3D[]{
				new Point3D( triangles[ triangle + 0 ], triangles[ triangle + 1 ], triangles[ triangle + 2 ] ),
				new Point3D( triangles[ triangle + 3 ], triangles[ triangle + 4 ], triangles[ triangle + 5 ] ),
				new Point3D( triangles[ triangle + 6 ], triangles[ triangle + 7 ], triangles[ triangle + 8 ] )
			};

			final TIntArrayList vertexIndices = new TIntArrayList();
			triangleVertexLUT.add(vertexIndices);
			for ( int i = 0; i < keys.length; ++i )
			{
				final Point3D key = keys[ i ];
				final int vertexIndex;
				if ( vertexIndexMap.contains( key ) )
					vertexIndex = vertexIndexMap.get( keys[ i ] );
				else
				{
					vertexIndex = vertices.size() / 3;
					vertexIndexMap.put( key, vertexIndex );
					vertices.add( ( float )key.getX() );
					vertices.add( ( float )key.getY() );
					vertices.add( ( float )key.getZ() );
				}
				vertexIndices.add(vertexIndex);

				final TIntArrayList triangleIndices;
				if ( vertexTriangleLUT.size() > vertexIndex )
				{
					triangleIndices =  vertexTriangleLUT.get( vertexIndex );
				}
				else
				{
					triangleIndices = new TIntArrayList();
					vertexTriangleLUT.add(triangleIndices);
				}
				triangleIndices.add(triangleIndex);
			}
		}
		return new ValueTriple< TFloatArrayList, ArrayList< TIntArrayList >, ArrayList< TIntArrayList > >(
				vertices,
				vertexTriangleLUT,
				triangleVertexLUT );
	}

	/**
	 * @param triangles
	 * @return
	 */
	public static float[] convertFromLUT(
			final TFloatArrayList vertices,
			ArrayList< TIntArrayList > triangleVertexLUT) {

		final float[] export = new float[ triangleVertexLUT.size() * 9 ];
		int t = -1;
		for ( final TIntArrayList triangleVertices : triangleVertexLUT )
		{
			for ( int i = 0; i < triangleVertices.size(); ++i )
			{
				int vertexIndex = triangleVertices.get( i ) * 3;
				export[ ++t ] = vertices.get( vertexIndex );
				export[ ++t ] = vertices.get( ++vertexIndex );
				export[ ++t ] = vertices.get( ++vertexIndex );
			}
		}
		return export;
	}
}
