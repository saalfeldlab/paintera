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
