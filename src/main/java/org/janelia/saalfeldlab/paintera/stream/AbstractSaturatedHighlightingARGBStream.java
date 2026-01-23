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

import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;

/**
 * Generates and caches a stream of saturated colors. Colors are picked from a
 * radial projection of the RGB colors {red, yellow, green, cyan, blue,
 * magenta}. Changing the seed of the stream makes a new sequence.
 *
 * @author Stephan Saalfeld
 */
abstract public class AbstractSaturatedHighlightingARGBStream extends AbstractHighlightingARGBStream {

	public AbstractSaturatedHighlightingARGBStream(
			final SelectedSegments selectedSegments,
			final LockedSegments lockedSegments) {

		super(selectedSegments, lockedSegments);
	}

	static protected int interpolate(final double[] xs, final int k, final int l, final double u, final double v) {

		return (int)((v * xs[k] + u * xs[l]) * 255.0 + 0.5);
	}

	@Override
	protected int argbImpl(final long fragmentId, final boolean colorFromSegmentId) {

		final var segmentId = selectedSegments.getAssignment().getSegment(fragmentId);
		final boolean isActiveSegment = selectedSegments.isSegmentSelected(segmentId);
		final long assigned = colorFromSegmentId ? segmentId : fragmentId;
        int argb;
		synchronized (argbCache) {
            argb = argbCache.get(assigned);
        }
        if (argb == argbCache.getNoEntryValue()) {
			double x = getDouble(seed + assigned);
			x *= 6.0;
			final int k = (int)x;
			final int l = k + 1;
			final double u = x - k;
			final double v = 1.0 - u;

			final int r = interpolate(rs, k, l, u, v);
			final int g = interpolate(gs, k, l, u, v);
			final int b = interpolate(bs, k, l, u, v);

			argb = argb(r, g, b, alpha);

			synchronized (argbCache) {
				argbCache.put(assigned, argb);
			}
		}

		if (Label.INVALID == fragmentId && !explicitlySpecifiedColors.contains(fragmentId)) {
			argb = argb & 0x00ffffff | invalidSegmentAlpha;
		} else if (lockedSegments.isLocked(segmentId) && hideLockedSegments) {
			argb = argb & 0x00ffffff;
		} else {
			int alphaOverride = overrideAlpha.get(assigned);
			int actualAlpha = alpha;
			if (alphaOverride == overrideAlpha.getNoEntryValue()) {
				if (isActiveSegment) {
					if (isActiveFragment(fragmentId))
						actualAlpha = activeFragmentAlpha;
					else
						actualAlpha = activeSegmentAlpha;
				}
			} else
				actualAlpha = alphaOverride << 24;
			argb = argb & 0x00ffffff | actualAlpha;
		}

		return argb;
	}
}
