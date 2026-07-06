package org.janelia.saalfeldlab.paintera.data;

import net.imglib2.cache.volatiles.CacheHints;
import bdv.viewer.Interpolation;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * A source whose {@code getSource} returns a sliced 3D <em>view</em> of an nD cached backing. The renderer can neither
 * set cache hints on, nor prefetch, a view, so when slicing is active it delegates both to the backing's cell cache
 * through this interface. Without this, nD sources render at the backing's default priority and never prefetch.
 */
public interface SlicedRenderSource {

	/** True when the current view is a reduced/permuted/embedded slice of an nD backing (not a plain 3D cell image). */
	boolean isSliced();

	/** Apply the per-frame render {@code hints} to the nD backing's cell cache for {@code level}. */
	void setSliceCacheHints(int level, CacheHints hints);

	/**
	 * Prefetch the backing cells covering {@code screenInterval} at the current slice of {@code level}.
	 *
	 * @param sourceToScreen the (spatial) source-voxel to screen transform, as the renderer computes it
	 * @param prefetchHints  the prefetch cache hints, or {@code null} to fall back to the backing's default priority
	 */
	void prefetchSlice(int level, AffineTransform3D sourceToScreen, Dimensions screenInterval, Interpolation interpolation, CacheHints prefetchHints);
}
