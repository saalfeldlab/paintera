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

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import io.github.oshai.kotlinlogging.KLogger;
import io.github.oshai.kotlinlogging.KotlinLogging;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import kotlin.Unit;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.jctools.maps.NonBlockingHashMapLong;

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

	private static final KLogger LOG = KotlinLogging.INSTANCE.logger(() -> Unit.INSTANCE);

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

	protected LockedSegmentsState lockedSegments;

	private final BooleanProperty colorFromSegmentId = new SimpleBooleanProperty();

	/* calling `get()` on the property in a tight loop is expensive;
	 * don't do it per pixel, just on stateChange */
	private volatile boolean colorFromSegment = colorFromSegmentId.get();

	protected final NonBlockingHashMapLong<Integer> explicitlySpecifiedColors = new NonBlockingHashMapLong<>();

	public final NonBlockingHashMapLong<Integer> overrideAlpha = new NonBlockingHashMapLong<>();


	public AbstractHighlightingARGBStream(
			final SelectedSegments selectedSegments,
			final LockedSegmentsState lockedSegments) {

		this.selectedSegments = selectedSegments;
		this.selectedSegments.addListener(_ -> clearCache());

		this.lockedSegments = lockedSegments;
		this.lockedSegments.addListener(_ -> clearCache());

		this.colorFromSegmentId.addListener((_, _, newv) -> {
			colorFromSegment = newv;
			stateChanged();
		});
	}

	protected volatile NonBlockingHashMapLong<Integer> argbCache = new NonBlockingHashMapLong<>(false);

	public boolean isActiveFragment(final long fragmentId) {

		return selectedSegments.getSelectedIds().isActive(fragmentId);
	}

	public boolean isActiveSegment(final long segmentId) {

		return selectedSegments.isSegmentSelected(segmentId);
	}

	public boolean isLockedSegment(final long segmentId) {

		return lockedSegments.isLocked(segmentId);
	}

	protected static int argb(final int r, final int g, final int b, final int alpha) {

		return (r << 8 | g) << 8 | b | alpha;
	}

	protected double getDouble(final long id) {

		return getDoubleImpl(id, colorFromSegment);
	}

	protected abstract double getDoubleImpl(final long id, boolean colorFromSegmentId);

	@Override
	public int argb(final long id) {

		return id == Label.TRANSPARENT ? ZERO : argbImpl(id, colorFromSegment);
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

		argbCache = new NonBlockingHashMapLong<>(explicitlySpecifiedColors.size(), false);
		stateChanged();
	}


	public boolean getColorFromSegmentId() {

		return colorFromSegment;
	}

	public BooleanProperty colorFromSegmentIdProperty() {

		return colorFromSegmentId;
	}

	public void setHideLockedSegments(final boolean hideLockedSegments) {

		if (hideLockedSegments != this.hideLockedSegments) {
			this.hideLockedSegments = hideLockedSegments;
			clearCache();
		}
	}

	public boolean getHideLockedSegments() {

		return this.hideLockedSegments;
	}

	public void specifyColorExplicitly(final long segmentId, final int color, final boolean overrideAlpha) {

		explicitlySpecifiedColors.put(segmentId, (Integer) color);
		if (overrideAlpha) {
			Integer alpha = color >>> 24;
			this.overrideAlpha.put(segmentId, alpha);
		}
		argbCache.remove(segmentId);
		stateChanged();
	}

	public void specifyColorExplicitly(final long segmentId, final int color) {

		specifyColorExplicitly(segmentId, color, false);
	}

	public void specifyColorsExplicitly(final long[] segmentIds, final int[] colors) {

		LOG.debug("Specifying segmentIds {} and colors {}", segmentIds, colors);
		for (int i = 0; i < segmentIds.length; ++i) {
			this.explicitlySpecifiedColors.put(segmentIds[i], (Integer)colors[i]);
		}
		argbCache.putAll(explicitlySpecifiedColors);
		stateChanged();
	}

	public void removeExplicitColor(final long segmentId) {

		LOG.debug("Removing color {} from {}", segmentId, this.explicitlySpecifiedColors);
		if (this.explicitlySpecifiedColors.containsKey(segmentId)) {
			LOG.debug("Map contains colors: Removing color {} from {}", segmentId, this.explicitlySpecifiedColors);
			this.explicitlySpecifiedColors.remove(segmentId);
			if (overrideAlpha.containsKey(segmentId)) {
				overrideAlpha.remove(segmentId);
			}
			argbCache.remove(segmentId);
			stateChanged();
		}
	}

	public void removeExplicitColors(final long[] segmentIds) {

		boolean notifyStateChanged = false;
		for (final long id : segmentIds) {
			if (this.explicitlySpecifiedColors.containsKey(id)) {
				this.explicitlySpecifiedColors.remove(id);
				notifyStateChanged = true;
			}
		}
		if (notifyStateChanged) {
			clearCache();
		}
	}

	public TLongIntMap getExplicitlySpecifiedColorsCopy() {

		TLongIntHashMap tLongIntHashMap = new TLongIntHashMap(explicitlySpecifiedColors.size());
		explicitlySpecifiedColors.forEach(tLongIntHashMap::put);
		return tLongIntHashMap;
	}

	public SelectedSegments getSelectedSegments() {

		return this.selectedSegments;
	}

}
