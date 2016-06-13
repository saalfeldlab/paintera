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

import bdv.bigcat.control.Switch;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.labels.labelset.Label;


/**
 * Generates a stream of saturated colors.  Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by the golden
 * angle, making them reasonably distinct.  Changing the seed of the stream
 * makes a new sequence.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class GoldenAngleSaturatedConfirmSwitchARGBStream extends GoldenAngleSaturatedARGBStream implements Switch
{
	protected boolean hideConfirmed = true;

	public GoldenAngleSaturatedConfirmSwitchARGBStream( final FragmentSegmentAssignment assignment )
	{
		super( assignment );
		seed = 1;
	}

	@Override
	public void toggleSwitch()
	{
		hideConfirmed = !hideConfirmed;
	}

	@Override
	public void setSwitch( final boolean value )
	{
		hideConfirmed = value;
	}

	@Override
	public boolean getSwitch()
	{
		return hideConfirmed;
	}

	@Override
	public int argb( final long fragmentId )
	{
		long segmentId = assignment.getSegment( fragmentId );
		if ( Label.INVALID == segmentId && !hideConfirmed )
			segmentId = fragmentId;
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
		if ( Label.INVALID == segmentId )
			argb = argb & 0x00ffffff | invalidSegmentAlpha;
		else if ( activeFragment == fragmentId )
			argb = argb & 0x00ffffff | activeFragmentAlpha;
		else if ( activeSegment == segmentId )
			argb = argb & 0x00ffffff | activeSegmentAlpha;

		return argb;
	}
}
