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
package bdv.labels.labelset;


/**
 * Generates a stream of saturated colors.  Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by the golden
 * angle, making them reasonably distinct.  Changing the seed of the stream
 * makes a new sequence.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class GoldenAngleSaturatedARGBStream implements ARGBSource
{
	final static protected double goldenRatio = 1.0 / ( 0.5 * Math.sqrt( 5 ) + 0.5 );
	final static protected double[] rs = new double[]{ 1, 1, 0, 0, 0, 1, 1 };
	final static protected double[] gs = new double[]{ 0, 1, 1, 1, 0, 0, 0 };
	final static protected double[] bs = new double[]{ 0, 0, 0, 1, 1, 1, 0 };

	protected long seed = 0;
	protected int alpha = 0x3000000;
	protected int activeAlpha = 0xff000000;
	protected Long active = null;

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
	 *
	 */
	public void setActive( final Long id )
	{
		active = id;
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

	@Override
	public int argb( final long id )
	{
		double x = ( id + seed ) * goldenRatio;
		x -= ( long )Math.floor( x );
		x *= 6.0;
		final int k = ( int )x;
		final int l = k + 1;
		final double u = x - k;
		final double v = 1.0 - u;

		final int r = interpolate( rs, k, l, u, v );
		final int g = interpolate( gs, k, l, u, v );
		final int b = interpolate( bs, k, l, u, v );

		if ( active != null && active.longValue() == id )
			return argb( r, g, b, activeAlpha );
		else
			return argb( r, g, b, alpha );
	}
}
