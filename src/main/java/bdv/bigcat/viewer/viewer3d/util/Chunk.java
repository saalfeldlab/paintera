package bdv.bigcat.viewer.viewer3d.util;

import net.imglib2.Interval;

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
}
