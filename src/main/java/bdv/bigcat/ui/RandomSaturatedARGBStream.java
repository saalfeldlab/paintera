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

import java.util.Random;

import bdv.bigcat.label.FragmentSegmentAssignment;



/**
 * Generates a stream of saturated colors.  Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by a pseudo-random
 * angle with the hope to make them reasonably distinct.  Changing the seed of
 * the stream makes a new sequence.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class RandomSaturatedARGBStream extends AbstractSaturatedARGBStream
{
	final Random rnd = new Random();
	final static private double DOUBLE_UNIT = 0x1.0p-53; // 1.0 / (1L << 53)

	public RandomSaturatedARGBStream( final FragmentSegmentAssignment assignment )
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
	final static protected int maskSeed( final long seed, final int nbits )
	{
		return ( int )( seed & ( ( 1L << nbits ) - 1 ) );
	}

	/**
	 * Xorshift random number generator
	 *
	 * @author Wilfried Elmenreich https://mobile.aau.at/~welmenre/
	 * @source http://demesos.blogspot.com.au/2011/09/replacing-java-random-generator.html
	 */
	@Override
	final protected double getDouble( final long id )
	{
		final long seed1 = nextSeed( id );
		final long seed2 = nextSeed( seed1 );
		final int x = maskSeed( seed1, 26 );
		final int y = maskSeed( seed2, 27 );

		return ( ( ( long )x << 27 ) + y ) * DOUBLE_UNIT;
	}
}
