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
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;
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
			final LockedSegmentsState lockedSegments) {

		super(selectedSegments, lockedSegments);
	}

	static protected int interpolate(final double[] xs, final int k, final int l, final double u, final double v) {

		return (int)((v * xs[k] + u * xs[l]) * 255.0 + 0.5);
	}

	@Override
	protected int argbImpl(final long fragmentId, final boolean colorFromSegmentId) {

		final var segmentId = getSegmentId(fragmentId);
		final long assigned = colorFromSegmentId ? segmentId : fragmentId;

		return argbCache.computeIfAbsent(assigned, id -> {

			final Integer explicitArgb = explicitlySpecifiedColors.get(id);
            if (explicitArgb != null)
				return withFragmentSegmentAlpha(explicitArgb, id, fragmentId, segmentId);

            double x = getDoubleImpl(seed + assigned, colorFromSegmentId);
            x *= 6.0;
            final int k = (int) x;
            final int l = k + 1;
            final double u = x - k;
            final double v = 1.0 - u;

            final int r = interpolate(rs, k, l, u, v);
            final int g = interpolate(gs, k, l, u, v);
            final int b = interpolate(bs, k, l, u, v);

            final int argb = argb(r, g, b, alpha);
            return withFragmentSegmentAlpha(argb, id, fragmentId, segmentId);
		});
	}

	private int withFragmentSegmentAlpha(int argb, long assigned, long fragmentId, long segmentId) {

        if (invalidFragmentDefault(fragmentId))
			return (argb & 0x00ffffff) | invalidSegmentAlpha;

		if (hideLockedSegment(segmentId))
			return (argb & 0x00ffffff);

		final Integer alphaOverride = overrideAlpha.get(assigned);
		if (alphaOverride != null)
			return (argb & 0x00ffffff) | alphaOverride << 24;

		if (isActiveSegment(segmentId)) {
			int alpha = isActiveFragment(fragmentId) ? activeFragmentAlpha : activeSegmentAlpha;
			return (argb & 0x00ffffff) | alpha;
		}

		return argb;
	}

	/**
	 * Query the segment id for the given fragment or segment id.
	 * If the fragment is not part of a segment, return the fragment id.
	 *
	 * @param segmentOrFragmentId to get the associated segment id for
	 * @return the segment id, OR fragment Id if the fragment is not part of a segment
	 */
	private long getSegmentId(long segmentOrFragmentId) {
		return selectedSegments.getAssignment().getSegment(segmentOrFragmentId);
	}

	private boolean hideLockedSegment(long segmentId) {
		return hideLockedSegments && isLockedSegment(segmentId);
	}

	private boolean invalidFragmentDefault(long fragmentId) {
		return Label.INVALID == fragmentId && !explicitlySpecifiedColors.containsKey(fragmentId);
	}
}
