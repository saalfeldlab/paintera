package bdv.bigcat.viewer.viewer3d.util;

import java.util.Optional;

import graphics.scenery.Mesh;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;

/**
 * Chunk is a part of the volume. Each chunk knows its offset and keeps its mesh
 * in different resolutions.
 *
 * @author vleite
 * @param <T>
 * @param <T>
 *
 */
public class Chunk< T >
{
	/**
	 * Volume of the chunk.
	 */
	private final RandomAccessibleInterval< T > volume;

	/**
	 * offset of the chunk, to positioning it in the world.
	 */
	private final long[] offset;

	/**
	 * All the mesh generated for the chunk and its resolution
	 */
	private Optional< Mesh > mesh;

	/**
	 * Unique index of each chunk, it takes into account the position of the
	 * chunk in the world
	 */
	private final int index;

	/**
	 * Constructor, initialize variables with dummy values.
	 */
	public Chunk( final RandomAccessibleInterval< T > volume, final long[] offset, final int index )
	{
		this.volume = volume;
		this.offset = offset;
		this.mesh = Optional.empty();
		this.index = index;
	}

	/**
	 * Return the volume of the chunk
	 *
	 * @return RAI correspondent to the chunk
	 */
	public RandomAccessibleInterval< T > getVolume()
	{
		return volume;
	}

	/**
	 * Return the offset of the chunk
	 *
	 * @return int[] with x, y and z offset (the initial position of the chunk),
	 *         must be divided by the cubeSize to get the exact position in the
	 *         world
	 */
	public long[] getOffset()
	{
		return offset;
	}

	public void addMesh( final Mesh mesh )
	{
		this.mesh = Optional.of( mesh );
	}

	/**
	 * @return the unique chunk index
	 */
	public int getIndex()
	{
		return index;
	}

	/**
	 * Indicates if the chunk contains the position informed. The bounding box
	 * of the chunk takes into account its offset.
	 *
	 * @param position
	 *            x, y, z in world coordinates
	 * @return true if the point is inside the chunk, false otherwise.
	 */
	public boolean contains( final Localizable location )
	{
		assert volume.numDimensions() == location.numDimensions(): "volume dimension is " + volume.numDimensions() +
				" and point dimension is " + location.numDimensions();

		for ( int i = 0; i < volume.numDimensions(); i++ )
			if ( volume.max( i ) < location.getIntPosition( i ) || volume.min( i ) > location.getIntPosition( i ) )
				return false;

		return true;
	}

	/**
	 * The bounding box is given by a vector of volume.numDimensions * 2
	 * positions: the first half contains the begin in each dimension and the
	 * second half contains the end in each dimension
	 *
	 * @return the bounding box of the chunk
	 */
	public int[] getChunkBoundinBox()
	{
		// begin and end of each dimension
		int[] bb = new int[ volume.numDimensions() * 2 ];
		// first half is the minimum in each dimension
		for ( int i = 0; i < volume.numDimensions(); i++ )
		{
			bb[ i ] = ( int ) volume.min( i );
		}

		// second half is the maximum of each dimension
		for ( int i = volume.numDimensions(); i < volume.numDimensions() * 2; i++ )
		{
			bb[ i ] = ( int ) volume.max( i );
		}

		return bb;
	}
}
