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
 * Takes max 
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class ARGBCompositeMax implements Composite< ARGBType, ARGBType >
{
	@Override
	public void compose( final ARGBType a, final ARGBType b )
	{
		final int argbA = a.get();
		final int argbB = b.get();

		final int rA = ARGBType.red( argbA );
		final int rB = ARGBType.red( argbB );
		final int gA = ARGBType.green( argbA );
		final int gB = ARGBType.green( argbB );
		final int bA = ARGBType.blue( argbA );
		final int bB = ARGBType.blue( argbB );

		final int rTarget = Math.max( rA, rB );
		final int gTarget = Math.max( gA, gB );
		final int bTarget = Math.max( bA, bB );

		a.set( ARGBType.rgba( rTarget, gTarget, bTarget, 255 ) );
	}
}
