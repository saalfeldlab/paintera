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


/**
 * A function that composes two input values a and b into a.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public interface Composite< A, B >
{
	/**
	 * Composes a and b into a.
	 *
	 * @param a
	 * @param b
	 */
	public void compose( final A a, final B b );
}
