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
package bdv.bigcat.label;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public interface IdPicker
{
	/**
	 * Find the most significant fragment id at a screen pixel.
	 *
	 * @param x
	 * @param y
	 * @return
	 */
	public long getIdAtDisplayCoordinate( final int x, final int y );

	/**
	 * Find the most significant fragment id at a world pixel.
	 *
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public long getIdAtWorldCoordinate( final double x, final double y, final double z );


}
