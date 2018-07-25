/**
 * License: GPL
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License 2 as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.paintera.stream;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;

/**
 * Generates a stream of saturated colors. Colors are picked from a radial
 * projection of the RGB colors {red, yellow, green, cyan, blue, magenta}.
 * Adjacent colors along the discrete id axis are separated by the golden angle,
 * making them reasonably distinct. Changing the seed of the stream makes a new
 * sequence.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class GoldenAngleSaturatedHighlightingARGBStream extends AbstractSaturatedHighlightingARGBStream
{
	public GoldenAngleSaturatedHighlightingARGBStream(
			final SelectedIds highlights,
			final FragmentSegmentAssignmentState assignment,
			final LockedSegments lockedSegments)
	{
		super(highlights, assignment, lockedSegments);
		seed = 1;
	}

	final static protected double goldenRatio = 1.0 / (0.5 * Math.sqrt(5) + 0.5);

	@Override
	final protected double getDoubleImpl(final long id, final boolean colorFromSegmentId)
	{
		final double x = id * seed * goldenRatio;
		return x - (long) Math.floor(x);
	}
}
