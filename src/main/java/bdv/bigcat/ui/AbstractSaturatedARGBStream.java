/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.bigcat.ui;

import bdv.bigcat.FragmentSegmentAssignment;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TLongIntHashMap;


/**
 * Generates a stream of saturated colors.  Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Changing the seed of the stream makes a new sequence.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractSaturatedARGBStream implements ARGBStream
{
	final static protected double[] rs = new double[]{ 1, 1, 0, 0, 0, 1, 1 };
	final static protected double[] gs = new double[]{ 0, 1, 1, 1, 0, 0, 0 };
	final static protected double[] bs = new double[]{ 0, 0, 0, 1, 1, 1, 0 };

	protected long seed = 0;
	protected int alpha = 0x2000000;
	protected int activeFragmentAlpha = 0xff000000;
	protected int activeSegmentAlpha = 0x80000000;
	protected long activeFragment = 0L;
	protected long activeSegment = 0l;
	final protected FragmentSegmentAssignment assignment;

	public AbstractSaturatedARGBStream( final FragmentSegmentAssignment assignment )
	{
		this.assignment = assignment;
	}

	//protected Long2IntOpenHashMap argbCache = new Long2IntOpenHashMap();
	protected TLongIntHashMap argbCache = new TLongIntHashMap(
			Constants.DEFAULT_CAPACITY ,
			Constants.DEFAULT_LOAD_FACTOR,
			0,
			0 );

	final static protected int interpolate( final double[] xs, final int k, final int l, final double u, final double v )
	{
		return ( int )( ( v * xs[ k ] + u * xs[ l ] ) * 255.0 + 0.5 );
	}

	final static protected int argb( final int r, final int g, final int b, final int alpha )
	{
		return ( ( ( r << 8 ) | g ) << 8 ) | b | alpha;
	}

	abstract protected double getDouble( final long id );

	@Override
	final public int argb( final long fragmentId )
	{
		final long segmentId = assignment.getSegment( fragmentId );
		int argb = argbCache.get( segmentId );
		if ( argb == 0x00000000 )
		{
			double x = getDouble( seed + segmentId );
			x *= 6.0;
			final int k = ( int )x;
			final int l = k + 1;
			final double u = x - k;
			final double v = 1.0 - u;

			final int r = interpolate( rs, k, l, u, v );
			final int g = interpolate( gs, k, l, u, v );
			final int b = interpolate( bs, k, l, u, v );

			argb = argb( r, g, b, alpha );

			argbCache.put( segmentId, argb );
		}
		if ( activeFragment == fragmentId )
			argb = argb & 0x00ffffff | activeFragmentAlpha;
		else if ( activeSegment == segmentId )
			argb = argb & 0x00ffffff | activeSegmentAlpha;

		return argb;
	}

	/**
	 * Change the seed.
	 *
	 * @param seed
	 */
	public void setSeed( final long seed )
	{
		this.seed = seed;
	}

	/**
	 * Increment seed.
	 */
	public void incSeed()
	{
		++seed;
	}

	/**
	 * Decrement seed.
	 */
	public void decSeed()
	{
		--seed;
	}

	/**
	 *
	 */
	public void setActive( final long segmentId )
	{
		activeFragment = segmentId;
		activeSegment = assignment.getSegment( segmentId );
	}

	/**
	 * Change alpha.  Values less or greater than [0,255] will be masked.
	 *
	 * @param alpha
	 */
	public void setAlpha( final int alpha )
	{
		this.alpha = alpha << 24;
	}


	public void clearCache()
	{
		argbCache.clear();
	}
}
