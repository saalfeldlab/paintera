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
package bdv.bigcat.composite;

import net.imglib2.type.numeric.ARGBType;

/**
 * Multiplies a by b and combines the result with a weighted by b's alpha value.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class ARGBCompositeAlphaMultiply implements Composite< ARGBType, ARGBType >
{
	@Override
	public void compose( final ARGBType a, final ARGBType b )
	{
		final int argbA = a.get();
		final int argbB = b.get();

		final double rA = ARGBType.red( argbA ) / 255.0;
		final double rB = ARGBType.red( argbB ) / 255.0;
		final double gA = ARGBType.green( argbA ) / 255.0;
		final double gB = ARGBType.green( argbB ) / 255.0;
		final double bA = ARGBType.blue( argbA ) / 255.0;
		final double bB = ARGBType.blue( argbB ) / 255.0;

		final double aA = ARGBType.alpha( argbA ) / 255.0;
		final double aB = ARGBType.alpha( argbB ) / 255.0;

		final double aTarget = aA + aB - aA * aB;

		final double rTarget = rA - rA * aB + rA * rB * aB;
		final double gTarget = gA - gA * aB + gA * gB * aB;
		final double bTarget = bA - bA * aB + bA * bB * aB;

		a.set( ARGBType.rgba(
				Math.max( 0,  Math.min( 255, ( int )Math.round( rTarget * 255 ) ) ),
				Math.max( 0,  Math.min( 255, ( int )Math.round( gTarget * 255 ) ) ),
				Math.max( 0,  Math.min( 255, ( int )Math.round( bTarget * 255 ) ) ),
				( int )( aTarget * 255 ) ) );
	}
}
