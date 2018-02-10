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
package bdv.bigcat.viewer.stream;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.labels.labelset.Label;

/**
 * Generates and caches a stream of saturated colors. Colors are picked from a
 * radial projection of the RGB colors {red, yellow, green, cyan, blue,
 * magenta}. Changing the seed of the stream makes a new sequence.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractSaturatedHighlightingARGBStream extends AbstractHighlightingARGBStream
{
	public AbstractSaturatedHighlightingARGBStream( final SelectedIds highlights, final FragmentSegmentAssignmentState< ? > assignment )
	{
		super( highlights, assignment );
	}

	final static protected int interpolate( final double[] xs, final int k, final int l, final double u, final double v )
	{
		return ( int ) ( ( v * xs[ k ] + u * xs[ l ] ) * 255.0 + 0.5 );
	}

	@Override
	protected int argbImpl( final long fragmentId, final boolean colorFromSegmentId )
	{
		final boolean isActiveSegment = isActiveSegment( fragmentId );
		final long assigned = colorFromSegmentId || !isActiveSegment ? assignment.getSegment( fragmentId ) : fragmentId;
		if ( !argbCache.contains( assigned ) )
		{
			double x = getDouble( seed + assigned );
			x *= 6.0;
			final int k = ( int ) x;
			final int l = k + 1;
			final double u = x - k;
			final double v = 1.0 - u;

			final int r = interpolate( rs, k, l, u, v );
			final int g = interpolate( gs, k, l, u, v );
			final int b = interpolate( bs, k, l, u, v );

			final int argb = argb( r, g, b, alpha );

			synchronized ( argbCache )
			{
				argbCache.put( assigned, argb );
			}
		}

		int argb = argbCache.get( assigned );
		if ( Label.INVALID == fragmentId )
			argb = argb & 0x00ffffff | invalidSegmentAlpha;
		else
			argb = argb & 0x00ffffff | ( isActiveSegment ? isActiveFragment( fragmentId ) ? activeFragmentAlpha : activeSegmentAlpha : alpha );

		return argb;
	}
}
