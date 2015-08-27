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

import bdv.bigcat.SegmentBodyAssignment;



/**
 * Generates a stream of saturated colors.  Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by the golden
 * angle, making them reasonably distinct.  Changing the seed of the stream
 * makes a new sequence.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class GoldenAngleSaturatedARGBStream extends AbstractSaturatedARGBStream
{
	public GoldenAngleSaturatedARGBStream( final SegmentBodyAssignment assignment )
	{
		super( assignment );
	}

	final static protected double goldenRatio = 1.0 / ( 0.5 * Math.sqrt( 5 ) + 0.5 );

	@Override
	public int argb( final long id )
	{
		int argb = argbCache.get( id );
		if ( argb == 0L )
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

			if ( activeSegment == id )
				argb = argb( r, g, b, activeSegmentAlpha );
			else
				argb = argb( r, g, b, alpha );

			argbCache.put( id, argb );
		}

		return argb;
	}
}
