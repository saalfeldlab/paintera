package bdv.bigcat.ui;

import java.util.ArrayList;

/**
 * Marching cubes based on
 * https://github.com/ShibbyTheCookie/MarchingCubes/tree/master/MarchingCubesJava
 * 
 * Using generic type
 */
public class MarchingCubes2< T extends Comparable< T > >
{
	ArrayList< float[] > vertices = new ArrayList<>();
	ArrayList< float[] > normals = new ArrayList<>();
	
	static float[] lerp( float[] vec1, float[] vec2, float alpha )
	{
		return new float[] { vec1[ 0 ] + ( vec2[ 0 ] - vec1[ 0 ] ) * alpha, vec1[ 1 ] + ( vec2[ 1 ] - vec1[ 1 ] ) * alpha, vec1[ 2 ] + ( vec2[ 2 ] - vec1[ 2 ] ) * alpha };
	}

	// Actual position along edge weighted according to function values.
	float vertList[][] = new float[ 12 ][ 3 ];

	public ArrayList< float[] > marchingCubes( T[] values, int[] volDim, float[] voxDim, T isoLevel, int offset )
	{
		// Calculate maximal possible axis value (used in vertice normalization)
		float maxX = voxDim[ 0 ] * ( volDim[ 0 ] - 1 );
		float maxY = voxDim[ 1 ] * ( volDim[ 1 ] - 1 );
		float maxZ = voxDim[ 2 ] * ( volDim[ 2 ] - 1 );
		float maxAxisVal = Math.max( maxX, Math.max( maxY, maxZ ) );

		int total = 0;
		// Volume iteration
		for ( int z = 0; z < volDim[ 2 ] - 1; z++ )
		{
			for ( int y = 0; y < volDim[ 1 ] - 1; y++ )
			{
				for ( int x = 0; x < volDim[ 0 ] - 1; x++ )
				{

					// Indices pointing to cube vertices
					//              pyz  ___________________  pxyz
					//                  /|                 /|
					//                 / |                / |
					//                /  |               /  |
					//          pz   /___|______________/pxz|
					//              |    |              |   |
					//              |    |              |   |
					//              | py |______________|___| pxy
					//              |   /               |   /
					//              |  /                |  /
					//              | /                 | /
					//              |/__________________|/
					//             p                     px

					int p = x + ( volDim[ 0 ] * y ) + ( volDim[ 0 ] * volDim[ 1 ] * ( z + offset ) ),
							px = p + 1,
							py = p + volDim[ 0 ],
							pxy = py + 1,
							pz = p + volDim[ 0 ] * volDim[ 1 ],
							pxz = px + volDim[ 0 ] * volDim[ 1 ],
							pyz = py + volDim[ 0 ] * volDim[ 1 ],
							pxyz = pxy + volDim[ 0 ] * volDim[ 1 ];

					System.out.println();
					// X Y Z
					float position[] = new float[] { x * voxDim[ 0 ], y * voxDim[ 1 ], ( z + offset ) * voxDim[ 2 ] };

					// Voxel intensities
					T value0 = values[ p ],
							value1 = values[ px ],
							value2 = values[ py ],
							value3 = values[ pxy ],
							value4 = values[ pz ],
							value5 = values[ pxz ],
							value6 = values[ pyz ],
							value7 = values[ pxyz ];

					// Voxel is active if its intensity is above isolevel
					int cubeindex = 0;
					if ( value0.compareTo( isoLevel ) > 0 )
						cubeindex |= 1;
					if ( value1.compareTo( isoLevel ) > 0 )
						cubeindex |= 2;
					if ( value2.compareTo( isoLevel ) > 0 )
						cubeindex |= 8;
					if ( value3.compareTo( isoLevel ) > 0 )
						cubeindex |= 4;
					if ( value4.compareTo( isoLevel ) > 0 )
						cubeindex |= 16;
					if ( value5.compareTo( isoLevel ) > 0 )
						cubeindex |= 32;
					if ( value6.compareTo( isoLevel ) > 0 )
						cubeindex |= 128;
					if ( value7.compareTo( isoLevel ) > 0 )
						cubeindex |= 64;

					// Fetch the triggered edges
					int bits = TablesMC.MC_EDGE_TABLE[ cubeindex ];

					// If no edge is triggered... skip
					if ( bits == 0 )
						continue;

					// Interpolate the positions based od voxel intensities
					float mu = 0.5f;

					// bottom of the cube
					if ( ( bits & 1 ) != 0 )
					{
						T first = diff( isoLevel, value0 );
						T second = diff( value1, value0 );
						mu = ( float ) div( first, second );
//                        mu = (isoLevel - value0) / (value1 - value0);
						vertList[ 0 ] = lerp( position, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ], position[ 2 ] }, mu );
					}
					if ( ( bits & 2 ) != 0 )
					{
						T first = diff( isoLevel, value1 );
						T second = diff( value3, value1 );
						mu = div( first, second );
//                    	mu = (isoLevel - value1) / (value3 - value1);
						vertList[ 1 ] = lerp( new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ], position[ 2 ] }, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] }, mu );
					}
					if ( ( bits & 4 ) != 0 )
					{
						T first = diff( isoLevel, value2 );
						T second = diff( value3, value2 );
						mu = div( first, second );
//                        mu = (isoLevel - value2) / (value3 - value2);
						vertList[ 2 ] = lerp( new float[] { position[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] }, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] }, mu );
					}
					if ( ( bits & 8 ) != 0 )
					{
						T first = diff( isoLevel, value0 );
						T second = diff( value2, value0 );
						mu = div( first, second );
//                    	mu = (isoLevel - value0) / (value2 - value0);
						vertList[ 3 ] = lerp( position, new float[] { position[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] }, mu );
					}
					// top of the cube
					if ( ( bits & 16 ) != 0 )
					{
						T first = diff( isoLevel, value4 );
						T second = diff( value5, value4 );
						mu = div( first, second );
//                        mu = (isoLevel - value4) / (value5 - value4);
						vertList[ 4 ] = lerp( new float[] { position[ 0 ], position[ 1 ], position[ 2 ] + voxDim[ 2 ] }, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}
					if ( ( bits & 32 ) != 0 )
					{
						T first = diff( isoLevel, value5 );
						T second = diff( value7, value5 );
						mu = div( first, second );
//                        mu = (isoLevel - value5) / (value7 - value5);
						vertList[ 5 ] = lerp( new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ], position[ 2 ] + voxDim[ 2 ] }, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}
					if ( ( bits & 64 ) != 0 )
					{
						T first = diff( isoLevel, value6 );
						T second = diff( value7, value6 );
						mu = div( first, second );
//                        mu = (isoLevel - value6) / (value7 - value6);
						vertList[ 6 ] = lerp( new float[] { position[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] + voxDim[ 2 ] }, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}
					if ( ( bits & 128 ) != 0 )
					{
						T first = diff( isoLevel, value4 );
						T second = diff( value6, value4 );
						mu = div( first, second );
//                    	mu = (isoLevel - value4) / (value6 - value4);
						vertList[ 7 ] = lerp( new float[] { position[ 0 ], position[ 1 ], position[ 2 ] + voxDim[ 2 ] }, new float[] { position[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}
					// vertical lines of the cube
					if ( ( bits & 256 ) != 0 )
					{
						T first = diff( isoLevel, value0 );
						T second = diff( value4, value0 );
						mu = div( first, second );
//                    	mu = (isoLevel - value0) / (value4 - value0);
						vertList[ 8 ] = lerp( position, new float[] { position[ 0 ], position[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}
					if ( ( bits & 512 ) != 0 )
					{
						T first = diff( isoLevel, value1 );
						T second = diff( value5, value1 );
						mu = div( first, second );
//                        mu = (isoLevel - value1) / (value5 - value1);
						vertList[ 9 ] = lerp( new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ], position[ 2 ] }, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}
					if ( ( bits & 1024 ) != 0 )
					{
						T first = diff( isoLevel, value3 );
						T second = diff( value7, value3 );
						mu = div( first, second );
//                        mu = (isoLevel - value3) / (value7 - value3);
						vertList[ 10 ] = lerp( new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] }, new float[] { position[ 0 ] + voxDim[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}
					if ( ( bits & 2048 ) != 0 )
					{
						T first = diff( isoLevel, value2 );
						T second = diff( value6, value2 );
						mu = div( first, second );
//                        mu = (isoLevel - value2) / (value6 - value2);
						vertList[ 11 ] = lerp( new float[] { position[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] }, new float[] { position[ 0 ], position[ 1 ] + voxDim[ 1 ], position[ 2 ] + voxDim[ 2 ] }, mu );
					}

					// construct triangles -- get correct vertices from
					// triTable.
					int i = 0;
					// "Re-purpose cubeindex into an offset into triTable."
					cubeindex <<= 4;

					while ( TablesMC.MC_TRI_TABLE[ cubeindex + i ] != -1 )
					{
						int index1 = TablesMC.MC_TRI_TABLE[ cubeindex + i ];
						int index2 = TablesMC.MC_TRI_TABLE[ cubeindex + i + 1 ];
						int index3 = TablesMC.MC_TRI_TABLE[ cubeindex + i + 2 ];

						// Add triangles vertices normalized with the maximal
						// possible value
						vertices.add( new float[] { vertList[ index3 ][ 0 ] / maxAxisVal - 0.5f, vertList[ index3 ][ 1 ] / maxAxisVal - 0.5f, vertList[ index3 ][ 2 ] / maxAxisVal - 0.5f } );
						vertices.add( new float[] { vertList[ index2 ][ 0 ] / maxAxisVal - 0.5f, vertList[ index2 ][ 1 ] / maxAxisVal - 0.5f, vertList[ index2 ][ 2 ] / maxAxisVal - 0.5f } );
						vertices.add( new float[] { vertList[ index1 ][ 0 ] / maxAxisVal - 0.5f, vertList[ index1 ][ 1 ] / maxAxisVal - 0.5f, vertList[ index1 ][ 2 ] / maxAxisVal - 0.5f } );

						i += 3;
					}
					
					total += i;
				}
			}
		}
		
		System.out.println("number of vertices: " + total);
		
		calculateNormals();
		return vertices;
	}

	public void calculateNormals() {
		
		normals = vertices;
		
		// Calculate normals.
		for (int i = 0; i < vertices.size(); i++) {
			float[] vec1, vec2, normal;
			int id0, id1, id2;
			float[] triangle = vertices.get( i );
			id0 = ( int ) triangle[0];
			id1 = ( int ) triangle[1];
			id2 = ( int ) triangle[2];
			vec1 = vertList[id1]; // - vertList[id0];
//			vec2 = _mesh->getVertex(id2) - _mesh->getVertex(id0);
//			normal.x() = vec1.z()*vec2.y() - vec1.y()*vec2.z();
//			normal.y() = vec1.x()*vec2.z() - vec1.z()*vec2.x();
//			normal.z() = vec1.y()*vec2.x() - vec1.x()*vec2.y();
//			_mesh->getNormal(id0) += normal;
//			_mesh->getNormal(id1) += normal;
//			_mesh->getNormal(id2) += normal;
//		}
//
//		// Normalize normals.
//		for (unsigned int i = 0; i < _nNormals; i++) {
//			float length = sqrt(
//					_mesh->getNormal(i).x()*_mesh->getNormal(i).x() +
//					_mesh->getNormal(i).y()*_mesh->getNormal(i).y() +
//					_mesh->getNormal(i).z()*_mesh->getNormal(i).z());
//			_mesh->getNormal(i) /= length;
		}

	}
	
	@SuppressWarnings( "unchecked" )
	public T diff( T a, T b )
	{

		if ( a == null || b == null ) { return null; }

		if ( a instanceof Double )
		{
			return ( ( T ) new Double( ( ( Double ) a ).doubleValue() - ( ( Double ) b ).doubleValue() ) );
		}
		else if ( a instanceof Integer )
		{
			return ( ( T ) new Integer( ( ( Integer ) a ).intValue() - ( ( Integer ) b ).intValue() ) );
		}
		else if ( a instanceof Float )
		{
			return ( ( T ) new Float( ( ( Float ) a ).floatValue() - ( ( Float ) b ).floatValue() ) );
		}
		else if ( a instanceof Short )
		{
			return ( ( T ) new Short( ( short ) ( ( ( Short ) a ).shortValue() - ( ( Short ) b ).shortValue() ) ) );
		}
		else
		{
			throw new IllegalArgumentException( "Type " + a.getClass() + " is not supported by this method" );
		}
	}

	public float div( T a, T b )
	{

		if ( a == null || b == null ) { return 0; }

		if ( a instanceof Double )
		{
			return ( new Float( ( ( Double ) a ).doubleValue() / ( ( Double ) b ).doubleValue() ) );
		}
		else if ( a instanceof Integer )
		{
			return ( new Float( ( ( Integer ) a ).intValue() / ( ( Integer ) b ).intValue() ) );
		}
		else if ( a instanceof Float )
		{
			return ( new Float( ( ( Float ) a ).floatValue() / ( ( Float ) b ).floatValue() ) );
		}
		else if ( a instanceof Short )
		{
			return ( new Float( ( short ) ( ( ( Short ) a ).shortValue() / ( ( Short ) b ).shortValue() ) ) );
		}
		else
		{
			throw new IllegalArgumentException( "Type " + a.getClass() + " is not supported by this method" );
		}
	}
}