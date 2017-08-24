package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.Map;

import bdv.labels.labelset.LabelMultisetType;
import graphics.scenery.Mesh;
import net.imglib2.RandomAccessibleInterval;

/**
 * Chunk is a part of the volume. Each chunk knows its offset and keeps its mesh
 * in different resolutions.
 * 
 * @author vleite
 *
 */
public class Chunk
{
	/**
	 * Volume of the chunk.
	 */
	private RandomAccessibleInterval< LabelMultisetType > volume;

	/**
	 * offset of the chunk, to positioning it in the world.
	 */
	private int[] offset;

	/**
	 * All the mesh generated for the chunk and its resolution
	 */
	private Map< int[], Mesh > meshMap;

	private int index;

	public int getIndex()
	{
		return index;
	}

	public void setIndex( int index )
	{
		this.index = index;
	}

	/**
	 * Constructor, initialize variables with dummy values.
	 */
	public Chunk()
	{
		volume = null;
		offset = null;
		meshMap = new HashMap< int[], Mesh >();
		index = -1;
	}

	/**
	 * Return the volume of the chunk
	 * 
	 * @return RAI correspondent to the chunk
	 */
	public RandomAccessibleInterval< LabelMultisetType > getVolume()
	{
		return volume;
	}

	/**
	 * Define the volume for the chunk
	 * 
	 * @param volume
	 *            RAI
	 */
	public void setVolume( RandomAccessibleInterval< LabelMultisetType > volume )
	{
		this.volume = volume;
	}

	/**
	 * Return the offset of the chunk
	 * 
	 * @return int[] with x, y and z offset to remap the chunk in the world
	 */
	public int[] getOffset()
	{
		return offset;
	}

	/**
	 * Define the offset of the chunk
	 * 
	 * @param offset
	 *            int[] with x, y and z offset to remap the chunk in the world.
	 */
	public void setOffset( int[] offset )
	{
		this.offset = offset;
	}

	/**
	 * Define the {@link#mesh} generated for the chunk
	 * 
	 * @param mesh
	 *            sceneryMesh
	 * @param resolution
	 *            the cube size used to generate the mesh
	 */
	public void setMesh( Mesh mesh, int[] resolution )
	{
		meshMap.put( resolution, mesh );
	}

	/**
	 * Return the {@link#mesh} in a specific resolution.
	 * 
	 * @param resolution
	 *            cube size used to generate the mesh
	 * @return the {@link#mesh} at the given resolution, if no mesh was found
	 *         return null.
	 */
	public Mesh getMesh( int[] resolution )
	{
		return meshMap.get( resolution );
	}

	/**
	 * Indicates if the chunk contains the position informed. The bounding box
	 * of the chunk takes into account its offset.
	 * 
	 * @param position
	 *            x, y, z in world coordinates
	 * @return true if the point is inside the chunk, false otherwise.
	 */
	public boolean contains( int[] position )
	{
		assert volume.numDimensions() == position.length: "volume dimension is " + volume.numDimensions() + " and point dimension is " + position.length;

		for ( int i = 0; i < volume.numDimensions(); i++ )
		{
			if ( volume.max( i ) < position[ i ] || volume.min( i ) > position[ i ] )
			{
				return false;
			}
		}

		return true;
	}

	public int[] getChunkBoundinBox()
	{
		return new int[] { ( int ) volume.min( 0 ), ( int ) volume.min( 1 ), ( int ) volume.min( 2 ), ( int ) volume.max( 0 ), ( int ) volume.max( 1 ), ( int ) volume.max( 2 ) };
	}
}
