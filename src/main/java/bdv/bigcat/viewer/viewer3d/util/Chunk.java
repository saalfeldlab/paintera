package bdv.bigcat.viewer.viewer3d.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.labels.labelset.LabelMultisetType;
import graphics.scenery.Mesh;
import net.imglib2.Localizable;
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
	private Map< List< Integer >, Mesh > meshMap;

	/**
	 * Unique index of each chunk, it takes into account the position of the
	 * chunk in the world
	 */
	private int index;

	/**
	 * Constructor, initialize variables with dummy values.
	 */
	public Chunk()
	{
		volume = null;
		offset = null;
		meshMap = new HashMap< List< Integer >, Mesh >();
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
	 * @return int[] with x, y and z offset (the initial position of the chunk),
	 *         must be divided by the cubeSize to get the exact position in the
	 *         world
	 */
	public int[] getOffset()
	{
		return offset;
	}

	/**
	 * Define the offset of the chunk
	 * 
	 * @param offset
	 *            int[] with x, y and z offset (the initial position of the
	 *            chunk)
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
		meshMap.put( Arrays.asList( resolution[ 0 ], resolution[ 1 ], resolution[ 2 ] ), mesh );
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
		// for each entry in the map
		for ( Map.Entry< List< Integer >, Mesh > entry : meshMap.entrySet() )
		{
			// get the key and check if it is equal to the given resolution
			List< Integer > key = entry.getKey();
			for ( int i = 0; i < resolution.length; i++ )
			{
				// if one component is different
				if ( key.get( i ) != resolution[ i ] ) {
					return null;
				}
			}

			// if all components are equal, return the mesh
			return entry.getValue();
		}

		// if there is nothing in the map, return null
		return null;
	}

	/**
	 * @return number of meshes found in this chunk
	 */
	public int getNumberOfMeshResolutions()
	{
		return meshMap.size();
	}

	/**
	 * @return the unique chunk index
	 */
	public int getIndex()
	{
		return index;
	}

	/**
	 * define the index of the chunk
	 * 
	 * @param index
	 */
	public void setIndex( int index )
	{
		this.index = index;
	}

	/**
	 * Indicates if the chunk contains the position informed. The bounding box
	 * of the chunk takes into account its offset.
	 * 
	 * @param position
	 *            x, y, z in world coordinates
	 * @return true if the point is inside the chunk, false otherwise.
	 */
	public boolean contains( Localizable location )
	{
		assert volume.numDimensions() == location.numDimensions(): "volume dimension is " + volume.numDimensions() +
				" and point dimension is " + location.numDimensions();

		for ( int i = 0; i < volume.numDimensions(); i++ )
		{
			if ( volume.max( i ) < location.getIntPosition( i ) || volume.min( i ) > location.getIntPosition( i ) )
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * The bounding box is given by a vector of six positions: the first 3 are
	 * the (x, y, z) from the begin and the 3 lasts are the (x, y, z) for the
	 * end of the bb
	 * 
	 * @return the bounding box of the chunk
	 */
	public int[] getChunkBoundinBox()
	{
		return new int[] { ( int ) volume.min( 0 ), ( int ) volume.min( 1 ), ( int ) volume.min( 2 ),
				( int ) volume.max( 0 ), ( int ) volume.max( 1 ), ( int ) volume.max( 2 ) };
	}
}
