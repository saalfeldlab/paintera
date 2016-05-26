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

import bdv.bigcat.label.FragmentSegmentAssignment;


/**
 * Generates and caches a stream of saturated colors.  Colors are picked from a
 * radial projection of the RGB colors {red, yellow, green, cyan, blue,
 * magenta}.  Changing the seed of the stream makes a new sequence.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractSaturatedARGBStream extends AbstractARGBStream
{
	public AbstractSaturatedARGBStream( final FragmentSegmentAssignment assignment )
	{
		super( assignment );
	}

	final static protected int interpolate( final double[] xs, final int k, final int l, final double u, final double v )
	{
		return ( int )( ( v * xs[ k ] + u * xs[ l ] ) * 255.0 + 0.5 );
	}

	@Override
	public int argb( final long fragmentId )
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
}
