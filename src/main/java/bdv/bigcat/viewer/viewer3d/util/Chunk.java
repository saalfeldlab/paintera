package bdv.bigcat.viewer.viewer3d.util;

import net.imglib2.Interval;
import net.imglib2.Localizable;

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
	private final Interval interval;

	private final long index;

	/**
	 * Constructor, initialize variables with dummy values.
	 */
	public Chunk( final Interval interval, final long index )
	{
		this.interval = interval;
		this.index = index;
	}

	public long index()
	{
		return index;
	}

	public Interval interval()
	{
		return this.interval;
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
		assert interval.numDimensions() == location.numDimensions(): "volume dimension is " + interval.numDimensions() +
				" and point dimension is " + location.numDimensions();

		for ( int i = 0; i < interval.numDimensions(); i++ )
			if ( interval.max( i ) < location.getIntPosition( i ) || interval.min( i ) > location.getIntPosition( i ) )
				return false;

		return true;
	}
}
