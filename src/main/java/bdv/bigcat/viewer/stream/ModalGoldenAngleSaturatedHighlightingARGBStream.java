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

import bdv.bigcat.control.Wheel;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.labels.labelset.Label;

/**
 * Generates a stream of saturated colors. Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by the golden angle,
 * making them reasonably distinct. Changing the seed of the stream makes a new
 * sequence.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class ModalGoldenAngleSaturatedHighlightingARGBStream extends GoldenAngleSaturatedHighlightingARGBStream implements Wheel
{
	final static public int NORMAL = 0;

	final static public int HIDE_CONFIRMED = 1;

	final static public int SELECTED_ONLY = 2;

	protected int mode = 0;

	public ModalGoldenAngleSaturatedHighlightingARGBStream( final SelectedIds highlights )
	{
		super( highlights );
		seed = 1;
	}

	@Override
	public synchronized void advance()
	{
		++mode;
		if ( mode == 3 )
			mode = 0;
	}

	@Override
	public synchronized void regress()
	{
		--mode;
		if ( mode == -1 )
			mode = 2;
	}

	@Override
	public void setMode( final int value )
	{
		mode = value % 3;
	}

	@Override
	public int getMode()
	{
		return mode;
	}

	@Override
	public int argb( final long fragmentId )
	{
		if ( !argbCache.contains( fragmentId ) )
		{
			double x = getDouble( seed + fragmentId );
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
				argbCache.put( fragmentId, argb );
			}
		}

		int argb = argbCache.get( fragmentId );

		if ( Label.INVALID == fragmentId )
			argb = argb & 0x00ffffff | invalidSegmentAlpha;
		else
			argb = argb & 0x00ffffff | ( isHighlight( fragmentId ) ? highlightAlpha : alpha );

//		System.out.println( "GETTING COLOR FOR " + fragmentId + " " + highlights );
		return argb;
	}
}
