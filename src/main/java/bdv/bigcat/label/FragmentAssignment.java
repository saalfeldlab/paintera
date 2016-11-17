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

import gnu.trove.set.hash.TLongHashSet;

/**
 * Assigns fragments to an arbitrary property.
 *
 * TODO currently, this reproduces parts of the TLongSet interface which seems
 *   to make it a redundant implementation, however, I suspect this class to
 *   become a remote backed interface and not relying on the trove interface
 *   seems to be a more generic solution at this point...
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class FragmentAssignment
{
	final private TLongHashSet fragments = new TLongHashSet();

	public TLongHashSet getAssignedFragments()
	{
		return fragments;
	}

	public void add( final long fragmentId )
	{
		fragments.add( fragmentId );
	}

	public boolean remove( final long fragmentId )
	{
		return fragments.remove( fragmentId );
	}

	public boolean contains( final long fragmentId )
	{
		return fragments.contains( fragmentId );
	}
}
