package bdv.bigcat.viewer;

/**
 * the primary structure used to pass around the components of a mesh
 */
public class SimpleMesh
{
	/**
	 * the number of vertices and normals
	 */
	int numberOfVertices;

	/**
	 * the vertices positions as an array of points 
	 */
	float[][] vertices;

	/**
	 * the number of faces
	 */
	public int numberOfTriangles;

	/**
	 * the triangles given by 3 vertex indices (length = numberOfTriangles * 3)
	 */
	int[] triangles;

	SimpleMesh( int vertexCount, float[][] verticesArray, int trianglesCount, int[] trianglesArray )
	{
		numberOfVertices = vertexCount;
		vertices = verticesArray;
		numberOfTriangles = trianglesCount;
		triangles = trianglesArray;
	}

	public SimpleMesh()
	{}
	
	public int getNumberOfVertices()
	{
		return numberOfVertices;
	}
	
	public void setNumberOfVertices( int nVertices )
	{
		numberOfVertices = nVertices;
	}
	
	public int getNumberOfTriangles()
	{
		return numberOfTriangles;
	}

	public void setNumberOfTriangles( int ntriangles )
	{
		numberOfTriangles= ntriangles;
	}

	public float[][] getVertices()
	{
		return vertices;
	}

	public void setVertices(float[][] verticesArray)
	{
		vertices = verticesArray;
	}

	public int[] getTriangles()
	{
		return triangles;
	}
	
	public void setTriangles( int[] trianglesArray)
	{
		triangles = trianglesArray;
	}

}
