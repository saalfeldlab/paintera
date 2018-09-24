package org.janelia.saalfeldlab.util.n5;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;

public class ImagesWithInvalidate<D, T> {
	public final RandomAccessibleInterval<D> data;

	public final RandomAccessibleInterval<T> vdata;

	public final AffineTransform3D transform;

	public final Invalidate<Long> invalidate;

	public final Invalidate<Long> vinvalidate;

	public ImagesWithInvalidate(
			final RandomAccessibleInterval<D> data,
			final RandomAccessibleInterval<T> vdata,
			final AffineTransform3D transform,
			final Invalidate<Long> invalidate,
			final Invalidate<Long> vinvalidate) {
		this.data = data;
		this.vdata = vdata;
		this.transform = transform;
		this.invalidate = invalidate;
		this.vinvalidate = vinvalidate;
	}
}
