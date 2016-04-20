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
			argb = id2argb(seed + segmentId);
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

	protected final static int hsva2argb(double h, double s, double v, int alpha) {

		if(s < 0) s = 0;
		if(s > 1) s = 1;
		if(v < 0) v = 0;
		if(v > 1) v = 1;
		
		int r = 0;
		int g = 0;
		int b = 0;

		if(s == 0) {
			r = (int)(255.0*v);
			g = (int)(255.0*v);
			b = (int)(255.0*v);
		}

		h = h%1.0; // want h to be in 0..1

		int i = (int)(h*6);
		double f = (h*6) - i;
		double p = v*(1.0f - s); 
		double q = v*(1.0f - s*f);
		double t = v*(1.0f - s*(1.0f-f));
		switch(i%6) {
		case 0:
			r = (int)(255.0*v);
			g = (int)(255.0*t);
			b = (int)(255.0*p);
			break;
		case 1:
			r = (int)(255.0*q);
			g = (int)(255.0*v);
			b = (int)(255.0*p);
			break;
		case 2:
			r = (int)(255.0*p);
			g = (int)(255.0*v);
			b = (int)(255.0*t);
			break;
		case 3:
			r = (int)(255.0*p);
			g = (int)(255.0*q);
			b = (int)(255.0*v);
			break;
		case 4:
			r = (int)(255.0*t);
			g = (int)(255.0*p);
			b = (int)(255.0*v);
			break;
		case 5:
			r = (int)(255.0*v);
			g = (int)(255.0*p);
			b = (int)(255.0*q);
			break;
		}
		return argb(r, g, b, alpha);
	}

	protected final int id2argb(long l) {

		double x = 0.4671057256451202*(l*(l+1))%1.0;
		double y = 0.6262286337141059*(l*(l+2))%1.0;
		double z = 0.9424373277692188*(l*(l+3))%1.0;

		double h = x;
		double s = 0.8 + y*0.2;
		double v = (l == 0 ? 0.0 : 0.5 + z*0.5);
		return hsva2argb(h, s, v, alpha);
	}
}