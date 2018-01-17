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

import net.imglib2.type.Type;

/**
 * Copies b into a.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class CompositeCopy< A extends Type< A > > implements Composite< A, A >
{
	@Override
	public void compose( final A a, final A b )
	{
		a.set( b );
	}
}
