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

import gnu.trove.impl.Constants;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Generates and caches a stream of colors.
 *
 * @author Stephan Saalfeld
 * @author Philipp Hanslovsky
 */
public abstract class AbstractHighlightingARGBStream extends ObservableWithListenersList implements ARGBStream {

	private static final int ZERO = 0x00000000;

	public static int DEFAULT_ALPHA = 0x20000000;

	public static int DEFAULT_ACTIVE_FRAGMENT_ALPHA = 0xd0000000;

	public static int DEFAULT_ACTIVE_SEGMENT_ALPHA = 0x80000000;

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	final static protected double[] rs = new double[]{1, 1, 0, 0, 0, 1, 1};

	final static protected double[] gs = new double[]{0, 1, 1, 1, 0, 0, 0};

	final static protected double[] bs = new double[]{0, 0, 0, 1, 1, 1, 0};

	protected long seed = 0;

	protected int alpha = DEFAULT_ALPHA;

	protected int activeFragmentAlpha = DEFAULT_ACTIVE_FRAGMENT_ALPHA;

	protected int activeSegmentAlpha = DEFAULT_ACTIVE_SEGMENT_ALPHA;

	protected int invalidSegmentAlpha = ZERO;

	protected boolean hideLockedSegments = true;

	protected SelectedSegments selectedSegments;

	protected LockedSegments lockedSegments;

	private final BooleanProperty colorFromSegmentId = new SimpleBooleanProperty();

	protected final TLongIntHashMap explicitlySpecifiedColors = new TLongIntHashMap();
	public final TLongIntHashMap overrideAlpha = new TLongIntHashMap(
			Constants.DEFAULT_CAPACITY,
			Constants.DEFAULT_LOAD_FACTOR,
			Label.INVALID,
			-1
	);

	public AbstractHighlightingARGBStream(
			final SelectedSegments selectedSegments,
			final LockedSegments lockedSegments) {

		this.selectedSegments = selectedSegments;
		this.lockedSegments = lockedSegments;
		this.colorFromSegmentId.addListener((obs, oldv, newv) -> stateChanged());
	}

	protected final TLongIntHashMap argbCache = new TLongIntHashMap(
			Constants.DEFAULT_CAPACITY * 10,
			Constants.DEFAULT_LOAD_FACTOR,
			Label.TRANSPARENT,
			0
	);

	public boolean isActiveFragment(final long id) {

		return selectedSegments.getSelectedIds().isActive(id);
	}

	public boolean isActiveSegment(final long id) {

		return selectedSegments.isSegmentSelected(selectedSegments.getAssignment().getSegment(id));
	}

	public boolean isLockedSegment(final long id) {

		return lockedSegments.isLocked(selectedSegments.getAssignment().getSegment(id));
	}

	protected static int argb(final int r, final int g, final int b, final int alpha) {

		return (r << 8 | g) << 8 | b | alpha;
	}

	protected double getDouble(final long id) {

		return getDoubleImpl(id, colorFromSegmentId.get());
	}

	protected abstract double getDoubleImpl(final long id, boolean colorFromSegmentId);

	@Override
	public int argb(final long id) {

		return id == Label.TRANSPARENT ? ZERO : argbImpl(id, colorFromSegmentId.get());
	}

	protected abstract int argbImpl(long id, boolean colorFromSegmentId);

	/**
	 * Change the seed.
	 *
	 * @param seed
	 */
	public void setSeed(final long seed) {

		if (this.seed != seed) {
			this.seed = seed;
			stateChanged();
		}
	}

	/**
	 * Increment seed.
	 */
	public void incSeed() {

		setSeed(seed + 1);
	}

	/**
	 * Decrement seed.
	 */
	public void decSeed() {

		setSeed(seed - 1);
	}

	/**
	 * @return The current seed value
	 */
	public long getSeed() {

		return this.seed;
	}

	/**
	 * Change alpha. Values less or greater than [0,255] will be masked.
	 *
	 * @param alpha
	 */
	public void setAlpha(final int alpha) {

		if (getAlpha() != alpha) {
			this.alpha = (alpha & 0xff) << 24;
			stateChanged();
		}
	}

	/**
	 * Change active fragment alpha. Values less or greater than [0,255] will be
	 * masked.
	 *
	 * @param alpha
	 */
	public void setActiveSegmentAlpha(final int alpha) {

		if (getActiveSegmentAlpha() != alpha) {
			this.activeSegmentAlpha = (alpha & 0xff) << 24;
			stateChanged();
		}
	}

	public void setInvalidSegmentAlpha(final int alpha) {

		if (getInvalidSegmentAlpha() != alpha) {
			this.invalidSegmentAlpha = (alpha & 0xff) << 24;
			stateChanged();
		}
	}

	public void setActiveFragmentAlpha(final int alpha) {

		if (getActiveFragmentAlpha() != alpha) {
			this.activeFragmentAlpha = (alpha & 0xff) << 24;
			stateChanged();
		}
	}

	public int getAlpha() {

		return this.alpha >>> 24;
	}

	public int getActiveSegmentAlpha() {

		return this.activeSegmentAlpha >>> 24;
	}

	public int getInvalidSegmentAlpha() {

		return invalidSegmentAlpha >>> 24;
	}

	public int getActiveFragmentAlpha() {

		return this.activeFragmentAlpha >>> 24;
	}

	public void clearCache() {

		LOG.debug("Before clearing cache: {}", argbCache);
		synchronized (argbCache) {
			argbCache.clear();
			argbCache.putAll(this.explicitlySpecifiedColors);
		}
		LOG.debug("After clearing cache: {}", argbCache);
		// TODO is this stateChanged bad here?
		// stateChanged() probably triggers a re-render, which calls clearCache,
		// which calls stateChanged, which ...
		// need to check if this is true
		stateChanged();
	}

	public void setColorFromSegmentId(final boolean fromSegmentId) {

		this.colorFromSegmentId.set(fromSegmentId);
	}

	public boolean getColorFromSegmentId() {

		return this.colorFromSegmentId.get();
	}

	public BooleanProperty colorFromSegmentIdProperty() {

		return this.colorFromSegmentId;
	}

	public void setSelectedSegments(final SelectedSegments selectedSegments) {

		this.selectedSegments = selectedSegments;
		clearCache();
	}

	public void setLockedSegments(final LockedSegments lockedSegments) {

		this.lockedSegments = lockedSegments;
		clearCache();
	}

	public void setSelectedAndLockedSegments(
			final SelectedSegments selectedSegments,
			final LockedSegments lockedSegments) {

		this.selectedSegments = selectedSegments;
		this.lockedSegments = lockedSegments;
		clearCache();
	}

	public void setHideLockedSegments(final boolean hideLockedSegments) {

		if (hideLockedSegments != this.hideLockedSegments) {
			this.hideLockedSegments = hideLockedSegments;
			stateChanged();
		}
	}

	public boolean getHideLockedSegments() {

		return this.hideLockedSegments;
	}

	public void specifyColorExplicitly(final long segmentId, final int color, final boolean overrideAlpha) {

		this.explicitlySpecifiedColors.put(segmentId, color);
		if (overrideAlpha) {
			var alpha = color >>> 24;
			this.overrideAlpha.put(segmentId, alpha);
		}
		clearCache();
	}

	public void specifyColorExplicitly(final long segmentId, final int color) {

		specifyColorExplicitly(segmentId, color, false);
	}

	public void specifyColorsExplicitly(final long[] segmentIds, final int[] colors) {

		LOG.debug("Specifying segmentIds {} and colors {}", segmentIds, colors);
		for (int i = 0; i < segmentIds.length; ++i) {
			this.explicitlySpecifiedColors.put(segmentIds[i], colors[i]);
		}
		clearCache();
	}

	public void removeExplicitColor(final long segmentId) {

		LOG.debug("Removing color {} from {}", segmentId, this.explicitlySpecifiedColors);
		if (this.explicitlySpecifiedColors.contains(segmentId)) {
			LOG.debug("Map contains colors: Removing color {} from {}", segmentId, this.explicitlySpecifiedColors);
			this.explicitlySpecifiedColors.remove(segmentId);
			if (overrideAlpha.containsKey(segmentId)) {
				overrideAlpha.remove(segmentId);
			}
			clearCache();
		}
	}

	public void removeExplicitColors(final long[] segmentIds) {

		boolean notifyStateChanged = false;
		for (final long id : segmentIds) {
			if (this.explicitlySpecifiedColors.contains(id)) {
				this.explicitlySpecifiedColors.remove(id);
				notifyStateChanged = true;
			}
		}
		if (notifyStateChanged) {
			clearCache();
		}
	}

	public TLongIntMap getExplicitlySpecifiedColorsCopy() {

		return new TLongIntHashMap(this.explicitlySpecifiedColors);
	}

	public SelectedSegments getSelectedSegments() {

		return this.selectedSegments;
	}

}
