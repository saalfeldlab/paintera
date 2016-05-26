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
import bdv.labels.labelset.Label;


/**
 * Generates a stream of colors.
 *
 * @author Jan Funke
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractHSVARGBStream extends AbstractARGBStream
{
	public AbstractHSVARGBStream( final FragmentSegmentAssignment assignment )
	{
		super( assignment );
	}

	@Override
	public int argb( final long fragmentId )
	{
		if ( fragmentId == Label.TRANSPARENT )
			return 0;
		final long segmentId = assignment.getSegment( fragmentId );
		int argb = argbCache.get( segmentId );
		if ( argb == 0x00000000 )
		{
			argb = id2argb( seed + segmentId );
			argbCache.put( segmentId, argb );
		}
		if ( activeFragment == fragmentId )
			argb = argb & 0x00ffffff | activeFragmentAlpha;
		else if ( activeSegment == segmentId )
			argb = argb & 0x00ffffff | activeSegmentAlpha;

		return argb;
	}

	protected final static int hsva2argb( double h, double s, double v, final int alpha )
	{

		if ( s < 0 )
			s = 0;
		if ( s > 1 )
			s = 1;
		if ( v < 0 )
			v = 0;
		if ( v > 1 )
			v = 1;

		int r = 0;
		int g = 0;
		int b = 0;

		if ( s == 0 )
		{
			r = ( int ) ( 255.0 * v );
			g = ( int ) ( 255.0 * v );
			b = ( int ) ( 255.0 * v );
		}

		h = h % 1.0; // want h to be in 0..1

		final int i = ( int ) ( h * 6 );
		final double f = ( h * 6 ) - i;
		final double p = v * ( 1.0f - s );
		final double q = v * ( 1.0f - s * f );
		final double t = v * ( 1.0f - s * ( 1.0f - f ) );
		switch ( i % 6 )
		{
		case 0:
			r = ( int ) ( 255.0 * v );
			g = ( int ) ( 255.0 * t );
			b = ( int ) ( 255.0 * p );
			break;
		case 1:
			r = ( int ) ( 255.0 * q );
			g = ( int ) ( 255.0 * v );
			b = ( int ) ( 255.0 * p );
			break;
		case 2:
			r = ( int ) ( 255.0 * p );
			g = ( int ) ( 255.0 * v );
			b = ( int ) ( 255.0 * t );
			break;
		case 3:
			r = ( int ) ( 255.0 * p );
			g = ( int ) ( 255.0 * q );
			b = ( int ) ( 255.0 * v );
			break;
		case 4:
			r = ( int ) ( 255.0 * t );
			g = ( int ) ( 255.0 * p );
			b = ( int ) ( 255.0 * v );
			break;
		case 5:
			r = ( int ) ( 255.0 * v );
			g = ( int ) ( 255.0 * p );
			b = ( int ) ( 255.0 * q );
			break;
		}
		return argb( r, g, b, alpha );
	}

	protected final int id2argb( final long l )
	{

		final double x = 0.4671057256451202 * ( l * ( l + 1 ) ) % 1.0;
		final double y = 0.6262286337141059 * ( l * ( l + 2 ) ) % 1.0;
		final double z = 0.9424373277692188 * ( l * ( l + 3 ) ) % 1.0;

		final double h = x;
		final double s = 0.8 + y * 0.2;
		final double v = ( l == 0 ? 0.0 : 0.5 + z * 0.5 );
		return hsva2argb( h, s, v, alpha );
	}
}