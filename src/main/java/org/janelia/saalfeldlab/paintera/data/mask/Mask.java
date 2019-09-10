package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Invalidate;

public class Mask<D> {

	public final MaskInfo<D> info;

	public final RandomAccessibleInterval<D> mask;

	public final Invalidate<?> invalidate;

	public final Invalidate<?> invalidateVolatile;

	public final Runnable shutdown;

	public Mask(
			final MaskInfo<D> info,
			final RandomAccessibleInterval<D> mask,
			final Invalidate<?> invalidate,
			final Invalidate<?> invalidateVolatile,
			final Runnable shutdown) {
		this.info = info;
		this.mask = mask;
		this.invalidate = invalidate;
		this.invalidateVolatile = invalidateVolatile;
		this.shutdown = shutdown;
	}
}
