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

import gnu.trove.map.hash.TLongIntHashMap;
import bdv.bigcat.SegmentBodyAssignment;


/**
 * Generates a stream of saturated colors.  Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Changing the seed of the stream makes a new sequence.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractSaturatedARGBStream implements ARGBSource
{
	final static protected double[] rs = new double[]{ 1, 1, 0, 0, 0, 1, 1 };
	final static protected double[] gs = new double[]{ 0, 1, 1, 1, 0, 0, 0 };
	final static protected double[] bs = new double[]{ 0, 0, 0, 1, 1, 1, 0 };

	protected long seed = 0;
	protected int alpha = 0x2000000;
	protected int activeSegmentAlpha = 0xff000000;
	protected int activeBodyAlpha = 0x80000000;
	protected long activeSegment = 0L;
	protected long activeBody = 0l;
	final protected SegmentBodyAssignment assignment;

	public AbstractSaturatedARGBStream( final SegmentBodyAssignment assignment )
	{
		this.assignment = assignment;
	}

	//protected Long2IntOpenHashMap argbCache = new Long2IntOpenHashMap();
	protected TLongIntHashMap argbCache = new TLongIntHashMap();

	final static protected int interpolate( final double[] xs, final int k, final int l, final double u, final double v )
	{
		return ( int )( ( v * xs[ k ] + u * xs[ l ] ) * 255.0 + 0.5 );
	}

	final static protected int argb( final int r, final int g, final int b, final int alpha )
	{
		return ( ( ( r << 8 ) | g ) << 8 ) | b | alpha;
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
		activeSegment = segmentId;
		activeBody = assignment.getBody( segmentId );
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
