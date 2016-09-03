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
package bdv.bigcat.util;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;

/**
 * Tracks the interval that
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class DirtyInterval
{
	protected FinalInterval dirtyInterval = null;

	public void touch( final Interval interval )
	{
		if ( dirtyInterval == null )
			dirtyInterval = new FinalInterval( interval );
		else
			dirtyInterval = Intervals.union( dirtyInterval, interval );
	}

	public void clear()
	{
		dirtyInterval = null;
	}

	public FinalInterval getDirtyInterval()
	{
		return dirtyInterval;
	}
}
