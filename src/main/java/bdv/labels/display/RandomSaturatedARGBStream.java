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
package bdv.labels.display;

import java.util.Random;

import bdv.bigcat.SegmentBodyAssignment;



/**
 * Generates a stream of saturated colors.  Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by a pseudo-random
 * angle with the hope to make them reasonably distinct.  Changing the seed of
 * the stream makes a new sequence.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class RandomSaturatedARGBStream extends AbstractSaturatedARGBStream
{
	final Random rnd = new Random();
	final static private double DOUBLE_UNIT = 0x1.0p-53; // 1.0 / (1L << 53)

	public RandomSaturatedARGBStream( final SegmentBodyAssignment assignment )
	{
		super( assignment );
	}

	/**
	 * Xorshift random number generator
	 *
	 * @author Wilfried Elmenreich https://mobile.aau.at/~welmenre/
	 * @source http://demesos.blogspot.com.au/2011/09/replacing-java-random-generator.html
	 */
	final static protected long nextSeed( final long seed )
	{
		long x = seed;
		x ^= ( x << 21 );
		x ^= ( x >>> 35 );
		x ^= ( x << 4 );

		return x;
	}

	/**
	 * Xorshift random number generator
	 *
	 * @author Wilfried Elmenreich https://mobile.aau.at/~welmenre/
	 * @source http://demesos.blogspot.com.au/2011/09/replacing-java-random-generator.html
	 */
	static protected int maskSeed( final long seed, final int nbits )
	{
		return ( int )( seed & ( ( 1L << nbits ) - 1 ) );
	}

	/**
	 * Xorshift random number generator
	 *
	 * @author Wilfried Elmenreich https://mobile.aau.at/~welmenre/
	 * @source http://demesos.blogspot.com.au/2011/09/replacing-java-random-generator.html
	 */
	static protected double getDouble( final long id )
	{
		final long seed1 = nextSeed( id );
		final long seed2 = nextSeed( seed1 );
		final int x = maskSeed( seed1, 26 );
		final int y = maskSeed( seed2, 27 );

		return ( ( ( long )x << 27 ) + y ) * DOUBLE_UNIT;
	}

	@Override
	public int argb( final long segmentId )
	{
		final long bodyId = assignment.getBody( segmentId );
		int argb = argbCache.get( bodyId );
		if ( argb == 0x00000000 )
		{
			double x = getDouble( seed + bodyId );
			x *= 6.0;
			final int k = ( int )x;
			final int l = k + 1;
			final double u = x - k;
			final double v = 1.0 - u;

			final int r = interpolate( rs, k, l, u, v );
			final int g = interpolate( gs, k, l, u, v );
			final int b = interpolate( bs, k, l, u, v );

			argb = argb( r, g, b, alpha );

			argbCache.put( bodyId, argb );
		}
		if ( activeSegment == segmentId )
			argb = argb & 0x00ffffff | activeSegmentAlpha;
		else if ( activeBody == bodyId )
			argb = argb & 0x00ffffff | activeBodyAlpha;


		return argb;
	}
}
